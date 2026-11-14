package cn.lineai.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.Conversation;
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
 */
public final class SubAgentRegistry implements AgentRuntime {

    /** One live session: the driving controller plus the originating extension. */
    static final class Session {
        final String agentId;
        final String name;
        final boolean persistent;
        final AgentExtension agent;
        final ChatController controller;
        volatile boolean hadPendingApprovals;

        Session(String agentId, String name, boolean persistent, AgentExtension agent,
                ChatController controller) {
            this.agentId = agentId;
            this.name = name;
            this.persistent = persistent;
            this.agent = agent;
            this.controller = controller;
        }
    }

    private final AppGraph graph;
    private final AgentEventBus bus;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Object monitor = new Object();
    private volatile long statusVersion;
    private final cn.lineai.core.kernel.observe.Observable<List<AgentEntry>> entries =
            new cn.lineai.core.kernel.observe.Observable<>(new ArrayList<>());

    /**
     * Creates the registry over the application graph.
     *
     * @param graph the composition root supplying stores, clients and the root controller
     * @param bus the runtime event bus the session inboxes and root routing share
     */
    public SubAgentRegistry(AppGraph graph, AgentEventBus bus) {
        this.graph = graph;
        this.bus = bus;
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

    @Override
    public AgentEntry start(AgentExtension agent, String task, boolean persistent) {
        String kind = persistent ? Conversation.KIND_AGENT : Conversation.KIND_AGENT_TRANSIENT;
        Conversation conversation = graph.conversations().create(
                agent.name.isEmpty() ? agent.slug : agent.name + ": " + firstLine(task),
                kind, graph.chat().conversationId());
        String agentId = conversation.id;
        if (persistent) {
            graph.settings().put("context.agent." + agentId, agent.slug);
        }
        ChatController controller = buildController(agent, persistent, agentId);
        Session session = new Session(agentId,
                agent.name.isEmpty() ? agent.slug : agent.name, persistent, agent, controller);
        sessions.put(agentId, session);
        controller.observe(state -> onSessionState(session, state));
        controller.bindConversation(agentId);
        controller.send(task);
        publishEntries();
        return new AgentEntry(agentId, session.name, kind, agentId, AgentStatus.RUNNING);
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
        for (String kind : new String[] {Conversation.KIND_AGENT,
                Conversation.KIND_AGENT_TRANSIENT}) {
            for (Conversation row : graph.conversations().listByKind(kind)) {
                if (live.containsKey(row.id)) continue;
                result.add(new AgentEntry(row.id, row.title, kind, row.id,
                        AgentStatus.COMPLETED));
            }
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
            return revive(agentId, message);
        }
        if (!session.persistent) {
            return "Sub agent \"" + session.name + "\" is transient and cannot receive"
                    + " messages.";
        }
        session.controller.send(message);
        return "Message delivered to sub agent \"" + session.name + "\" ["
                + session.agentId + "].";
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

    private String revive(String agentId, String message) {
        Conversation row = null;
        for (Conversation candidate : graph.conversations().listByKind(Conversation.KIND_AGENT)) {
            if (candidate.id.equals(agentId)) {
                row = candidate;
                break;
            }
        }
        if (row == null) {
            return "Unknown sub agent: " + agentId;
        }
        String slug = graph.settings().get("context.agent." + agentId, "");
        AgentExtension agent = null;
        for (AgentExtension candidate : graph.agentExtensions().list()) {
            if (candidate.slug.equals(slug)) {
                agent = candidate;
                break;
            }
        }
        if (agent == null) {
            return "Sub agent [" + agentId + "] cannot be revived: its agent definition ("
                    + slug + ") is missing or disabled.";
        }
        AgentEntry started = start(agent, message, true);
        return "Revived sub agent \"" + started.name + "\" [" + started.id
                + "] with your message; its answer arrives when the turn finishes.";
    }

    private ChatController buildController(AgentExtension agent, boolean persistent,
                                           String agentId) {
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
        RuntimeInbox sessionInbox = bus.view("agent:" + agentId);
        cn.lineai.tool.api.ToolContext context = new cn.lineai.tool.api.ToolContext() {
            @Override public File workspaceRoot() { return shared.workspaceRoot(); }
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
        AgentStatus previous = statusOf(session);
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
        if (status != AgentStatus.RUNNING && session.hadPendingApprovals) {
            session.hadPendingApprovals = false;
        }
        if (state.pendingApprovals.isEmpty() && !session.hadPendingApprovals) {
            session.hadPendingApprovals = false;
        }
        session.hadPendingApprovals = !state.pendingApprovals.isEmpty();

        if (status == previous) {
            return;
        }
        statusVersion++;
        synchronized (monitor) {
            monitor.notifyAll();
        }
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

    private void publishEntries() {
        entries.set(list());
    }
}
