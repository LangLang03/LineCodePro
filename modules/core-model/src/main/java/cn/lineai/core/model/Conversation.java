package cn.lineai.core.model;

/** Conversation header data. Message bodies are stored separately and loaded on demand. */
public final class Conversation {

    /** Kind of a regular user conversation. */
    public static final String KIND_USER = "";
    /** Kind of a persistent sub-agent conversation (messageable, revivable). */
    public static final String KIND_AGENT = "agent";
    /** Kind of a transient sub-agent conversation (viewable, not messageable). */
    public static final String KIND_AGENT_TRANSIENT = "agent_transient";

    public final String id;
    public final String title;
    public final String projectId;
    public final long createdAt;
    public final long updatedAt;
    public final int messageCount;
    /** Conversation kind: {@link #KIND_USER} for regular chats, an agent kind otherwise. */
    public final String kind;
    /** Conversation the agent was spawned from; empty for user conversations. */
    public final String parentId;

    /**
     * Creates a user-conversation header, normalising null strings to empty.
     * @param id stable conversation identifier
     * @param title display title
     * @param projectId owning project identifier
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last activity timestamp in epoch milliseconds
     * @param messageCount number of stored messages
     */
    public Conversation(String id, String title, String projectId, long createdAt, long updatedAt, int messageCount) {
        this(id, title, projectId, createdAt, updatedAt, messageCount, KIND_USER, "");
    }

    /**
     * Creates a header with kind and parent linkage, normalising null strings to empty.
     * @param id stable conversation identifier
     * @param title display title
     * @param projectId owning project identifier
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last activity timestamp in epoch milliseconds
     * @param messageCount number of stored messages
     * @param kind conversation kind (user or agent kind)
     * @param parentId conversation the agent was spawned from
     */
    public Conversation(String id, String title, String projectId, long createdAt, long updatedAt,
                        int messageCount, String kind, String parentId) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.projectId = projectId == null ? "" : projectId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
        this.kind = kind == null ? KIND_USER : kind;
        this.parentId = parentId == null ? "" : parentId;
    }

    /**
     * Returns a copy with the title replaced.
     * @param value new display title
     * @return a new immutable header
     */
    public Conversation withTitle(String value) {
        return new Conversation(id, value, projectId, createdAt, updatedAt, messageCount, kind, parentId);
    }

    /**
     * Returns a copy stamped as active at {@code now} with {@code count} messages.
     * @param now new last-activity timestamp in epoch milliseconds
     * @param count updated message count
     * @return a new immutable header
     */
    public Conversation touched(long now, int count) {
        return new Conversation(id, title, projectId, createdAt, now, count, kind, parentId);
    }

    /** Returns true when this conversation belongs to a sub agent rather than the user. */
    public boolean isAgentConversation() {
        return !KIND_USER.equals(kind);
    }
}
