package cn.lineai.core.model;

/** Conversation header data. Message bodies are stored separately and loaded on demand. */
public final class Conversation {

    public final String id;
    public final String title;
    public final String projectId;
    public final long createdAt;
    public final long updatedAt;
    public final int messageCount;

    public Conversation(String id, String title, String projectId, long createdAt, long updatedAt, int messageCount) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.projectId = projectId == null ? "" : projectId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    public Conversation withTitle(String value) {
        return new Conversation(id, value, projectId, createdAt, updatedAt, messageCount);
    }

    public Conversation touched(long now, int count) {
        return new Conversation(id, title, projectId, createdAt, now, count);
    }
}
