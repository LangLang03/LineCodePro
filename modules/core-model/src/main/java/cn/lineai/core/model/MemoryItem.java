package cn.lineai.core.model;

/** One long-lived memory entry. */
public final class MemoryItem {

    public static final String SCOPE_USER = "user";
    public static final String SCOPE_PROJECT = "project";
    public static final String SCOPE_ENVIRONMENT = "environment";

    public final String id;
    public final String scope;
    public final String projectId;
    public final String title;
    public final String content;
    public final String source;
    public final double confidence;
    public final long createdAt;
    public final long updatedAt;
    public final long lastUsedAt;
    public final int useCount;

    public MemoryItem(String id, String scope, String projectId, String title, String content,
                      String source, double confidence, long createdAt, long updatedAt,
                      long lastUsedAt, int useCount) {
        this.id = safe(id);
        this.scope = normalizeScope(scope);
        this.projectId = safe(projectId);
        this.title = safe(title);
        this.content = safe(content);
        this.source = safe(source);
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastUsedAt = lastUsedAt;
        this.useCount = Math.max(0, useCount);
    }

    public static String normalizeScope(String scope) {
        if (SCOPE_PROJECT.equals(scope) || SCOPE_ENVIRONMENT.equals(scope)) return scope;
        return SCOPE_USER;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
