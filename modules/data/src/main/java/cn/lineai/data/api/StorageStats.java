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

    public long totalBytes() {
        return chatBytes + configBytes + homeBytes + diffBytes;
    }
}
