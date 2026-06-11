package cn.lineai.data.repository;

public final class ProjectRecord {
    private final String id;
    private final String label;
    private final String path;
    private final String source;
    private final String description;
    private final boolean selected;
    private final long createdAt;
    private final long updatedAt;

    public ProjectRecord(
            String id,
            String label,
            String path,
            String source,
            String description,
            boolean selected,
            long createdAt,
            long updatedAt
    ) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.path = path == null ? "" : path;
        this.source = source == null ? "managed" : source;
        this.description = description == null ? "" : description;
        this.selected = selected;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getPath() {
        return path;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelected() {
        return selected;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
