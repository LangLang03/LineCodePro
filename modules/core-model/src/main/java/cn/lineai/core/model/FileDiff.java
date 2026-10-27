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
