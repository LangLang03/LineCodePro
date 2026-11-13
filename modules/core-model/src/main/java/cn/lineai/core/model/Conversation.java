package cn.lineai.core.model;

/** Conversation header data. Message bodies are stored separately and loaded on demand. */
public final class Conversation {

    public final String id;
    public final String title;
    public final String projectId;
    public final long createdAt;
    public final long updatedAt;
    public final int messageCount;

    /**
     * Creates a header, normalising null strings to empty.
     * @param id stable conversation identifier
     * @param title display title
     * @param projectId owning project identifier
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last activity timestamp in epoch milliseconds
     * @param messageCount number of stored messages
     */
    public Conversation(String id, String title, String projectId, long createdAt, long updatedAt, int messageCount) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.projectId = projectId == null ? "" : projectId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    /**
     * Returns a copy with the title replaced.
     * @param value new display title
     * @return a new immutable header
     */
    public Conversation withTitle(String value) {
        return new Conversation(id, value, projectId, createdAt, updatedAt, messageCount);
    }

    /**
     * Returns a copy stamped as active at {@code now} with {@code count} messages.
     * @param now new last-activity timestamp in epoch milliseconds
     * @param count updated message count
     * @return a new immutable header
     */
    public Conversation touched(long now, int count) {
        return new Conversation(id, title, projectId, createdAt, now, count);
    }
}
