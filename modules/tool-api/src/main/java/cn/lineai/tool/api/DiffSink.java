package cn.lineai.tool.api;

/**
 * Receives file snapshots from mutating tools.
 *
 * <p>Implementations persist before/after pairs so the workspace history stays independent of
 * the tool implementations; a {@code null} sink means diffs are simply not recorded.</p>
 */
public interface DiffSink {

    /**
     * Records one file mutation for history tracking.
     *
     * @param path workspace-relative path of the changed file
     * @param before file content before the mutation; empty for a newly created file
     * @param after file content after the mutation; empty for a deleted file
     * @param existedBefore {@code true} when the file already existed before the mutation
     */
    void record(String path, String before, String after, boolean existedBefore);
}
