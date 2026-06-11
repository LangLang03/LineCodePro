package cn.lineai.tool;

public final class ToolResult {
    private final String toolCallId;
    private final String toolName;
    private final String content;
    private final boolean error;
    private final String diffId;
    private final String reviewState;
    private final String reviewMessage;

    public ToolResult(String toolCallId, String toolName, String content, boolean error) {
        this(toolCallId, toolName, content, error, "", "", "");
    }

    public ToolResult(
            String toolCallId,
            String toolName,
            String content,
            boolean error,
            String diffId,
            String reviewState,
            String reviewMessage
    ) {
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.toolName = toolName == null ? "" : toolName;
        this.content = content == null ? "" : content;
        this.error = error;
        this.diffId = diffId == null ? "" : diffId;
        this.reviewState = reviewState == null ? "" : reviewState;
        this.reviewMessage = reviewMessage == null ? "" : reviewMessage;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getContent() {
        return content;
    }

    public boolean isError() {
        return error;
    }

    public String getDiffId() {
        return diffId;
    }

    public String getReviewState() {
        return reviewState;
    }

    public String getReviewMessage() {
        return reviewMessage;
    }

    public ToolResult withCall(String nextToolCallId, String nextToolName) {
        return new ToolResult(nextToolCallId, nextToolName, content, error, diffId, reviewState, reviewMessage);
    }

    public ToolResult withDiffId(String nextDiffId) {
        return new ToolResult(toolCallId, toolName, content, error, nextDiffId, reviewState, reviewMessage);
    }

    public ToolResult withReview(String nextReviewState, String nextReviewMessage) {
        return new ToolResult(toolCallId, toolName, content, error, diffId, nextReviewState, nextReviewMessage);
    }
}
