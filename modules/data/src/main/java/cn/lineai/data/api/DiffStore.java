package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.FileDiff;

/**
 * File changes produced by tools, with rollback support.
 *
 * <p>Each edit stores the full before and after text so a change can be reverted without a version
 * control system. Implementations are synchronous; callers own the scheduler.</p>
 */
public interface DiffStore {

    /**
     * Records one file change snapshot.
     *
     * @param rootPath workspace root that the path is relative to
     * @param path workspace-relative file path
     * @param before full file content before the change, or empty when the file did not exist
     * @param after full file content after the change
     * @param existedBefore whether the file existed prior to the change; revert deletes it when false
     */
    void record(String rootPath, String path, String before, String after, boolean existedBefore);

    /**
     * Lists the recorded changes, newest first.
     *
     * @return every stored diff, including entries that were already reverted
     */
    List<FileDiff> list();

    /**
     * Restores the recorded before-content of one diff and marks it reverted.
     *
     * @param id diff identifier
     * @return true when the file was restored, false when the diff is unknown or already reverted
     */
    boolean revert(String id);

    /** Deletes every recorded diff without touching workspace files. */
    void clear();
}
