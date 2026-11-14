package cn.lineai.data.api;

/** Storage usage broken down by the categories shown in the storage screen. */
public final class StorageStats {

    public final long diffBytes;
    public final int diffCount;
    public final long chatBytes;
    public final int chatCount;
    public final long configBytes;
    public final int configCount;
    public final long homeBytes;
    public final int homeCount;

    /**
     * Creates a usage snapshot.
     *
     * @param diffBytes bytes occupied by stored file diffs
     * @param diffCount number of stored file diff rows
     * @param chatBytes bytes occupied by conversation text
     * @param chatCount number of message rows
     * @param configBytes bytes occupied by settings and model rows
     * @param configCount number of settings and model rows
     * @param homeBytes bytes occupied by files under the app home directory
     * @param homeCount number of files under the app home directory
     */
    public StorageStats(long diffBytes, int diffCount, long chatBytes, int chatCount,
                        long configBytes, int configCount, long homeBytes, int homeCount) {
        this.diffBytes = diffBytes;
        this.diffCount = diffCount;
        this.chatBytes = chatBytes;
        this.chatCount = chatCount;
        this.configBytes = configBytes;
        this.configCount = configCount;
        this.homeBytes = homeBytes;
        this.homeCount = homeCount;
    }

    /**
     * Sums the byte counts of all categories.
     *
     * @return total bytes reported by this snapshot
     */
    public long totalBytes() {
        return chatBytes + configBytes + homeBytes + diffBytes;
    }
}
