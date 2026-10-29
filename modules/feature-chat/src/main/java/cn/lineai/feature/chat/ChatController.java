package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Conversation;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.ConversationStore;
import cn.lineai.data.api.MessageStore;
import cn.lineai.data.api.ErrorLogStore;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.engine.agent.loop.FinishReason;
import cn.lineai.engine.agent.loop.ToolLoopEngine;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.ai.ModelProbe;
import cn.lineai.engine.protocol.StreamEvent;
import cn.lineai.engine.prompt.SystemPromptAssembler;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCatalog;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Chat use cases and state publication.
 *
 * <p>Generation is driven by the tool loop: every model turn may request tools, results are
 * persisted as TOOL messages, and only mutating tools ask the user for approval. All store access
 * happens on the IO scheduler through an ordered write queue; every publication hops to main.</p>
 */
public final class ChatController {

    public static final String KEY_BASE_URL = "model.baseUrl";
    public static final String KEY_API_KEY = "model.apiKey";
    public static final String KEY_MODEL_ID = "model.modelId";
    public static final String KEY_MODEL_NAME = "model.name";
    public static final String KEY_CONTEXT_SIZE = "model.contextSize";
    public static final String KEY_MAX_TOKENS = "model.maxTokens";
    public static final String KEY_PROJECT_LABEL = "project.label";

    // Package-private for ChatTurnPipeline, which owns the streaming draft on the same event queue.
    final ConversationStore conversations;
    final MessageStore messages;
    private final ModelStore models;
    final SettingsStore settings;
    private final ModelClient client;
    private final Scheduler scheduler;
    final Clock clock;
    private final SystemPromptAssembler prompts;
    private final ToolCatalog toolCatalog;
    private final ToolContext toolContext;
    private ToolRegistry activeTools;
    private final PromptExtensions extensions;

    final Observable<ChatState> state = new Observable<>(ChatState.empty());
    private final Observable<List<Conversation>> conversationList =
            new Observable<>(Collections.<Conversation>emptyList());
    private final Set<String> alwaysAllowed = new HashSet<>();
    final SerialWrites writes;
    final SerialEvents events;

    String conversationId = "";
    ToolLoopEngine engine;
    private Runnable modelMissingHandler;
    ChatTurnPipeline pipeline;
    int seq;
    private volatile int generationSeq;
    private volatile boolean starting;
    private boolean started;
    ErrorLogStore errorLogs;
    java.util.function.Function<DomainException, String> failureText;

    public ChatController(ConversationStore conversations, MessageStore messages,
                          ModelStore models, SettingsStore settings, ModelClient client,
                          Scheduler scheduler, Clock clock, SystemPromptAssembler prompts,
                          ToolCatalog tools, ToolContext toolContext,
                          PromptExtensions extensions) {
        this.conversations = conversations;
        this.messages = messages;
        this.models = models;
        this.settings = settings;
        this.client = client;
        this.scheduler = scheduler;
        this.clock = clock;
        this.prompts = prompts;
        this.toolCatalog = tools;
        this.toolContext = toolContext;
        this.extensions = extensions;
        this.writes = new SerialWrites(scheduler);
        this.events = new SerialEvents(scheduler);
    }
    /** Localized failure formatter supplied by the composition root. */
    public void setFailureText(java.util.function.Function<DomainException, String> formatter) {
        failureText = formatter;
    }

    public void setErrorLogStore(ErrorLogStore store) {
        this.errorLogs = store;
    }
    public void setModelMissingHandler(Runnable handler) {
        this.modelMissingHandler = handler;
    }
    public ChatState state() {
        return state.value();
    }
    public Cancellable observe(Observable.Observer<ChatState> observer) {
        return state.observe(observer);
    }
    public Cancellable observeConversations(Observable.Observer<List<Conversation>> observer) {
        return conversationList.observe(observer);
    }
    public String conversationId() {
        return conversationId;
    }
    /** Loads the most recent conversation, or starts empty when there is none. */
    public void start() {
        if (started) return;
        started = true;
        scheduler.io(() -> {
            List<Conversation> loaded = conversations.list();
            publishConversations(loaded);
            if (loaded.isEmpty()) {
                publish(ChatState.empty().toBuilder().model(modelLabel()).build());
                return;
            }
            open(loaded.get(0).id);
        });
    }
    public void open(String id) {
        // Switching conversations must not leave the previous loop writing into the new list.
        if (engine != null) stop();
        invalidatePendingGeneration();
        scheduler.io(() -> {
            List<Message> loaded = messages.load(id);
            final int nextSeq = loaded.isEmpty() ? 1 : (int) loaded.get(loaded.size() - 1).seq + 1;
            final ChatState snapshot = ChatState.builder()
                    .messages(loaded)
                    .project("")
                    .contextPercent(ChatTurnConfig.percent(loaded,
                            ChatContextLimits.of(modelConfig(), settings)))
                    .model(modelLabel())
                    .revision(System.nanoTime())
                    .build();
            events.post(() -> {
                conversationId = id;
                seq = nextSeq;
                discardPipeline();
                publish(snapshot);
            });
        });
    }

