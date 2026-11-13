package cn.lineai.data.api;

/**
 * Computes storage usage and performs the destructive cleanup actions.
 *
 * <p>Implementations are synchronous database and file-system scans; callers must invoke from a
 * background dispatcher. Both clear operations are irreversible and exist for the storage screen.</p>
 */
public interface StorageStatsStore {

    /**
     * Snapshots the current usage for every tracked category.
     *
     * @return aggregated byte and entry counts for the chat, configuration, home and diff categories
     */
    StorageStats load();

    /** Deletes every conversation, message and content block. The operation cannot be undone. */
    void clearChatHistory();

    /** Deletes every recorded file diff snapshot; workspace files themselves are not touched. */
    void clearDiffCache();
}
