package cn.lineai.tool.agent;

import org.json.JSONObject;

import java.util.List;

import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Lists every sub agent session with its live status, so the model can track delegated work.
 */
final class AgentListTool implements Tool {

    private final AgentRuntime runtime;

    /**
     * Creates the tool.
     *
     * @param runtime runtime tracking the sub agent sessions
     */
    AgentListTool(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    /** {@inheritDoc} */
    @Override public String name() { return "agent_list"; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "List all sub agent sessions with id, name, kind and current status"
                + " (running / completed / failed / waiting_input).";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object().build();
    }

    /** The registry is process-local; available in every workspace mode. */
    @Override public boolean needsLocalWorkspace() { return false; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Sub agents", ToolIcon.AGENT, "list sessions",
                ToolPresentation.reason(arguments));
    }

    /** {@inheritDoc} */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        List<AgentEntry> entries = runtime.list();
        if (entries.isEmpty()) {
            return ToolResult.success("", "(no sub agent sessions)");
        }
        StringBuilder out = new StringBuilder();
        for (AgentEntry entry : entries) {
            if (out.length() > 0) out.append('\n');
            out.append("id=\"").append(entry.id).append("\" name=\"").append(entry.name)
                    .append("\" kind=").append(entry.kind.isEmpty() ? "transient" : entry.kind)
                    .append(" status=").append(entry.status.name().toLowerCase(
                            java.util.Locale.ROOT));
        }
        return ToolResult.success("", out.toString());
    }
}