    public void newConversation() {
        if (engine != null) stop();
        invalidatePendingGeneration();
        scheduler.io(() -> {
            final String label = modelLabel();
            events.post(() -> {
                conversationId = "";
                seq = 1;
                discardPipeline();
                publish(ChatState.empty().toBuilder().model(label).build());
            });
        });
    }

    public void deleteConversation(String id) {
        if (id.equals(conversationId)) {
            if (engine != null) stop();
            invalidatePendingGeneration();
        }
        scheduler.io(() -> {
            conversations.delete(id);
            settings.put(ContextCompactor.key(id), "");
            final boolean active = id.equals(conversationId);
            final String label = active ? modelLabel() : "";
            final List<Conversation> remaining = conversations.list();
            events.post(() -> {
                if (active) {
                    conversationId = "";
                    seq = 1;
                    discardPipeline();
                    publish(ChatState.empty().toBuilder().model(label).build());
                }
                publishConversations(remaining);
            });
        });
    }

    /** Outcome of a manual context compaction, mapped to localized copy by the caller. */
    public enum CompactionResult {
        DONE,
        INSUFFICIENT,
        NO_MODEL,
        BUSY,
        FAILED
    }

    /** Summarizes older messages now and shows a compact marker in the timeline. */
    public void compactContext(java.util.function.Consumer<CompactionResult> callback) {
        if (engine != null) {
            events.post(() -> callback.accept(CompactionResult.BUSY));
            return;
        }
        scheduler.io(() -> {
            final String id = conversationId;
            final ModelConfig model = modelConfig();
            if (model == null) {
                events.post(() -> callback.accept(CompactionResult.NO_MODEL));
                return;
            }
            if (id.isEmpty()) {
                events.post(() -> callback.accept(CompactionResult.INSUFFICIENT));
                return;
            }
            try {
                List<Message> loaded = messages.load(id);
                boolean done = ContextCompactor.compactNow(model, models, settings, id, loaded,
                        new ModelProbe()::complete);
                final int percent = ChatTurnConfig.percent(loaded,
                        ChatContextLimits.of(model, settings));
                events.post(() -> {
                    if (!done) {
                        callback.accept(CompactionResult.INSUFFICIENT);
                        return;
                    }
                    appendCompactMarker(percent);
                    callback.accept(CompactionResult.DONE);
                });
            } catch (Exception error) {
                events.post(() -> callback.accept(CompactionResult.FAILED));
            }
        });
    }

