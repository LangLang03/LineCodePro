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

    /**
     * Creates a memory item, normalising null strings to empty.
     * @param id stable item identifier
     * @param scope persistence scope, unknown values fall back to {@link #SCOPE_USER}
     * @param projectId owning project identifier when scoped to a project
     * @param title short summary of the entry
     * @param content full memory content
     * @param source where the entry originated
     * @param confidence confidence in [0, 1] assigned to the entry
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last modification timestamp in epoch milliseconds
     * @param lastUsedAt last usage timestamp in epoch milliseconds
     * @param useCount usage counter, negative values clamp to zero
     */
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

    /**
     * Maps {@code scope} to a supported scope, defaulting to {@link #SCOPE_USER}.
     * @param scope raw scope identifier, may be null
     * @return a supported scope identifier
     */
    public static String normalizeScope(String scope) {
        if (SCOPE_PROJECT.equals(scope) || SCOPE_ENVIRONMENT.equals(scope)) return scope;
        return SCOPE_USER;
    }

    /** Returns {@code value}, or an empty string when null. */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
