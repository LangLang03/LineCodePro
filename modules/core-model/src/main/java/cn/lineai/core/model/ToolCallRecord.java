package cn.lineai.core.model;

/** Tool call remembered on an assistant message so protocols can replay it. */
public final class ToolCallRecord {

    public final String id;
    public final String name;
    public final String arguments;

    public ToolCallRecord(String id, String name, String arguments) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "{}" : arguments;
    }
}
