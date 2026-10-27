package cn.lineai.data.api;

/** Computes storage usage and performs the destructive cleanup actions. */
public interface StorageStatsStore {

    StorageStats load();

    void clearChatHistory();

    void clearDiffCache();
}
