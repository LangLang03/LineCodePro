package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;


public abstract class AbstractToolCallBuilder {
    protected String id = "";
    protected String name = "";
    protected final StringBuilder arguments = new StringBuilder();

    public boolean hasName() {
        return name.length() > 0;
    }

    public ToolCall build(int index) {
        String resolvedId = id.length() == 0 ? defaultId(index) : id;
        String resolvedArgs = arguments.length() == 0 ? "{}" : arguments.toString();
        return new ToolCall(resolvedId, name, resolvedArgs);
    }

    public ToolCall buildWithId() {
        String resolvedArgs = arguments.length() == 0 ? "{}" : arguments.toString();
        return new ToolCall(id, name, resolvedArgs);
    }

    protected String defaultId(int index) {
        return "call_" + index + "_" + System.currentTimeMillis();
    }
}
