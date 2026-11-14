package cn.lineai.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.AgentSessionRecord;
import cn.lineai.core.model.AgentSessionStatus;
import cn.lineai.core.model.Conversation;
import cn.lineai.data.api.AgentSessionStore;
import cn.lineai.feature.chat.ChatController;
import cn.lineai.feature.chat.ToolApprovalItem;
import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.agent.AgentRuntime;
import cn.lineai.tool.api.AgentStatus;
import cn.lineai.tool.api.RuntimeEvent;
import cn.lineai.tool.api.RuntimeInbox;
import cn.lineai.tool.api.ToolCatalog;
import cn.lineai.tool.api.ToolRegistry;

/**
 * Tracks every live sub agent session and implements the {@link AgentRuntime} tool contract.
 *
 * <p>Each session owns a real conversation row (kind {@code agent} or
 * {@code agent_transient}) and a full {@link ChatController}, so transcripts persist,
 * compaction works and the UI can open the conversation like any other. Sessions run
 * concurrently: each loop is its own single-threaded state machine and the registry shares
 * nothing mutable with them except status bookkeeping. Completion is reported to the parent
 * through the runtime event inbox, carrying only the final output — the sub agent's process
 * stays in its own conversation.</p>
 *
 * <p>Session identity is persisted separately from the transcript: every session writes an
 * {@link AgentSessionRecord} (parent, conversation, agent definition, workspace binding,
 * status, last turn) so a process restart restores the agent instead of recreating it.
 * Sessions whose process died mid-turn load as {@code INTERRUPTED} and revive on the next
 * message; creating a session and restoring one are deliberately separate code paths.</p>
 *
 * <p>Every dispatch binds the session to one local directory: that directory becomes the
 * session's {@code ToolContext.workspaceRoot()}, so {@code "."} and every relative path inside
 * the sub agent resolve there instead of the parent's root. The binding is persisted with the
 * session and reused when the session is restored.</p>
 */
public final class SubAgentRegistry implements AgentRuntime {

    /** One live session: the driving controller plus the originating extension. */
    static final class Session {
        final String agentId;
        final String name;
        final boolean persistent;
        final AgentExtension agent;
        final ChatController controller;
        /** Owner-scoped inbox of this session; receives the root's messages while running. */
        final RuntimeInbox inbox;
        /** Last status announced to the parent; null until the first observed state. */
        volatile AgentStatus lastStatus;

        Session(String agentId, String name, boolean persistent, AgentExtension agent,
                ChatController controller, RuntimeInbox inbox) {
            this.agentId = agentId;
            this.name = name;
            this.persistent = persistent;
            this.agent = agent;
            this.controller = controller;
            this.inbox = inbox;
        }
    }

    private final AppGraph graph;
    private final AgentEventBus bus;
    private final AgentSessionStore records;
    /** Persisted session records, mirrored so reads never touch SQLite on the caller thread. */
    private final Map<String, AgentSessionRecord> recordCache = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean recordsLoaded;
    private final Object monitor = new Object();
    private volatile long statusVersion;
    private final cn.lineai.core.kernel.observe.Observable<List<AgentEntry>> entries =
            new cn.lineai.core.kernel.observe.Observable<>(new ArrayList<>());

    /**
     * Creates the registry over the application graph and loads the persisted sessions.
     *
     * @param graph the composition root supplying stores, clients and the root controller
     * @param bus the runtime event bus the session inboxes and root routing share
     * @param records persistence of the session identity records
     */
    public SubAgentRegistry(AppGraph graph, AgentEventBus bus, AgentSessionStore records) {
        this.graph = graph;
        this.bus = bus;
        this.records = records;
        // Loading and the stale-status recovery touch SQLite; keep them off the caller.
        graph.scheduler().io(this::loadRecords);
    }

    /** Live entries published on every status change; the drawer observes this. */
    public cn.lineai.core.kernel.observe.Observable<List<AgentEntry>> entries() {
        return entries;
    }

    /** All pending sub-agent approvals, flattened for the root approval panel. */
    public List<ToolApprovalItem> pendingSubApprovals() {
        List<ToolApprovalItem> pending = new ArrayList<>();
        for (Session session : sessions.values()) {
            for (ToolApprovalItem item : session.controller.state().pendingApprovals) {
                pending.add(new ToolApprovalItem(item.call, item.presentation,
                        session.agentId, session.name));
            }
        }
        return pending;
    }

