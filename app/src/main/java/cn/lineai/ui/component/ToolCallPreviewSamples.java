package cn.lineai.ui.component;

import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolDisplayCategory;

/**
 * ToolCall 预览页的模拟数据：为每个显示分类提供一组 ToolCall + ToolResult。
 * 纯静态、无 Android 依赖，便于单元测试。
 */
public final class ToolCallPreviewSamples {

    public static final class Sample {
        public final ToolCall call;
        public final ToolResult result;

        Sample(ToolCall call, ToolResult result) {
            this.call = call;
            this.result = result;
        }
    }

    private ToolCallPreviewSamples() {
    }

    public static Sample forCategory(ToolDisplayCategory category) {
        if (category == null) {
            return null;
        }
        switch (category) {
            case READ:
                return new Sample(
                        new ToolCall("preview_read", "file_read", "{\"file_path\":\"app/src/main/java/cn/lineai/MainActivity.java\"}"),
                        ToolResult.withReview("preview_read", "file_read", "Read 128 lines (3.2 KB)", false, "", "", ""));
            case WRITE:
                return new Sample(
                        new ToolCall("preview_write", "file_write", "{\"file_path\":\"app/src/main/java/cn/lineai/MainActivity.java\"}"),
                        ToolResult.withReview("preview_write", "file_write", "", false, "preview_diff", "pending", ""));
            case DELETE:
                return new Sample(
                        new ToolCall("preview_delete", "file_delete", "{\"file_path\":\"app/src/main/java/cn/lineai/TodoActivity.java\"}"),
                        ToolResult.withReview("preview_delete", "file_delete", "Deleted 1 file", false, "", "accepted", ""));
            case SHELL:
                return new Sample(
                        new ToolCall("preview_shell", "shell_execute", "{\"command\":\"gradlew :app:assembleDebug\"}"),
                        ToolResult.withReview("preview_shell", "shell_execute", "BUILD SUCCESSFUL in 12s", false, "", "", ""));
            case TODO:
                return new Sample(
                        new ToolCall("preview_todo", "todo_update", "{\"description\":\"Add unit tests for the parser\",\"status\":\"in_progress\"}"),
                        ToolResult.withReview("preview_todo", "todo_update", "Todo updated", false, "", "", ""));
            case AGENT:
                return new Sample(
                        new ToolCall("preview_agent", "agent", "{\"description\":\"Explore the codebase structure\"}"),
                        ToolResult.withReview("preview_agent", "agent", "", false, "", "running", ""));
            case AGENT_PIPELINE:
                return new Sample(
                        new ToolCall("preview_pipeline", "agent_pipeline", "{\"tasks\":[{\"description\":\"Scan layout\"},{\"description\":\"Summarize\"}]}"),
                        ToolResult.withReview("preview_pipeline", "agent_pipeline", "", false, "", "pending_review", ""));
            case IMAGE_GENERATION:
                return new Sample(
                        new ToolCall("preview_image", "image_generation", "{\"prompt\":\"A terminal-themed wallpaper\"}"),
                        ToolResult.withReview("preview_image", "image_generation", "Generated image (1024x1024)", false, "", "", ""));
            case PHONE_CONTROL:
                return new Sample(
                        new ToolCall("preview_phone", "phone_click", "{\"x\":100,\"y\":200}"),
                        ToolResult.withReview("preview_phone", "phone_click", "Clicked (100, 200)", false, "", "", ""));
            case GENERIC:
            default:
                return new Sample(
                        new ToolCall("preview_mcp", "mcpx_github", "{\"repo\":\"linecode\"}"),
                        ToolResult.withReview("preview_mcp", "mcpx_github", "Fetched 3 issues", false, "", "", ""));
        }
    }

    /** 附加样例：Shell 运行中状态（result 为 null）。 */
    public static Sample runningShell() {
        return new Sample(
                new ToolCall("preview_shell_running", "shell_execute", "{\"command\":\"gradlew :app:assembleDebug\"}"),
                null);
    }
}
