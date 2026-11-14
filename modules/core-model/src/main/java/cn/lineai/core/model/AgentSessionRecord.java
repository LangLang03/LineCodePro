package cn.lineai.core.model;

/**
 * Persisted identity of one sub agent session. The transcript lives in its own conversation;
 * this record is the session itself, so a process restart can restore the agent instead of
 * recreating it.
 */
public final class AgentSessionRecord {

    /** Session id; equal to the agent id used by the tool family. */
    public final String id;
    /** Display name of the agent definition at spawn time. */
    public final String name;
    /** Root conversation the agent was spawned from. */
    public final String parentConversationId;
    /** Conversation holding the agent's transcript. */
    public final String conversationId;
    /** Agent definition identifier (extension slug) that created the session. */
    public final String agentDefinitionId;
    /** Workspace path the session was bound to at spawn time. */
    public final String workspaceBinding;
    /** Persisted lifecycle state. */
    public final AgentSessionStatus status;
    /** True for a messageable, revivable session; false for a one-shot transient run. */
    public final boolean persistent;
    public final long createdAt;
    public final long updatedAt;
    /** Identifier of the last finished turn's assistant message; empty before the first turn. */
    public final String lastTurnId;

    /**
     * Creates a record, normalising null strings to empty.
     *
     * @param id session id
     * @param name display name of the agent definition
     * @param parentConversationId root conversation that spawned the agent
     * @param conversationId transcript conversation
     * @param agentDefinitionId extension slug of the agent definition
     * @param workspaceBinding workspace path bound at spawn time
     * @param status persisted lifecycle state
     * @param persistent true for a revivable session
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last transition timestamp in epoch milliseconds
     * @param lastTurnId assistant message id of the last finished turn
     */
    public AgentSessionRecord(String id, String name, String parentConversationId,
                              String conversationId, String agentDefinitionId,
                              String workspaceBinding, AgentSessionStatus status,
                              boolean persistent, long createdAt, long updatedAt,
                              String lastTurnId) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.parentConversationId = parentConversationId == null ? "" : parentConversationId;
        this.conversationId = conversationId == null ? "" : conversationId;
        this.agentDefinitionId = agentDefinitionId == null ? "" : agentDefinitionId;
        this.workspaceBinding = workspaceBinding == null ? "" : workspaceBinding;
        this.status = status == null ? AgentSessionStatus.COMPLETED : status;
        this.persistent = persistent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastTurnId = lastTurnId == null ? "" : lastTurnId;
    }

    /**
     * Returns a copy with a new status, update timestamp and last turn id.
     *
     * @param newStatus the new lifecycle state
     * @param now transition timestamp in epoch milliseconds
     * @param newLastTurnId assistant message id of the finished turn; may be empty
     * @return a new immutable record
     */
    public AgentSessionRecord withStatus(AgentSessionStatus newStatus, long now,
                                         String newLastTurnId) {
        return new AgentSessionRecord(id, name, parentConversationId, conversationId,
                agentDefinitionId, workspaceBinding, newStatus, persistent, createdAt, now,
                newLastTurnId == null || newLastTurnId.isEmpty() ? lastTurnId : newLastTurnId);
    }
}