    /** Routes an approval decision made in the root panel to the owning sub agents. */
    public void resolveAllPending(boolean allowed) {
        for (Session session : sessions.values()) {
            if (!session.controller.state().pendingApprovals.isEmpty()) {
                if (allowed) {
                    session.controller.approveOnce();
                } else {
                    session.controller.denyApproval();
                }
            }
        }
    }

    /** Stops every live session; called before an archive import wipes the tables. */
    public void shutdown() {
        for (Session session : sessions.values()) {
            session.controller.haltForDataReset();
        }
        sessions.clear();
        publishEntries();
    }

    /** The ChatController driving one sub agent conversation, or null when unknown. */
    public ChatController controllerFor(String agentId) {
        Session session = sessions.get(agentId);
        return session == null ? null : session.controller;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The explicit binding is re-checked here so a session is never created in a directory
     * that vanished between the dispatch check and the start; an empty binding inherits the
     * selected project root, the only option for SSH and terminal-provider workspaces.</p>
     */
    @Override
    public AgentEntry start(AgentExtension agent, String task, boolean persistent,
                            String workspace) {
        String kind = persistent ? Conversation.KIND_AGENT : Conversation.KIND_AGENT_TRANSIENT;
        String binding = bindingFor(workspace);
        if (binding == null) {
            return null;
        }
        long now = graph.clock().nowMs();
        Conversation conversation = graph.conversations().create(
                agent.name.isEmpty() ? agent.slug : agent.name + ": " + firstLine(task),
                kind, graph.chat().conversationId());
        String agentId = conversation.id;
        writeRecord(new AgentSessionRecord(agentId, displayName(agent),
                graph.chat().conversationId(), agentId, agent.slug, binding,
                AgentSessionStatus.RUNNING, persistent, now, now, ""));
        Session session = attach(agent, persistent, agentId, binding);
        session.controller.bindConversation(agentId);
        session.controller.send(task);
        if (!session.controller.busy()) {
            // send() is a no-op without a model; report the failure instead of advertising
            // a session that will never run.
            sessions.remove(agentId);
            records.delete(agentId);
            recordCache.remove(agentId);
            graph.conversations().delete(agentId);
            publishEntries();
            return null;
        }
        publishEntries();
        return new AgentEntry(agentId, session.name, kind, agentId, AgentStatus.RUNNING);
    }

    /** Builds, registers and observes a session bound to an agent id and workspace path. */
    private Session attach(AgentExtension agent, boolean persistent, String agentId,
                           String binding) {
        RuntimeInbox inbox = bus.view("agent:" + agentId);
        ChatController controller = buildController(agent, persistent, agentId, inbox, binding);
        Session session = new Session(agentId, displayName(agent), persistent, agent,
                controller, inbox);
        session.lastStatus = AgentStatus.RUNNING;
        sessions.put(agentId, session);
        controller.observe(state -> onSessionState(session, state));
        return session;
    }

    @Override
    public List<AgentEntry> list() {
        List<AgentEntry> result = new ArrayList<>();
        Map<String, Boolean> live = new ConcurrentHashMap<>();
        for (Session session : sessions.values()) {
            live.put(session.agentId, Boolean.TRUE);
            result.add(new AgentEntry(session.agentId, session.name, kindOf(session),
                    session.agentId, statusOf(session)));
        }
        for (AgentSessionRecord record : recordCache.values()) {
            if (live.containsKey(record.id)) continue;
            result.add(new AgentEntry(record.id, record.name, kindOf(record),
                    record.conversationId, entryStatus(record.status)));
        }
        return result;
    }

    @Override
    public String send(String agentId, String message) {
        if (agentId == null || agentId.isEmpty()) {
            return "agent_id must not be empty";
        }
        if (agentId.equals(graph.chat().conversationId())) {
            return "You are the parent agent; you cannot send a message to yourself.";
        }
        Session session = sessions.get(agentId);
        if (session == null) {
            return restore(agentId, message);
        }
        if (!session.persistent) {
            return "Sub agent \"" + session.name + "\" is transient and cannot receive"
                    + " messages.";
        }
        if (session.controller.busy()) {
            // A running turn ignores ChatController.send() silently; deliver through the
            // session inbox instead — the loop drains it at its next input point, and the
            // idle-wake path delivers it if the turn finishes first.
            session.inbox.post(new RuntimeEvent.UserMessage("parent", message));
            return "Queued for sub agent \"" + session.name + "\" [" + session.agentId
                    + "]: it is running and will see the message at its next input point.";
        }
        session.controller.send(message);
        if (!session.controller.busy()) {
            return "Could not deliver to sub agent \"" + session.name + "\" ["
                    + session.agentId + "]: no model is configured.";
        }
        return "Delivered to sub agent \"" + session.name + "\" [" + session.agentId
                + "] as a new turn.";
    }

    @Override
    public List<AgentEntry> waitFor(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        long seen = statusVersion;
        synchronized (monitor) {
            long remaining = deadline - System.currentTimeMillis();
            while (remaining > 0) {
                try {
                    monitor.wait(Math.min(200, remaining));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                remaining = deadline - System.currentTimeMillis();
                if (statusVersion != seen) {
                    break;
                }
            }
        }
        return list();
    }

    /**
     * Restores a persisted session: reads its record, reattaches the controller to the
     * original conversation (history and seq counter), rebuilds the inbox and starts a turn
     * with the incoming message. Deliberately separate from {@link #start}: restoring never
     * creates a conversation or a record.
     */
    private String restore(String agentId, String message) {
        AgentSessionRecord record = recordCache.get(agentId);
        if (record == null) {
            record = records.find(agentId);
        }
        if (record == null) {
            return "Unknown sub agent: " + agentId;
        }
        if (!record.persistent) {
            return "Sub agent [" + agentId + "] was a transient run and cannot be revived.";
        }
        AgentExtension agent = definition(record.agentDefinitionId);
        if (agent == null) {
            return "Sub agent [" + agentId + "] cannot be revived: its agent definition ("
                    + record.agentDefinitionId + ") is missing or disabled.";
        }
        final AgentSessionRecord restored = record;
        Session session = attach(agent, true, agentId, record.workspaceBinding);
        // openThen restores the persisted transcript and the seq counter before the new
        // turn appends rows; appending to a fresh conversation would lose the context.
        session.controller.openThen(restored.conversationId, () -> {
            session.controller.send(message);
            if (!session.controller.busy()) {
                updateRecord(agentId, AgentSessionStatus.FAILED, "");
            }
        });
        publishEntries();
        return "Revived sub agent \"" + session.name + "\" [" + agentId
                + "] in its existing conversation; its answer arrives when the turn finishes.";
    }

    /** Resolves an agent definition by its persisted slug. */
    private AgentExtension definition(String slug) {
        for (AgentExtension candidate : graph.agentExtensions().list()) {
            if (candidate.slug.equals(slug)) {
                return candidate;
            }
        }
        return null;
    }

    private ChatController buildController(AgentExtension agent, boolean persistent,
                                           String agentId, RuntimeInbox sessionInbox,
                                           String binding) {
        List<cn.lineai.tool.api.Tool> allowed = new ArrayList<>();
        for (cn.lineai.tool.api.Tool tool : graph.agentBaseTools().registry().all()) {
            if (!persistent && tool.mutating()) continue;
            if (tool.kind() == cn.lineai.tool.api.ToolKind.PHONE) continue;
            if (!agent.toolNames.isEmpty() && !agent.toolNames.contains(tool.name())) continue;
            allowed.add(tool);
        }
        cn.lineai.tool.api.ToolRegistry registry = new cn.lineai.tool.api.ToolRegistry();
        for (cn.lineai.tool.api.Tool tool : allowed) registry.register(tool);
        ToolCatalog fixedCatalog = () -> registry;
        cn.lineai.tool.api.ToolContext shared = graph.agentToolContext();
        cn.lineai.tool.api.ToolContext context = new cn.lineai.tool.api.ToolContext() {
            @Override public File workspaceRoot() {
                // The session stays bound to the directory chosen at dispatch time. A restored
                // session keeps that directory while it still exists; once it is gone the
                // session falls back to the current selection.
                if (!binding.isEmpty()) {
                    File bound = new File(binding);
                    if (bound.isDirectory()) return bound;
                }
                return shared.workspaceRoot();
            }
            @Override public String modelId() { return shared.modelId(); }
            @Override public boolean mutationAllowed() { return persistent; }
            @Override public boolean pathProtectionBypassed() {
                return shared.pathProtectionBypassed();
            }
            @Override public cn.lineai.tool.api.TodoStore todos() { return shared.todos(); }
            @Override public cn.lineai.tool.api.MemorySink memory() {
                return persistent ? shared.memory() : null;
            }
            @Override public cn.lineai.tool.api.DiffSink diffs() {
                return persistent ? shared.diffs() : null;
            }
            @Override public cn.lineai.tool.api.BackgroundTasks backgrounds() {
                return shared.backgrounds();
            }
            @Override public RuntimeInbox inbox() { return sessionInbox; }
        };
        ChatController controller = new ChatController(graph.conversations(), graph.messages(),
                graph.models(), graph.settings(), graph.modelClient(), graph.scheduler(),
                graph.clock(), graph.assembler(), fixedCatalog, context, graph.extensions());
        controller.setAgentSystemPrompt(agent.prompt);
        controller.setAgentView(agentId, !persistent);
        return controller;
    }

    private void onSessionState(Session session, cn.lineai.feature.chat.ChatState state) {
        AgentStatus status;
        if (!state.pendingApprovals.isEmpty()) {
            status = AgentStatus.WAITING_INPUT;
        } else if (state.streaming) {
            status = AgentStatus.RUNNING;
        } else if (state.statusText.isEmpty()) {
            status = AgentStatus.COMPLETED;
        } else {
            status = AgentStatus.FAILED;
        }
        // Compare against the last announced value: the controller has already published the
        // new state, so reading it back here would always equal the fresh status and suppress
        // every transition — including the completion event the parent waits for.
        AgentStatus previous = session.lastStatus;
        session.lastStatus = status;
        if (status == previous) {
            return;
        }
        statusVersion++;
        synchronized (monitor) {
            monitor.notifyAll();
        }
        updateRecord(session.agentId, recordStatus(status),
                status == AgentStatus.COMPLETED || status == AgentStatus.FAILED
                        ? lastAssistantId(state) : "");
        publishEntries();
        if (status == AgentStatus.COMPLETED || status == AgentStatus.FAILED) {
            bus.post(AgentEventBus.ROOT_OWNER, new RuntimeEvent.AgentTurnCompleted(
                    session.agentId, session.name, status == AgentStatus.FAILED,
                    finalOutput(state)));
        }
        if (status == AgentStatus.WAITING_INPUT && session.persistent) {
            bus.post(AgentEventBus.ROOT_OWNER, new RuntimeEvent.AgentNeedsInput(
                    session.agentId, "approval requested for a mutating tool"));
        }
    }

    /** Scans backwards for the last completed assistant answer without tool calls. */
    private static String finalOutput(cn.lineai.feature.chat.ChatState state) {
        for (int i = state.messages.size() - 1; i >= 0; i--) {
            cn.lineai.core.model.Message message = state.messages.get(i);
            if (message.role == cn.lineai.core.model.Role.ASSISTANT
                    && message.toolCalls.isEmpty()
                    && message.status == cn.lineai.core.model.Message.Status.COMPLETE
                    && message.visibility == cn.lineai.core.model.Message.Visibility.VISIBLE) {
                return message.plainText();
            }
        }
        return "";
    }

    private AgentStatus statusOf(Session session) {
        cn.lineai.feature.chat.ChatState state = session.controller.state();
        if (!state.pendingApprovals.isEmpty()) return AgentStatus.WAITING_INPUT;
        if (state.streaming) return AgentStatus.RUNNING;
        return state.statusText.isEmpty() ? AgentStatus.COMPLETED : AgentStatus.FAILED;
    }

    private static String kindOf(Session session) {
        return session.persistent ? Conversation.KIND_AGENT : Conversation.KIND_AGENT_TRANSIENT;
    }

    private static String firstLine(String task) {
        String line = task == null ? "" : task.trim();
        int newline = line.indexOf('\n');
        if (newline > 0) line = line.substring(0, newline);
        return line.length() > 60 ? line.substring(0, 60) : line;
    }

    /** Loads the persisted records, migrates legacy agent conversations, recovers victims. */
    private void loadRecords() {
        try {
            for (AgentSessionRecord record : records.list()) {
                recordCache.put(record.id, record);
            }
            migrateLegacyConversations();
        } catch (RuntimeException error) {
            System.err.println("agent session load failed: " + error);
        }
        recordsLoaded = true;
        recoverInterrupted();
        publishEntries();
    }

    /**
     * Pre-v15 installs created agent conversations without a session record; synthesize one
     * from the conversation row so those agents restore like any other.
     */
    private void migrateLegacyConversations() {
        for (String kind : new String[] {Conversation.KIND_AGENT,
                Conversation.KIND_AGENT_TRANSIENT}) {
            for (Conversation row : graph.conversations().listByKind(kind)) {
                if (recordCache.containsKey(row.id)) continue;
                String slug = graph.settings().get("context.agent." + row.id, "");
                AgentSessionRecord legacy = new AgentSessionRecord(row.id, row.title, "",
                        row.id, slug, "", AgentSessionStatus.COMPLETED,
                        Conversation.KIND_AGENT.equals(kind), row.createdAt, row.updatedAt, "");
                writeRecord(legacy);
            }
        }
    }

    /**
     * A record still marked RUNNING or WAITING_INPUT at load time means the process died
     * mid-turn: mark it INTERRUPTED so the drawer and the tools report the truth.
     */
    private void recoverInterrupted() {
        for (AgentSessionRecord record : interruptedOnLoad(new ArrayList<>(recordCache.values()),
                graph.clock().nowMs())) {
            writeRecord(record);
        }
    }

    /**
     * Visible for tests: returns the records that must be rewritten because they died
     * mid-turn ({@code RUNNING}/{@code WAITING_INPUT} at load time become INTERRUPTED).
     *
     * @param records the loaded records
     * @param now transition timestamp in epoch milliseconds
     * @return the records that changed; empty when everything is consistent
     */
    static List<AgentSessionRecord> interruptedOnLoad(List<AgentSessionRecord> records,
                                                      long now) {
        List<AgentSessionRecord> changed = new ArrayList<>();
        for (AgentSessionRecord record : records) {
            if (record.status == AgentSessionStatus.RUNNING
                    || record.status == AgentSessionStatus.WAITING_INPUT) {
                changed.add(record.withStatus(AgentSessionStatus.INTERRUPTED, now, ""));
            }
        }
        return changed;
    }

    /** Updates one cached record and persists it on the io scheduler. */
    private void updateRecord(String agentId, AgentSessionStatus status, String lastTurnId) {
        AgentSessionRecord record = recordCache.get(agentId);
        if (record == null || record.status == status) {
            return;
        }
        writeRecord(record.withStatus(status, graph.clock().nowMs(), lastTurnId));
    }

    /** Mirrors a record in the cache and writes it to SQLite. */
    private void writeRecord(AgentSessionRecord record) {
        recordCache.put(record.id, record);
        graph.scheduler().io(() -> {
            try {
                records.save(record);
            } catch (RuntimeException error) {
                System.err.println("agent session save failed: " + record.id + ": " + error);
            }
        });
    }

    /**
     * Resolves a fresh session's binding: an explicit local directory must still exist, while
     * an empty binding falls back to the selected project root, which is also the only option
     * for remote workspaces because SSH and terminal-provider workspaces cannot bind locally.
     *
     * @param workspace canonical directory chosen by the dispatcher; may be null or empty
     * @return the binding to persist, or null when the explicit directory no longer exists
     */
    private String bindingFor(String workspace) {
        if (workspace == null || workspace.trim().isEmpty()) {
            cn.lineai.core.model.Project project = graph.projects().selected();
            return project == null ? "" : project.path;
        }
        File bound = new File(workspace.trim());
        return bound.isDirectory() ? bound.getAbsolutePath() : null;
    }

    private static String displayName(AgentExtension agent) {
        return agent.name.isEmpty() ? agent.slug : agent.name;
    }

    private static String kindOf(AgentSessionRecord record) {
        return record.persistent ? Conversation.KIND_AGENT : Conversation.KIND_AGENT_TRANSIENT;
    }

    private static AgentSessionStatus recordStatus(AgentStatus status) {
        switch (status) {
            case RUNNING:
                return AgentSessionStatus.RUNNING;
            case WAITING_INPUT:
                return AgentSessionStatus.WAITING_INPUT;
            case FAILED:
                return AgentSessionStatus.FAILED;
            default:
                return AgentSessionStatus.COMPLETED;
        }
    }

    private static AgentStatus entryStatus(AgentSessionStatus status) {
        switch (status) {
            case RUNNING:
                return AgentStatus.RUNNING;
            case WAITING_INPUT:
                return AgentStatus.WAITING_INPUT;
            case FAILED:
                return AgentStatus.FAILED;
            case INTERRUPTED:
                return AgentStatus.INTERRUPTED;
            default:
                return AgentStatus.COMPLETED;
        }
    }

    /** Id of the last completed assistant answer, used as the persisted last turn marker. */
    private static String lastAssistantId(cn.lineai.feature.chat.ChatState state) {
        for (int i = state.messages.size() - 1; i >= 0; i--) {
            cn.lineai.core.model.Message message = state.messages.get(i);
            if (message.role == cn.lineai.core.model.Role.ASSISTANT
                    && message.toolCalls.isEmpty()
                    && message.status == cn.lineai.core.model.Message.Status.COMPLETE
                    && message.visibility == cn.lineai.core.model.Message.Visibility.VISIBLE) {
                return message.id;
            }
        }
        return "";
    }

    /** Publishes on the main thread; every observable consumer is UI code. */
    private void publishEntries() {
        graph.scheduler().main(() -> entries.set(list()));
    }
}