    private void appendCompactMarker(int percent) {
        long now = clock.nowMs();
        Message marker = Message.builder()
                .id(Ids.prefixed("msg"))
                .conversationId(conversationId)
                .seq(seq++)
                .role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE)
                .addBlock(ContentBlock.compact(
                        Message.Status.COMPLETE.name().toLowerCase(java.util.Locale.ROOT)))
                .finishReason("compact")
                .times(now, now)
                .build();
        publish(state.value().toBuilder()
                .add(marker)
                .contextPercent(percent)
                .revision(System.nanoTime())
                .build());
        writes.submit(() -> messages.append(marker));
    }

    public void send(String text) {
        send(text, Collections.<String>emptyList(), Collections.<String>emptyList());
    }
    public void send(String text, List<String> imagePaths) {
        send(text, imagePaths, Collections.<String>emptyList());
    }
    public void send(String text, List<String> imagePaths, List<String> filePaths) {
        String prompt = text == null ? "" : text.trim();
        final List<String> images = imagePaths == null
                ? Collections.<String>emptyList() : new ArrayList<>(imagePaths);
        final List<String> files = filePaths == null ? Collections.<String>emptyList() : new ArrayList<>(filePaths);
        if ((prompt.isEmpty() && images.isEmpty() && files.isEmpty())
                || engine != null || starting) {
            return;
        }
        starting = true;
        ModelConfig model = modelConfig();
        if (model == null) {
            starting = false;
            if (modelMissingHandler != null) {
                modelMissingHandler.run();
            }
            return;
        }
        final int generation = ++generationSeq;
        scheduler.io(() -> {
            try {
                startGeneration(prompt, images, files, model, generation);
            } catch (RuntimeException error) {
                events.post(() -> starting = false);
                throw error;
            }
        });
    }

    private void startGeneration(String prompt, List<String> images, List<String> files,
                                 ModelConfig model, int generation) {
        if (generation != generationSeq) {
            return;
        }
        long now = clock.nowMs();
        String target = conversationId;
        if (target.isEmpty()) {
            String title = ChatTurnConfig.attachmentTitle(prompt, images, files);
            Conversation created = conversations.create(title);
            target = created.id;
            conversationId = created.id;
            seq = 1;
        }
        Message.Builder userBuilder = Message.builder()
                .id(Ids.prefixed("msg"))
                .conversationId(target)
                .seq(seq++)
                .role(Role.USER)
                .status(Message.Status.COMPLETE)
                .times(now, now);
        if (!prompt.isEmpty()) {
            userBuilder.addBlock(ContentBlock.text(prompt));
        }
        ChatTurnConfig.addAttachments(userBuilder, images, files);
        Message user = userBuilder.build();
        messages.append(user);

        List<Message> loaded = messages.load(target);
        boolean softCompaction = Boolean.parseBoolean(
                settings.get(UiPreferences.Field.SOFT_COMPACTION.key, "true"));
        final ContextCompactor.Result context = ContextCompactor.prepare(model, models,
                settings, target, loaded, softCompaction, new ModelProbe()::complete);
        final List<Message> history = context.history;
        final String summarySection = prompts == null || context.summary.isEmpty()
                ? "" : prompts.summary(context.summary);
        final String conversation = target;
        events.post(() -> beginGeneration(user, history, summarySection, model, generation,
                conversation));
    }

    /** Runs on the event queue: publishes the user row and starts the loop in one ordered step. */
    private void beginGeneration(Message user, List<Message> history, String summarySection,
                                 ModelConfig model, int generation, String conversation) {
        starting = false;
        if (generation != generationSeq) {
            return;
        }
        conversationId = conversation;
        pipeline = new ChatTurnPipeline(this, model);
        ChatState building = state.value().toBuilder()
                .streaming(true)
                .status("")
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .add(user)
                .revision(System.nanoTime())
                .build();
        publish(building);

        ToolRegistry registry = ToolModes.filterChatMode(ToolModes.chatMode(settings),
                ToolModes.filter(ToolModes.mode(settings), model.toolCallLimit == 0
                        ? new ToolRegistry() : toolCatalog.registry()));
        activeTools = registry;
        final ToolLoopEngine loop = new ToolLoopEngine(client, registry, toolContext,
                scheduler, model.toolCallLimit);
        loop.listener(new LoopListener(pipeline));
        engine = loop;
        loop.start(model, history, conversationId,
                ChatTurnConfig.systemPrompt(prompts, settings, model,
                        extensions == null ? "" : extensions.section(), summarySection,
                        extensions == null ? "" : extensions.learningSection(),
                        registry, toolCatalog.registry()),
                ChatTurnConfig.reasoningSpec(settings, model));
    }

    private void discardPipeline() {
        // Dropping the reference makes every late callback from the old loop stale and ignored.
        pipeline = null;
    }

    /** Abandons a send that is still preparing on the IO pool; its setup event becomes a no-op. */
    private void invalidatePendingGeneration() {
        generationSeq++;
        starting = false;
    }

    private final class LoopListener implements ToolLoopEngine.Listener {
        private final ChatTurnPipeline draft;

        LoopListener(ChatTurnPipeline draft) {
            this.draft = draft;
        }

        @Override public void onTurnStarted() {
            events.post(draft::beginTurn);
        }

        @Override public void onText(String delta) {
            events.post(() -> draft.appendText(delta));
        }

        @Override public void onReasoning(String delta) {
            events.post(() -> draft.appendReasoning(delta));
        }

        @Override public void onAssistantMessage(Message message) {
            events.post(() -> draft.finishAssistant(message));
        }

        @Override public void onToolFinished(ToolCall call, ToolResult result) {
            events.post(() -> draft.appendTool(call, result));
        }

        @Override public void onConfirmationRequired(List<ToolCall> calls) {
            events.post(() -> draft.requestConfirmation(calls));
        }

        @Override public void onFinished(FinishReason reason) {
            events.post(draft::finishTurn);
        }

        @Override public void onFailed(DomainException error) {
            events.post(() -> draft.failTurn(error));
        }

        @Override public void onBudgetExceeded(int requested, int used, int max) {
            events.post(draft::budgetExceeded);
        }
    }

    void engineConfirm(boolean allowed) {
        ToolLoopEngine active = engine;
        if (active != null) {
            active.confirm(allowed);
        }
    }

    boolean autoApprove(List<ToolCall> calls) {
        if (calls.isEmpty() || ToolModes.AUTO.equals(ToolModes.mode(settings))) {
            return true;
        }
        for (ToolCall call : calls) {
            Tool tool = toolsForLookup().find(call.name);
            boolean readOnly = tool != null && !tool.mutating();
            if (!readOnly && !alwaysAllowed.contains(call.name)) {
                return false;
            }
        }
        return true;
    }

    public ToolPresentation presentationFor(String toolName) {
        return toolsForLookup().presentation(toolName);
    }


    ToolRegistry toolsForLookup() {
        return activeTools == null ? toolCatalog.registry() : activeTools;
    }

    public void approveOnce() {
        events.post(this::approveOnceNow);
    }

    private void approveOnceNow() {
        if (engine == null || pipeline == null || pipeline.pendingApprovals().isEmpty()) {
            return;
        }
        pipeline.pendingApprovals().clear();
        publish(state.value().toBuilder()
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        engineConfirm(true);
    }

    public void approveAlways() {
        events.post(this::approveAlwaysNow);
    }

    private void approveAlwaysNow() {
        if (engine == null || pipeline == null || pipeline.pendingApprovals().isEmpty()) {
            return;
        }
        for (ToolApprovalItem item : pipeline.pendingApprovals()) {
            alwaysAllowed.add(item.call.name);
        }
        approveOnceNow();
    }

    public void denyApproval() {
        events.post(this::denyNow);
    }

    private void denyNow() {
        if (engine == null || pipeline == null || pipeline.pendingApprovals().isEmpty()) {
            return;
        }
        pipeline.pendingApprovals().clear();
        publish(state.value().toBuilder()
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        engineConfirm(false);
    }

    public void stop() {
        invalidatePendingGeneration();
        ToolLoopEngine active = engine;
        engine = null;
        // Finalize the cancelled draft before the loop's own finish event lands.
        events.post(() -> {
            if (pipeline != null) {
                pipeline.cancel();
            }
        });
        if (active != null) {
            active.cancel();
        }
    }

    void publish(ChatState snapshot) {
        boolean hasModel = modelConfig() != null;
        ChatState ready = snapshot.toBuilder().hasModel(hasModel)
                .project(settings.get(KEY_PROJECT_LABEL, ""))
                .chatMode(ToolModes.chatMode(settings)).build();
        scheduler.main(() -> state.set(ready));
    }

    void publishConversations(List<Conversation> list) {
        scheduler.main(() -> conversationList.set(list));
    }

    /** Estimated tokens currently in context, using the 4-chars-per-token approximation. */
    public int usedTokens() {
        long chars = 0;
        for (Message message : state.value().messages) {
            chars += message.plainText().length() + message.reasoningText().length();
        }
        return (int) (chars / 4);
    }

    public int maxTokens() {
        return Math.max(1, ChatContextLimits.of(modelConfig(), settings));
    }

    /** True when the current conversation already contains messages. */
    public boolean hasMessages() { return !state.value().messages.isEmpty(); }
    /** Reloads conversations and the selected model after an external data restore. */
    public void reload() {
        invalidatePendingGeneration();
        if (engine != null) stop();
        started = false;
        conversationId = "";
        seq = 1;
        start();
        refreshModel();
    }

    public void refreshModel() {
        scheduler.main(() -> publish(state.value().toBuilder().model(modelLabel()).build()));
    }

    private String modelLabel() {
        ModelConfig model = modelConfig();
        return model == null ? "" : model.displayName();
    }

    private ModelConfig modelConfig() {
        ModelConfig selected = models.selected();
        if (selected != null) {
            return selected;
        }
        String baseUrl = settings.get(KEY_BASE_URL, "");
        String modelId = settings.get(KEY_MODEL_ID, "");
        if (baseUrl.isEmpty() || modelId.isEmpty()) {
            return null;
        }
        return ModelConfig.builder()
                .identity("default", settings.get(KEY_MODEL_NAME, modelId))
                .endpoint(settings.get("provider.label", "custom"), modelId, baseUrl)
                .secret(settings.get(KEY_API_KEY, ""))
                .context(settings.getInt(KEY_CONTEXT_SIZE, 128000), settings.getInt(KEY_MAX_TOKENS, 4096))
                .build();
    }

}
