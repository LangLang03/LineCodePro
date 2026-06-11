package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MemoryOverviewState {
    private final String projectId;
    private final List<Memory> longTerm;
    private final List<Memory> project;
    private final List<Memory> environment;
    private final List<WorkingMemory> shortTerm;
    private final List<HistoryEntry> history;

    public MemoryOverviewState(
            String projectId,
            List<Memory> longTerm,
            List<Memory> project,
            List<Memory> environment,
            List<WorkingMemory> shortTerm,
            List<HistoryEntry> history
    ) {
        this.projectId = projectId == null ? "" : projectId;
        this.longTerm = immutable(longTerm);
        this.project = immutable(project);
        this.environment = immutable(environment);
        this.shortTerm = immutable(shortTerm);
        this.history = immutable(history);
    }

    public String getProjectId() {
        return projectId;
    }

    public List<Memory> getLongTerm() {
        return longTerm;
    }

    public List<Memory> getProject() {
        return project;
    }

    public List<Memory> getEnvironment() {
        return environment;
    }

    public List<WorkingMemory> getShortTerm() {
        return shortTerm;
    }

    public List<HistoryEntry> getHistory() {
        return history;
    }

    private static <T> List<T> immutable(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public static final class Memory {
        public static final String SCOPE_USER = "user";
        public static final String SCOPE_PROJECT = "project";
        public static final String SCOPE_ENVIRONMENT = "environment";

        private final String id;
        private final String scope;
        private final String projectId;
        private final String content;
        private final String source;
        private final double confidence;
        private final long createdAt;
        private final long updatedAt;
        private final long lastUsedAt;
        private final int useCount;

        public Memory(
                String id,
                String scope,
                String projectId,
                String content,
                String source,
                double confidence,
                long createdAt,
                long updatedAt,
                long lastUsedAt,
                int useCount
        ) {
            this.id = id == null ? "" : id;
            this.scope = scope == null ? SCOPE_USER : scope;
            this.projectId = projectId == null ? "" : projectId;
            this.content = content == null ? "" : content;
            this.source = source == null ? "" : source;
            this.confidence = confidence;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.lastUsedAt = lastUsedAt;
            this.useCount = useCount;
        }

        public String getId() {
            return id;
        }

        public String getScope() {
            return scope;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getContent() {
            return content;
        }

        public String getSource() {
            return source;
        }

        public double getConfidence() {
            return confidence;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public long getLastUsedAt() {
            return lastUsedAt;
        }

        public int getUseCount() {
            return useCount;
        }
    }

    public static final class WorkingMemory {
        private final String id;
        private final String projectId;
        private final String content;
        private final String source;
        private final long expiresAt;
        private final long createdAt;
        private final long updatedAt;

        public WorkingMemory(
                String id,
                String projectId,
                String content,
                String source,
                long expiresAt,
                long createdAt,
                long updatedAt
        ) {
            this.id = id == null ? "" : id;
            this.projectId = projectId == null ? "" : projectId;
            this.content = content == null ? "" : content;
            this.source = source == null ? "" : source;
            this.expiresAt = expiresAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getId() {
            return id;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getContent() {
            return content;
        }

        public String getSource() {
            return source;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }

    public static final class HistoryEntry {
        private final String id;
        private final String projectId;
        private final String conversationId;
        private final String messageId;
        private final String role;
        private final String text;
        private final String title;
        private final long createdAt;
        private final long updatedAt;

        public HistoryEntry(
                String id,
                String projectId,
                String conversationId,
                String messageId,
                String role,
                String text,
                String title,
                long createdAt,
                long updatedAt
        ) {
            this.id = id == null ? "" : id;
            this.projectId = projectId == null ? "" : projectId;
            this.conversationId = conversationId == null ? "" : conversationId;
            this.messageId = messageId == null ? "" : messageId;
            this.role = role == null ? "" : role;
            this.text = text == null ? "" : text;
            this.title = title == null ? "" : title;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getId() {
            return id;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getConversationId() {
            return conversationId;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getRole() {
            return role;
        }

        public String getText() {
            return text;
        }

        public String getTitle() {
            return title;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }
}
