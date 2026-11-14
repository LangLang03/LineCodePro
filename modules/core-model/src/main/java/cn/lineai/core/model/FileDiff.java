package cn.lineai.core.model;

/** Before/after snapshot of one file mutation, used for rollback. */
public final class FileDiff {

    public final String id;
    public final String rootPath;
    public final String path;
    public final String before;
    public final String after;
    public final long createdAt;
    public final boolean reverted;
    public final boolean existedBefore;

    /**
     * Creates a snapshot, normalising null strings to empty.
     * @param id stable diff identifier
     * @param rootPath workspace root the file belongs to
     * @param path file path relative to the root
     * @param before content prior to the mutation
     * @param after content following the mutation
     * @param createdAt creation timestamp in epoch milliseconds
     * @param reverted whether the mutation has already been rolled back
     * @param existedBefore whether the file existed prior to the mutation
     */
    public FileDiff(String id, String rootPath, String path, String before, String after,
                    long createdAt, boolean reverted, boolean existedBefore) {
        this.id = id == null ? "" : id;
        this.rootPath = rootPath == null ? "" : rootPath;
        this.path = path == null ? "" : path;
        this.before = before == null ? "" : before;
        this.after = after == null ? "" : after;
        this.createdAt = createdAt;
        this.reverted = reverted;
        this.existedBefore = existedBefore;
    }
}
