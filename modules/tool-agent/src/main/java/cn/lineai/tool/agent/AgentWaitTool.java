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
 * Blocks until any tracked sub agent changes status or the timeout elapses, then reports
 * the status snapshot. The tool result is the status list only — each sub agent's output
 * reaches the loop through the runtime event inbox.
 */
final class AgentWaitTool implements Tool {

    private static final long MAX_WAIT_MS = 300_000;
    private static final long DEFAULT_WAIT_MS = 30_000;

    private final AgentRuntime runtime;

    /**
     * Creates the tool.
     *
     * @param runtime runtime whose status changes end the wait early
     */
    AgentWaitTool(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    /** {@inheritDoc} */
    @Override public String name() { return "agent_wait"; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Wait for sub agent activity: blocks until any sub agent changes status"
                + " (finished, failed, needs input) or the timeout elapses, then returns the"
                + " status of every session.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .number("timeout_ms", "Maximum wait in milliseconds (default 30000, max 300000)")
                .build();
    }

    /** The registry is process-local; available in every workspace mode. */
    @Override public boolean needsLocalWorkspace() { return false; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Sub agents", ToolIcon.AGENT, "wait for activity",
                ToolPresentation.reason(arguments));
    }

    /** {@inheritDoc} */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        long timeoutMs = DEFAULT_WAIT_MS;
        if (arguments != null) {
            long requested = arguments.optLong("timeout_ms", DEFAULT_WAIT_MS);
            if (requested > 0) timeoutMs = Math.min(requested, MAX_WAIT_MS);
        }
        List<AgentEntry> entries = runtime.waitFor(timeoutMs);
        if (entries.isEmpty()) {
            return ToolResult.success("", "(no sub agent sessions)");
        }
        StringBuilder out = new StringBuilder();
        for (AgentEntry entry : entries) {
            if (out.length() > 0) out.append('\n');
            out.append("id=\"").append(entry.id).append("\" name=\"").append(entry.name)
                    .append("\" status=").append(entry.status.name().toLowerCase(
                            java.util.Locale.ROOT));
        }
        return ToolResult.success("", out.toString());
    }
}
