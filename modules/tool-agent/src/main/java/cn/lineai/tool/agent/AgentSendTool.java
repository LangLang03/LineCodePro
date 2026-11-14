package cn.lineai.tool.agent;

import org.json.JSONObject;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Sends a message to a persistent sub agent; a finished agent is revived with a new turn
 * and a running agent receives the message as a queued runtime event.
 */
final class AgentSendTool implements Tool {

    private final AgentRuntime runtime;

    /**
     * Creates the tool.
     *
     * @param runtime runtime routing the message
     */
    AgentSendTool(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    /** {@inheritDoc} */
    @Override public String name() { return "agent_send"; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Send a message to a persistent sub agent. A finished agent is revived with a"
                + " new turn; a running agent receives the message at its next input point.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("agent_id", "Sub agent id, as reported by agent_list")
                .string("message", "Message body delivered to the sub agent")
                .required("agent_id").required("message").build();
    }

    /** The registry is process-local; available in every workspace mode. */
    @Override public boolean needsLocalWorkspace() { return false; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Sub agents", ToolIcon.AGENT,
                "send → " + (arguments == null ? "" : arguments.optString("agent_id", "")),
                ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Empty fields are rejected locally; delivery failures surface as failure results so
     * the model can retry or report to the user.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String agentId = arguments == null ? "" : arguments.optString("agent_id", "").trim();
        String message = arguments == null ? "" : arguments.optString("message", "").trim();
        if (agentId.isEmpty() || message.isEmpty()) {
            return ToolResult.failure("", "agent_id and message must not be empty");
        }
        return ToolResult.success("", runtime.send(agentId, message));
    }
}
