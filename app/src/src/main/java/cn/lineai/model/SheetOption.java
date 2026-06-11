package cn.lineai.model;

public final class SheetOption {
    private final String id;
    private final String label;
    private final String description;
    private final boolean selected;
    private final String deleteActionId;
    private final String deleteActionLabel;

    public SheetOption(String id, String label, String description, boolean selected) {
        this(id, label, description, selected, "", "");
    }

    public SheetOption(
            String id,
            String label,
            String description,
            boolean selected,
            String deleteActionId,
            String deleteActionLabel
    ) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.selected = selected;
        this.deleteActionId = deleteActionId == null ? "" : deleteActionId;
        this.deleteActionLabel = deleteActionLabel == null ? "" : deleteActionLabel;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelected() {
        return selected;
    }

    public String getDeleteActionId() {
        return deleteActionId;
    }

    public String getDeleteActionLabel() {
        return deleteActionLabel.length() == 0 ? "删除" : deleteActionLabel;
    }

    public boolean hasDeleteAction() {
        return deleteActionId.length() > 0;
    }
}
