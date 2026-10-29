package cn.lineai.feature.chat.toolcall;

/**
 * Optional bridge to the persisted file-diff store.
 *
 * <p>The chat feature never depends on the data module: the composition root may install a lookup
 * that resolves a diff id to its before/after snapshot. When no lookup is installed (or it returns
 * {@code null}) the write card falls back to the request arguments, so it still renders something
 * meaningful.</p>
 */
public interface ToolDiffLookup {

    /** Returns the snapshot for {@code diffId}, or {@code null} when it is unknown. */
    Snapshot find(String diffId);

    /** Immutable before/after snapshot of one file mutation. */
    final class Snapshot {
        public final String path;
        public final String before;
        public final String after;

        public Snapshot(String path, String before, String after) {
            this.path = path == null ? "" : path;
            this.before = before == null ? "" : before;
            this.after = after == null ? "" : after;
        }
    }
}
