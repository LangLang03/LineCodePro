package cn.lineai.tool.workspace;

import org.json.JSONObject;

import cn.lineai.tool.api.BackgroundTasks;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** Reads the output of a background shell task; can block until it finishes. */
public final class ShellOutputTool implements Tool {

    public static final String NAME = "shell_output";

    @Override public String name() { return NAME; }

    @Override public String description() {
        return "Read the output of a background shell task. Set block=true to wait until the "
                + "task finishes (up to timeout_ms) before returning.";
    }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("id", "Background task id returned by shell")
                .bool("block", "Wait for the task to finish before returning (default false)")
                .number("timeout_ms", "Max wait in milliseconds when block=true "
                        + "(default 30000, max 600000)")
                .required("id").build();
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Task output", ToolIcon.TERMINAL,
                arguments.optString("id", ""), ToolPresentation.reason(arguments));
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        BackgroundTasks backgrounds = context.backgrounds();
        if (backgrounds == null) {
            return ToolResult.failure("", "Background tasks are unavailable");
        }
        String id = arguments.optString("id", "").trim();
        if (id.isEmpty()) return ToolResult.failure("", "id must not be empty");
        int timeoutMs = (int) ShellTool.clampTimeout(arguments,
                ShellTool.DEFAULT_OUTPUT_TIMEOUT_MS);
        return ToolResult.success("",
                backgrounds.output(id, arguments.optBoolean("block", false), timeoutMs));
    }
}
