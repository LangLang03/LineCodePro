package cn.lineai.tool.api;

/** Result of one tool invocation. */
public final class ToolResult {

    public final String callId;
    public final String content;
    public final boolean error;
    public final boolean truncated;
    public final String imagePath;

    private ToolResult(String callId, String content, boolean error, boolean truncated,
                       String imagePath) {
        this.callId = callId == null ? "" : callId;
        this.content = content == null ? "" : content;
        this.error = error;
        this.truncated = truncated;
        this.imagePath = imagePath == null ? "" : imagePath;
    }

    public static ToolResult success(String callId, String content) {
        return new ToolResult(callId, content, false, false, "");
    }

    public static ToolResult failure(String callId, String message) {
        return new ToolResult(callId, message, true, false, "");
    }

    public static ToolResult successWithImage(String callId, String content, String imagePath) {
        return new ToolResult(callId, content, false, false, imagePath);
    }

    public static ToolResult truncated(String callId, String content) {
        return new ToolResult(callId, content, false, true, "");
    }
}
