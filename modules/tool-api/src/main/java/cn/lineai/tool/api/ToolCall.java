package cn.lineai.tool.api;

import java.util.Objects;

/** A model-requested tool invocation. */
public final class ToolCall {

    public final String id;
    public final String name;
    public final String arguments;

    public ToolCall(String id, String name, String arguments) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
    }

    public boolean isEmpty() {
        return name.isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ToolCall)) return false;
        ToolCall that = (ToolCall) other;
        return id.equals(that.id) && name.equals(that.name) && arguments.equals(that.arguments);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, arguments);
    }
}
