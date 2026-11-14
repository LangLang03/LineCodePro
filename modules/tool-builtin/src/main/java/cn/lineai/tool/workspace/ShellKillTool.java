package cn.lineai.tool.workspace;

import org.json.JSONObject;

import cn.lineai.tool.api.BackgroundTasks;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Stops a background shell task started by the shell tool.
 *
 * <p>Part of the unified shell family; killing is routed to the same local or remote
 * environment that started the task.</p>
 */
public final class ShellKillTool implements Tool {

    /** Model-facing tool name; removed together with the family by {@link LocalToolPolicy}. */
    public static final String NAME = "shell_kill";

    /** {@inheritDoc} */
    @Override public String name() { return NAME; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Stop a background shell task by the id returned from shell.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("id", "Background task id returned by shell")
                .required("id").build();
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return true; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Kill task", ToolIcon.TERMINAL,
                arguments.optString("id", ""), ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Denies the request when mutations are not allowed, when no background registry is
     * available, or when the id is empty; otherwise delegates to {@link BackgroundTasks#kill}.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        if (!context.mutationAllowed()) return ToolResult.failure("", "Mutation not allowed");
        BackgroundTasks backgrounds = context.backgrounds();
        if (backgrounds == null) {
            return ToolResult.failure("", "Background tasks are unavailable");
        }
        String id = arguments.optString("id", "").trim();
        if (id.isEmpty()) return ToolResult.failure("", "id must not be empty");
        return ToolResult.success("", backgrounds.kill(id));
    }
}
