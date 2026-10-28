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

    private final ConversationStore conversations;
    private final MessageStore messages;
    private final ModelStore models;
    private final SettingsStore settings;
    private final ModelClient client;
    private final Scheduler scheduler;
    private final Clock clock;
    private final SystemPromptAssembler prompts;
    private final ToolCatalog toolCatalog;
    private final ToolContext toolContext;
    private ToolRegistry activeTools;
    private final PromptExtensions extensions;

    private final Observable<ChatState> state = new Observable<>(ChatState.empty());
    private final Observable<List<Conversation>> conversationList =
            new Observable<>(Collections.<Conversation>emptyList());
    private final Set<String> alwaysAllowed = new HashSet<>();
    private final SerialWrites writes;

    private String conversationId = "";
    private ToolLoopEngine engine;
    private Runnable modelMissingHandler;
    private StringBuilder streamText = new StringBuilder();
    private StringBuilder streamReasoning = new StringBuilder();
    private Message streamingMessage;
    private List<ToolApprovalItem> pendingApprovals = new ArrayList<>();
    private int seq;
    private boolean started;
    private ErrorLogStore errorLogs;

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
        scheduler.io(() -> {
            List<Message> loaded = messages.load(id);
            conversationId = id;
            seq = loaded.isEmpty() ? 1 : (int) loaded.get(loaded.size() - 1).seq + 1;
            ChatState snapshot = ChatState.builder()
                    .messages(loaded)
                    .project("")
                    .contextPercent(ChatTurnConfig.percent(loaded, settings.getInt(KEY_CONTEXT_SIZE, 128000)))
                    .model(modelLabel())
                    .revision(System.nanoTime())
                    .build();
            publish(snapshot);
        });
    }

    public void newConversation() {
        scheduler.io(() -> {
            conversationId = "";
            seq = 1;
            publish(ChatState.empty().toBuilder().model(modelLabel()).build());
        });
    }

    public void deleteConversation(String id) {
        scheduler.io(() -> {
            conversations.delete(id);
            settings.put(ContextCompactor.key(id), "");
            if (id.equals(conversationId)) {
                conversationId = "";
                seq = 1;
                publish(ChatState.empty().toBuilder().model(modelLabel()).build());
            }
            publishConversations(conversations.list());
        });
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
        if ((prompt.isEmpty() && images.isEmpty() && files.isEmpty()) || engine != null) {
            return;
        }
        ModelConfig model = modelConfig();
        if (model == null) {
            if (modelMissingHandler != null) {
                modelMissingHandler.run();
            }
            return;
        }
        scheduler.io(() -> startGeneration(prompt, images, files, model));
    }

    private void startGeneration(String prompt, List<String> images, List<String> files,
                                 ModelConfig model) {
        long now = clock.nowMs();
        if (conversationId.isEmpty()) {
            String title = ChatTurnConfig.attachmentTitle(prompt, images, files);
            Conversation created = conversations.create(title);
            conversationId = created.id;
            seq = 1;
        }
        Message.Builder userBuilder = Message.builder()
                .id(Ids.prefixed("msg"))
                .conversationId(conversationId)
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

        streamText = new StringBuilder();
        streamReasoning = new StringBuilder();
        streamingMessage = null;
        pendingApprovals = new ArrayList<>();
        List<Message> loaded = messages.load(conversationId);
        boolean softCompaction = Boolean.parseBoolean(
                settings.get(UiPreferences.Field.SOFT_COMPACTION.key, "true"));
        final ContextCompactor.Result context = ContextCompactor.prepare(model, models,
                settings, conversationId, loaded, softCompaction, new ModelProbe()::complete);
        List<Message> history = context.history;
        final String summarySection = prompts == null || context.summary.isEmpty()
                ? "" : prompts.summary(context.summary);
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
        loop.listener(new LoopListener(model, loop));
        engine = loop;
        scheduler.main(() -> loop.start(model, history, conversationId,
                ChatTurnConfig.systemPrompt(prompts, settings, model,
                        extensions == null ? "" : extensions.section(), summarySection,
                        extensions == null ? "" : extensions.learningSection(),
                        registry, toolCatalog.registry()),
                ChatTurnConfig.reasoningSpec(settings, model)));
    }

    private final class LoopListener implements ToolLoopEngine.Listener {
        private final ModelConfig model;
        private final ToolLoopEngine loop;

        LoopListener(ModelConfig model, ToolLoopEngine loop) {
            this.model = model;
            this.loop = loop;
        }

        @Override public void onTurnStarted() {
            streamText = new StringBuilder();
            streamReasoning = new StringBuilder();
            long now = clock.nowMs();
            streamingMessage = Message.builder()
                    .id(Ids.prefixed("msg"))
                    .conversationId(conversationId)
                    .seq(seq++)
                    .role(Role.ASSISTANT)
                    .status(Message.Status.STREAMING)
                    .model(model.modelId, model.providerId)
                    .times(now, now)
                    .build();
            publish(state.value().toBuilder()
                    .add(streamingMessage)
                    .streaming(true)
                    .model(model.displayName())
                    .revision(System.nanoTime())
                    .build());
        }

        @Override public void onText(String delta) {
            streamText.append(delta);
            scheduler.main(() -> publishStreaming(model));
        }

        @Override public void onReasoning(String delta) {
            streamReasoning.append(delta);
            scheduler.main(() -> publishStreaming(model));
        }

        @Override public void onAssistantMessage(Message message) {
            Message current = streamingMessage;
            if (current == null) {
                current = Message.builder()
                        .id(Ids.prefixed("msg"))
                        .conversationId(conversationId)
                        .seq(seq++)
                        .role(Role.ASSISTANT)
                        .status(Message.Status.STREAMING)
                        .model(model.modelId, model.providerId)
                        .times(clock.nowMs(), clock.nowMs())
                        .build();
            }
            final Message finished = current.toBuilder()
                    .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                    .toolCalls(message.toolCalls)
                    .status(Message.Status.COMPLETE)
                    .finishReason(message.toolCalls.isEmpty() ? "stop" : "tool_calls")
                    .times(current.createdAt, clock.nowMs())
                    .build();
            streamingMessage = null;
            publish(state.value().toBuilder()
                    .replace(finished)
                    .streaming(true)
                    .revision(System.nanoTime())
                    .build());
            writes.submit(() -> messages.append(finished));
        }

        @Override public void onToolFinished(ToolCall call, ToolResult result) {
            long now = clock.nowMs();
            Message.Builder toolBuilder = Message.builder()
                    .id(Ids.prefixed("msg"))
                    .conversationId(conversationId)
                    .seq(seq++)
                    .role(Role.TOOL)
                    .status(result.error ? Message.Status.FAILED : Message.Status.COMPLETE)
                    .tool(call.id, call.name)
                    .addBlock(ContentBlock.text(result.content))
                    .times(now, now);
            if (!result.imagePath.isEmpty()) toolBuilder.addBlock(ContentBlock.image(result.imagePath));
            final Message tool = toolBuilder.build();
            publish(state.value().toBuilder()
                    .add(tool)
                    .revision(System.nanoTime())
                    .build());
            writes.submit(() -> messages.append(tool));
        }

        @Override public void onConfirmationRequired(List<ToolCall> calls) {
            if (autoApprove(calls)) {
                loop.confirm(true);
                return;
            }
            List<ToolApprovalItem> approvals = new ArrayList<>();
            for (ToolCall call : calls) {
                approvals.add(new ToolApprovalItem(call,
                        toolsForLookup().presentation(call.name, call.arguments)));
            }
            pendingApprovals = approvals;
            publish(state.value().toBuilder()
                    .pendingApprovals(approvals)
                    .revision(System.nanoTime())
                    .build());
        }

        @Override public void onFinished(FinishReason reason) {
            engine = null;
            pendingApprovals = new ArrayList<>();
            publish(state.value().toBuilder()
                    .streaming(false)
                    .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                    .revision(System.nanoTime())
                    .build());
            writes.submit(() -> {
                conversations.touch(conversationId, clock.nowMs(),
                        messages.count(conversationId));
                publishConversations(conversations.list());
            });
        }

        @Override public void onFailed(DomainException error) {
            engine = null;
            pendingApprovals = new ArrayList<>();
            if (errorLogs != null) {
                errorLogs.record(model.displayName(),
                        error.code().name() + " · " + error.detail(),
                        error.getMessage() == null ? "" : error.getMessage());
            }
            if (streamingMessage != null) {
                final Message failed = streamingMessage.toBuilder()
                        .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                        .status(Message.Status.FAILED)
                        .error(error.code().name())
                        .finishReason("error")
                        .times(streamingMessage.createdAt, clock.nowMs())
                        .build();
                streamingMessage = null;
                publish(state.value().toBuilder()
                        .replace(failed)
                        .streaming(false)
                        .status(error.code().name())
                        .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                        .revision(System.nanoTime())
                        .build());
                writes.submit(() -> messages.append(failed));
            } else {
                publish(state.value().toBuilder()
                        .streaming(false)
                        .status(error.code().name())
                        .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                        .revision(System.nanoTime())
                        .build());
            }
            writes.submit(() -> {
                conversations.touch(conversationId, clock.nowMs(),
                        messages.count(conversationId));
                publishConversations(conversations.list());
            });
        }

        @Override public void onBudgetExceeded(int requested, int used, int max) {
            engine = null;
            pendingApprovals = new ArrayList<>();
            publish(state.value().toBuilder()
                    .streaming(false)
                    .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                    .revision(System.nanoTime())
                    .build());
        }
    }

    private boolean autoApprove(List<ToolCall> calls) {
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

    private ToolRegistry toolsForLookup() {
        return activeTools == null ? toolCatalog.registry() : activeTools;
    }

    public void approveOnce() {
        if (engine == null || pendingApprovals.isEmpty()) {
            return;
        }
        pendingApprovals = new ArrayList<>();
        publish(state.value().toBuilder()
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        engine.confirm(true);
    }

    public void approveAlways() {
        if (engine == null || pendingApprovals.isEmpty()) {
            return;
        }
        for (ToolApprovalItem item : pendingApprovals) {
            alwaysAllowed.add(item.call.name);
        }
        approveOnce();
    }

    public void denyApproval() {
        if (engine == null || pendingApprovals.isEmpty()) {
            return;
        }
        pendingApprovals = new ArrayList<>();
        publish(state.value().toBuilder()
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        engine.confirm(false);
    }

    private void publishStreaming(ModelConfig model) {
        if (streamingMessage == null) {
            return;
        }
        streamingMessage = streamingMessage.toBuilder()
                .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                .times(streamingMessage.createdAt, clock.nowMs())
                .build();
        publish(state.value().toBuilder()
                .replace(streamingMessage)
                .streaming(true)
                .model(model.displayName())
                .contextPercent(ChatTurnConfig.percent(state.value().messages,
                        settings.getInt(KEY_CONTEXT_SIZE, 128000)))
                .revision(System.nanoTime())
                .build());
    }

    public void stop() {
        ToolLoopEngine active = engine;
        engine = null;
        if (active != null) {
            active.cancel();
        }
        scheduler.main(() -> {
            pendingApprovals = new ArrayList<>();
            if (streamingMessage == null) {
                publish(state.value().toBuilder()
                        .streaming(false)
                        .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                        .build());
                return;
            }
            final Message stopped = streamingMessage.toBuilder()
                    .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                    .status(Message.Status.CANCELLED)
                    .finishReason(Message.Status.CANCELLED.name().toLowerCase(java.util.Locale.ROOT))
                    .times(streamingMessage.createdAt, clock.nowMs())
                    .build();
            streamingMessage = null;
            publish(state.value().toBuilder()
                    .replace(stopped)
                    .streaming(false)
                    .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                    .revision(System.nanoTime())
                    .build());
            writes.submit(() -> messages.append(stopped));
        });
    }

    private void publish(ChatState snapshot) {
        boolean hasModel = modelConfig() != null;
        ChatState ready = snapshot.toBuilder().hasModel(hasModel)
                .project(settings.get(KEY_PROJECT_LABEL, ""))
                .chatMode(ToolModes.chatMode(settings)).build();
        scheduler.main(() -> state.set(ready));
    }

    private void publishConversations(List<Conversation> list) {
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
        ModelConfig model = modelConfig();
        return Math.max(1, model == null ? settings.getInt(KEY_CONTEXT_SIZE, 128000)
                : model.contextSize);
    }

    /** Reloads conversations and the selected model after an external data restore. */
    public void reload() {
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
