package cn.lineai.data.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversationRecord {
    private final String id;
    private final String title;
    private final String projectId;
    private final long createdAt;
    private final long updatedAt;
    private final boolean current;
    private final String rawJson;
    private final List<MessageRecord> messages;

    public ConversationRecord(
            String id,
            String title,
            String projectId,
            long createdAt,
            long updatedAt,
            boolean current,
            String rawJson,
            List<MessageRecord> messages
    ) {
        this.id = id == null ? "" : id;
        this.title = title == null || title.length() == 0 ? "新对话" : title;
        this.projectId = projectId == null ? "" : projectId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.current = current;
        this.rawJson = rawJson == null ? "" : rawJson;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages == null ? Collections.emptyList() : messages));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getProjectId() {
        return projectId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getRawJson() {
        return rawJson;
    }

    public List<MessageRecord> getMessages() {
        return messages;
    }
}
