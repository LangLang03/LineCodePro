package cn.lineai.tool.workspace;

import org.json.JSONObject;

import cn.lineai.tool.api.BackgroundTasks;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Reads the output of a background shell task; can block until it finishes.
 *
 * <p>Part of the unified shell family; output is returned as a snapshot from the background
 * registry, optionally after waiting up to {@code timeout_ms}.</p>
 */
public final class ShellOutputTool implements Tool {

    /** Model-facing tool name; removed together with the family by {@link LocalToolPolicy}. */
    public static final String NAME = "shell_output";

    /** {@inheritDoc} */
    @Override public String name() { return NAME; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Read the output of a background shell task. Set block=true to wait until the "
                + "task finishes (up to timeout_ms) before returning.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("id", "Background task id returned by shell")
                .bool("block", "Wait for the task to finish before returning (default false)")
                .number("timeout_ms", "Max wait in milliseconds when block=true "
                        + "(default 30000, max 600000)")
                .required("id").build();
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Task output", ToolIcon.TERMINAL,
                arguments.optString("id", ""), ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link BackgroundTasks#output}; missing registry or empty id are reported
     * as failure results rather than thrown.</p>
     */
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
