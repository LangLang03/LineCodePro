package cn.lineai.tool;

public final class ToolCall {
    private final String id;
    private final String name;
    private final String arguments;

    public ToolCall(String id, String name, String arguments) {
        this.id = id == null || id.length() == 0 ? "tool_call_" + System.currentTimeMillis() : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "{}" : arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }
}
