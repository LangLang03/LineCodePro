package cn.lineai.data.repository;

public final class DiffRecord {
    private final String id;
    private final String filePath;
    private final String oldContent;
    private final String newContent;
    private final boolean oldExists;
    private final long timestamp;
    private final boolean reverted;

    public DiffRecord(
            String id,
            String filePath,
            String oldContent,
            String newContent,
            boolean oldExists,
            long timestamp,
            boolean reverted
    ) {
        this.id = id == null ? "" : id;
        this.filePath = filePath == null ? "" : filePath;
        this.oldContent = oldContent == null ? "" : oldContent;
        this.newContent = newContent == null ? "" : newContent;
        this.oldExists = oldExists;
        this.timestamp = timestamp;
        this.reverted = reverted;
    }

    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOldContent() {
        return oldContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public boolean isOldExists() {
        return oldExists;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isReverted() {
        return reverted;
    }
}
