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

    /** Settings keys shared beyond this module; the model.* seed keys live in SqliteModelStore. */
    public static final String KEY_CONTEXT_SIZE = "model.contextSize";
    public static final String KEY_PROJECT_LABEL = "project.label";

    // Package-private for ChatTurnPipeline, which owns the streaming draft on the event queue.
    final ConversationStore conversations;
    final MessageStore messages;
    final ModelStore models;
    final SettingsStore settings;
    private final ModelClient client;
    final Scheduler scheduler;
    final Clock clock;
    final SystemPromptAssembler prompts;
    private final ToolCatalog toolCatalog;
    final ToolContext toolContext;
    private ToolRegistry activeTools;
    final PromptExtensions extensions;

    final Observable<ChatState> state = new Observable<>(ChatState.empty());
    private final Observable<List<Conversation>> conversationList =
            new Observable<>(Collections.<Conversation>emptyList());
    private final ApprovalPolicy approvals;
    final SerialWrites writes;
    final SerialEvents events;

    String conversationId = "";
    /** Conversation shown in the UI; differs from {@code conversationId} while detached. */
    String viewedConversationId = "";
    /** True while a background turn drives another conversation than the viewed one. */
    boolean detached;
    /** Seq counter of the viewed conversation, captured when detaching. */
    int viewedSeq;
    ToolLoopEngine engine;
    /** Agent view marker: non-empty when this controller drives a sub agent conversation. */
    private String agentId = "";
    /** True when this controller drives a transient (not messageable) sub agent. */
    private boolean transientAgent;
    /** System prompt override for sub agent views; null assembles the standard prompt. */
    private String agentSystemPrompt;
    /** Source of sub-agent approval items merged into the approval panel. */
    private java.util.function.Supplier<List<ToolApprovalItem>> subApprovals;
    /** Routes an approval decision to the owning sub agents. */
    private java.util.function.Consumer<Boolean> subApprovalDecision;
    private Runnable modelMissingHandler;
    ChatTurnPipeline pipeline;
    int seq;
    volatile int generationSeq;
    volatile boolean starting;
    boolean started;
    ErrorLogStore errorLogs;

    /** Wires stores and schedulers; approval decisions delegate to {@link ApprovalPolicy}. */
    public ChatController(ConversationStore conversations, MessageStore messages,
                          ModelStore models, SettingsStore settings, ModelClient client,
                          Scheduler scheduler, Clock clock, SystemPromptAssembler prompts,
                          ToolCatalog tools, ToolContext toolContext,
                          PromptExtensions extensions) {
        this.conversations = conversations;
        this.messages = messages;
        this.models = models;
        this.settings = settings;
        this.approvals = new ApprovalPolicy(this::toolsForLookup,
                () -> ToolModes.mode(settings));
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
    /** Attaches the error-log store used to record turn failures. */
    public void setErrorLogStore(ErrorLogStore store) {
        this.errorLogs = store;
    }
    /** Hook invoked when a send is attempted without a configured model. */
    public void setModelMissingHandler(Runnable handler) {
        this.modelMissingHandler = handler;
    }

    /** Callback invoked when a send finds no configured model. */
    Runnable getModelMissingHandler() {
        return modelMissingHandler;
    }
    /** Current immutable snapshot. */
    public ChatState state() {
        return state.value();
    }
    /** Observes state changes; callbacks arrive on the main thread. */
    public Cancellable observe(Observable.Observer<ChatState> observer) {
        return state.observe(observer);
    }
    /** Observes the conversation list; callbacks arrive on the main thread. */
    public Cancellable observeConversations(Observable.Observer<List<Conversation>> observer) {
        return conversationList.observe(observer);
    }
    /** Identifier of the open conversation; empty until one is loaded or created. */
    public String conversationId() {
        return conversationId;
    }
    /** Loads the most recent conversation, or starts empty when there is none. */
    public void start() {
        ChatConversations.start(this);
    }

    /** Loads a conversation; a running loop keeps running and keeps writing to its own rows. */
    public void open(String id) {
        ChatConversations.open(this, id, null);
    }

    /** Conversation currently displayed; the running turn may belong to another one. */
    public String viewedConversationId() {
        return ChatDetachedView.viewed(this);
    }

    /** Conversation a background turn is writing into; empty when idle. */
    public String runningConversationId() {
        return ChatDetachedView.running(this);
    }

    /** True while the displayed conversation differs from the one a turn is writing into. */
    boolean detached() {
        return ChatDetachedView.detached(this);
    }

    /** Marks the controller as viewing another conversation while the turn keeps running. */
    void detachView(String id, int seqAtView) {
        ChatDetachedView.detach(this, id, seqAtView);
    }

    /** Rebinds the view to the running conversation; called when the user opens it again. */
    void reattachView() {
        ChatDetachedView.reattach(this);
    }

    /**
     * Hands the write target over to the viewed conversation once the background turn ended,
     * so the next send appends to the conversation the user is looking at.
     */
    void adoptViewAfterBackgroundTurn() {
        ChatDetachedView.adopt(this);
    }

    /** Clears the transcript and returns to the unsaved empty conversation. */
    public void newConversation() {
        ChatConversations.newConversation(this);
    }

    /** Deletes a conversation; the active one is stopped and its draft discarded before io deletion. */
    public void deleteConversation(String id) {
        ChatConversations.delete(this, id);
    }

    /** Outcome of a manual context compaction, mapped to localized copy by the caller. */
    public enum CompactionResult {
        DONE,
        INSUFFICIENT,
        NO_MODEL,
        BUSY,
        FAILED
    }

    /** Summarizes older messages now and shows a compact marker; the io-side prepare window
     * is guarded against a concurrent {@code send} by the compaction helper. */
    public void compactContext(java.util.function.Consumer<CompactionResult> callback) {
        ChatCompaction.compact(this, callback);
    }

    void appendCompactMarker(int percent) {
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

    /**
     * True while a turn is running or being prepared on the IO pool. Callers that must not
     * lose a message (sub-agent delivery) have to queue it through the runtime inbox instead
     * of calling {@link #send}, which is a silent no-op in this window.
     *
     * @return true when {@link #send} would currently be ignored
     */
    public boolean busy() {
        return engine != null || starting;
    }

    /** Sends a prompt with no attachments; ignored while a generation is active. */
    public void send(String text) {
        ChatSends.send(this, text, Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }

    /** Sends a prompt with image attachments; ignored while a generation is active. */
    public void send(String text, List<String> imagePaths) {
        ChatSends.send(this, text, imagePaths, Collections.<String>emptyList());
    }

    /** Sends a prompt with images and file references; ignored while a generation is active. */
    public void send(String text, List<String> imagePaths, List<String> filePaths) {
        ChatSends.send(this, text, imagePaths, filePaths);
    }

    void startGeneration(String prompt, List<String> images, List<String> files,
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
                settings, target, loaded, softCompaction, prompts.compactionTemplate(),
                new ModelProbe()::complete);
        final List<Message> history = context.history;
        final String summarySection = prompts == null || context.summary.isEmpty()
                ? "" : prompts.summary(context.summary);
        final String conversation = target;
        events.post(() -> beginGeneration(user, history, summarySection, model, generation,
                conversation));
    }

    /** Runs on the event queue: publishes the user row and starts the loop in one ordered step. */
    void beginGeneration(Message user, List<Message> history, String summarySection,
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
        loop.listen(new ChatLoopBridge(this, pipeline));
        engine = loop;
        String systemPrompt = agentSystemPrompt != null
                ? agentSystemPrompt
                : ChatTurnConfig.systemPrompt(prompts, settings, model,
                        extensions == null ? "" : extensions.section(), summarySection,
                        extensions == null ? "" : extensions.learningSection(),
                        registry, toolCatalog.registry());
        loop.start(model, history, conversationId, systemPrompt,
                ChatTurnConfig.reasoningSpec(settings, model));
    }

    /** Dropping the reference makes every late callback from the old loop stale and ignored. */
    void discardPipeline() {
        pipeline = null;
    }

    /** Abandons a send that is still preparing on the IO pool; its setup event becomes a no-op. */
    void invalidatePendingGeneration() {
        generationSeq++;
        starting = false;
    }

    void engineConfirm(boolean allowed) {
        ToolLoopEngine active = engine;
        if (active != null) {
            active.confirm(allowed);
        }
    }

    boolean autoApprove(List<ToolCall> calls) {
        return approvals.autoApprove(calls);
    }

    /** Presentation metadata for a tool name; chat UI must not branch on tool names. */
    public ToolPresentation presentationFor(String toolName) {
        return toolsForLookup().presentation(toolName);
    }


    ToolRegistry toolsForLookup() {
        return activeTools == null ? toolCatalog.registry() : activeTools;
    }

    /** Approves the pending batch once; queued on the event queue. */
    public void approveOnce() {
        events.post(this::approveOnceNow);
    }

    /** Clears pending approvals and confirms the engine; no-op when nothing is pending. */
    private void approveOnceNow() {
        if (subApprovalDecision != null) {
            subApprovalDecision.accept(true);
        }
        if (engine == null || pipeline == null || pipeline.pendingApprovals().isEmpty()) {
            publish(state.value().toBuilder()
                    .revision(System.nanoTime())
                    .build());
            return;
        }
        pipeline.pendingApprovals().clear();
        publish(state.value().toBuilder()
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        engineConfirm(true);
    }

    /** Approves the pending batch and remembers the tool names for the process lifetime. */
    public void approveAlways() {
        events.post(this::approveAlwaysNow);
    }

    /** Persists "always allow" for the pending tools via {@link ApprovalPolicy}, then approves. */
    private void approveAlwaysNow() {
        if (engine == null || pipeline == null || pipeline.pendingApprovals().isEmpty()) {
            return;
        }
        approvals.allowAll(pipeline.pendingApprovals());
        approveOnceNow();
    }

    /** Denies the pending batch; the engine refuses the rejected tool calls. */
    public void denyApproval() {
        events.post(this::denyNow);
    }

    /** Clears pending approvals and denies the engine; no-op when nothing is pending. */
    private void denyNow() {
        if (subApprovalDecision != null) {
            subApprovalDecision.accept(false);
        }
        if (engine == null || pipeline == null || pipeline.pendingApprovals().isEmpty()) {
            publish(state.value().toBuilder()
                    .revision(System.nanoTime())
                    .build());
            return;
        }
        pipeline.pendingApprovals().clear();
        publish(state.value().toBuilder()
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        engineConfirm(false);
    }

    /** Halts the loop; the cancelled draft is finalized before the loop's own finish event lands. */
    public void stop() {
        invalidatePendingGeneration();
        ToolLoopEngine active = engine;
        engine = null;
        events.post(() -> {
            if (pipeline != null) {
                pipeline.cancel();
            }
        });
        if (active != null) {
            active.cancel();
        }
    }

    /**
     * Pre-binds this controller to an existing conversation without loading messages; used
     * for fresh sub agent sessions whose transcript starts empty. Restarted sessions with
     * existing rows must use {@link #openThen(String, Runnable)} to restore the seq counter.
     *
     * @param id the conversation this controller writes into
     */
    public void bindConversation(String id) {
        conversationId = id;
    }

    /**
     * Loads a conversation and then runs the action on the event queue; the ordered chain
     * guarantees the seq counter is restored before the action sends anything.
     *
     * @param id conversation to load
     * @param action queued after the conversation is bound
     */
    public void openThen(String id, Runnable action) {
        ChatConversations.open(this, id, action);
    }

    /** Halts any turn and drops the draft writer before an archive import wipes the tables. */
    public void haltForDataReset() {
        stop();
        discardPipeline();
    }

    /**
     * Starts a hidden-notice turn when the runtime inbox holds events after the last turn
     * ended; queued on the event queue by the turn-finish path. A busy controller skips the
     * wake — the pending events ride the next turn's own start-of-turn drain.
     */
    void wakeFromInbox() {
        ChatInboxWake.wake(this);
    }

    /** Enriches the snapshot with projection fields and publishes it on the main thread. */
    /**
     * Marks this controller as driving a sub agent conversation; the published state then
     * carries the agent marker so the UI can disable model switching and messaging.
     *
     * @param agentId the sub agent id stamped into every published state
     * @param transientAgent true for a transient (not messageable) agent view
     */
    public void setAgentView(String agentId, boolean transientAgent) {
        this.agentId = agentId == null ? "" : agentId;
        this.transientAgent = transientAgent;
    }

    /**
     * Overrides the assembled system prompt with the sub agent's persona prompt.
     *
     * @param prompt the agent prompt; null restores the standard assembly
     */
    public void setAgentSystemPrompt(String prompt) {
        this.agentSystemPrompt = prompt;
    }

    /**
     * Registers the sub-agent approval source merged into the approval panel and the
     * decision router for panel actions on sub-agent items.
     */
    public void setSubApprovalSource(java.util.function.Supplier<List<ToolApprovalItem>> source,
                                     java.util.function.Consumer<Boolean> decision) {
        this.subApprovals = source;
        this.subApprovalDecision = decision;
    }

    void publish(ChatState snapshot) {
        // The background turn keeps persisting rows, but its state must not replace the
        // conversation the user is reading.
        if (detached) {
            return;
        }
        boolean hasModel = modelConfig() != null;
        java.util.List<ToolApprovalItem> merged = mergeSubApprovals(snapshot.pendingApprovals);
        ChatState ready = snapshot.toBuilder().hasModel(hasModel)
                .pendingApprovals(merged)
                .project(settings.get(KEY_PROJECT_LABEL, ""))
                .chatMode(ToolModes.chatMode(settings))
                .agentId(agentId)
                .transientAgent(transientAgent)
                .build();
        scheduler.main(() -> state.set(ready));
    }

    /** Root approvals first, then the sub-agent items supplied by the registry. */
    private List<ToolApprovalItem> mergeSubApprovals(List<ToolApprovalItem> rootItems) {
        if (subApprovals == null) return rootItems;
        List<ToolApprovalItem> sub = subApprovals.get();
        if (sub == null || sub.isEmpty()) return rootItems;
        List<ToolApprovalItem> merged = new ArrayList<>(rootItems);
        merged.addAll(sub);
        return merged;
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

    /** Configured context window size in tokens; at least 1. */
    public int maxTokens() {
        return Math.max(1, ChatContextLimits.of(modelConfig(), settings));
    }

    /** True when the current conversation already contains messages. */
    public boolean hasMessages() { return !state.value().messages.isEmpty(); }
    /** Reloads conversations and the selected model after an external data restore. */
    /** Reopens the most recent conversation after an external data restore. */
    public void reload() {
        invalidatePendingGeneration();
        if (engine != null) stop();
        started = false;
        conversationId = "";
        seq = 1;
        start();
        refreshModel();
    }

    /** Republishes the state with the currently selected model's display name. */
    public void refreshModel() {
        scheduler.main(() -> publish(state.value().toBuilder().model(modelLabel()).build()));
    }

    String modelLabel() {
        ModelConfig model = modelConfig();
        return model == null ? "" : model.displayName();
    }

    /** The selected model only: legacy settings keys are seeded into the store by AppGraph. */
    ModelConfig modelConfig() {
        return models.selected();
    }
}
