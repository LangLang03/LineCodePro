package cn.lineai.engine.protocol;

/** Partially or fully assembled tool call. */
public final class ToolCallDraft {

    public final int index;
    public final String id;
    public final String name;
    public final String arguments;

    public ToolCallDraft(int index, String id, String name, String arguments) {
        this.index = index;
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
    }

    public boolean complete() {
        return !name.isEmpty();
    }
}
