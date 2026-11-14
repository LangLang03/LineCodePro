package cn.lineai.tool.api;

/** Immutable snapshot of one sub agent session, returned by the agent-listing tools. */
public final class AgentEntry {

    public final String id;
    public final String name;
    /** Conversation kind: {@code agent} (persistent) or {@code agent_transient}. */
    public final String kind;
    /** The conversation row holding this agent's full transcript. */
    public final String conversationId;
    /** Live status of the session. */
    public final AgentStatus status;

    public AgentEntry(String id, String name, String kind, String conversationId,
                      AgentStatus status) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.kind = kind == null ? "" : kind;
        this.conversationId = conversationId == null ? "" : conversationId;
        this.status = status == null ? AgentStatus.COMPLETED : status;
    }
}
