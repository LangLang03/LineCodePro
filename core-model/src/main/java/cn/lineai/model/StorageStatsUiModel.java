package cn.lineai.model;

/**
 * UI layer representation of storage statistics, decoupled from StorageStatsRepository.
 * Immutable snapshot: all fields final, no setters.
 */
public final class StorageStatsUiModel {
    private final long totalSize;
    private final int totalCount;
    private final long diffCacheSize;
    private final int diffCacheCount;
    private final long chatSize;
    private final int chatCount;
    private final long configSize;
    private final int configCount;
    private final long homeSize;
    private final int homeCount;

    public StorageStatsUiModel(
            long totalSize,
            int totalCount,
            long diffCacheSize,
            int diffCacheCount,
            long chatSize,
            int chatCount,
            long configSize,
            int configCount,
            long homeSize,
            int homeCount
    ) {
        this.totalSize = totalSize;
        this.totalCount = totalCount;
        this.diffCacheSize = diffCacheSize;
        this.diffCacheCount = diffCacheCount;
        this.chatSize = chatSize;
        this.chatCount = chatCount;
        this.configSize = configSize;
        this.configCount = configCount;
        this.homeSize = homeSize;
        this.homeCount = homeCount;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public long getDiffCacheSize() {
        return diffCacheSize;
    }

    public int getDiffCacheCount() {
        return diffCacheCount;
    }

    public long getChatSize() {
        return chatSize;
    }

    public int getChatCount() {
        return chatCount;
    }

    public long getConfigSize() {
        return configSize;
    }

    public int getConfigCount() {
        return configCount;
    }

    public long getHomeSize() {
        return homeSize;
    }

    public int getHomeCount() {
        return homeCount;
    }

    public String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        if (bytes < 1024 * 1024 * 1024) {
            long mb = bytes / (1024 * 1024);
            return mb + " MB";
        }
        long gb = bytes / (1024 * 1024 * 1024);
        return gb + " GB";
    }

    public String formatTotalSize() {
        return formatSize(totalSize);
    }

    public String formatDiffCacheSize() {
        return formatSize(diffCacheSize);
    }

    public String formatChatSize() {
        return formatSize(chatSize);
    }

    public String formatConfigSize() {
        return formatSize(configSize);
    }

    public String formatHomeSize() {
        return formatSize(homeSize);
    }
}
