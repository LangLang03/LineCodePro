package cn.lineai.ai.message;

public final class ToolModelMessage extends ModelMessage {
    private final String toolCallId;
    private final String toolName;
    private final boolean toolError;

    public ToolModelMessage(String content) {
        this(content, "", "", false);
    }

    public ToolModelMessage(String content, String toolCallId, String toolName) {
        this(content, toolCallId, toolName, false);
    }

    public ToolModelMessage(String content, String toolCallId, String toolName, boolean toolError) {
        super(content);
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.toolName = toolName == null ? "" : toolName;
        this.toolError = toolError;
    }

    @Override
    public String getToolCallId() {
        return toolCallId;
    }

    @Override
    public String getToolName() {
        return toolName;
    }

    @Override
    public boolean isToolError() {
        return toolError;
    }

    @Override
    public String getRole() {
        return "tool";
    }
}
