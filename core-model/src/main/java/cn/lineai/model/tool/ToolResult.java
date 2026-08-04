package cn.lineai.model.tool;

import cn.lineai.model.Strings;

public final class ToolResult {
    private final String toolCallId;
    private final String toolName;
    private final String content;
    private final boolean error;
    private final String diffId;
    private final String reviewState;
    private final String reviewMessage;

    /** 工具结果内容最大字符数（50KB），超过此限制时执行中间截断。 */
    public static final int MAX_TOOL_RESULT_CHARS = 50 * 1024;

    /** 中间截断时保留的首/尾各半字符数。 */
    private static final int TRUNCATION_HALF = MAX_TOOL_RESULT_CHARS / 2;

    public ToolResult(
            String toolCallId,
            String toolName,
            String content,
            boolean error,
            String diffId,
            String reviewState,
            String reviewMessage
    ) {
        this.toolCallId = Strings.nullToEmpty(toolCallId);
        this.toolName = Strings.nullToEmpty(toolName);
        this.content = Strings.nullToEmpty(content);
        this.error = error;
        this.diffId = Strings.nullToEmpty(diffId);
        this.reviewState = Strings.nullToEmpty(reviewState);
        this.reviewMessage = Strings.nullToEmpty(reviewMessage);
    }

    public static ToolResult of(String toolCallId, String toolName, String content, boolean error) {
        return new ToolResult(toolCallId, toolName, content, error, "", "", "");
    }

    public static ToolResult success(String output) {
        return new ToolResult("", "", output, false, "", "", "");
    }

    public static ToolResult error(String error) {
        return new ToolResult("", "", error, true, "", "", "");
    }

    public static ToolResult withReview(String output, String toolCallId, String toolName,
                                         String diffId, String reviewState, String reviewMessage) {
        return new ToolResult(toolCallId, toolName, output, false, diffId, reviewState, reviewMessage);
    }

    public static ToolResult withReview(String toolCallId, String toolName, String content,
                                         boolean error, String diffId, String reviewState, String reviewMessage) {
        return new ToolResult(toolCallId, toolName, content, error, diffId, reviewState, reviewMessage);
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

    /**
     * 对内容执行中间截断：当内容超过 {@link #MAX_TOOL_RESULT_CHARS} 时，
     * 保留首 TRUNCATION_HALF 字符 + 截断标记 + 尾 TRUNCATION_HALF 字符。
     * 不超过限制时原样返回。
     *
     * @param content 原始内容
     * @return 截断后的内容，或原始内容（如未超限）
     */
    public static String truncateContent(String content) {
        if (content == null || content.length() <= MAX_TOOL_RESULT_CHARS) {
            return content;
        }
        int truncated = content.length() - MAX_TOOL_RESULT_CHARS;
        return content.substring(0, TRUNCATION_HALF)
                + "\n... (" + truncated + " chars truncated) ...\n"
                + content.substring(content.length() - TRUNCATION_HALF);
    }
}
