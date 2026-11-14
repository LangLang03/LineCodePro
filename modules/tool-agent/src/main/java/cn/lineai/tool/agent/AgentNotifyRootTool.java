package cn.lineai.tool.agent;

import org.json.JSONObject;

import cn.lineai.tool.api.RuntimeEvent;
import cn.lineai.tool.api.RuntimeInbox;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Sub agent side of the message bus: posts one message to the parent agent's runtime inbox
 * without ending the current turn. The parent sees it as a hidden user message at its next
 * input point.
 */
public final class AgentNotifyRootTool implements Tool {

    /** Tool name visible inside a sub agent's registry. */
    static final String NAME = "agent_message_root";

    private final String agentId;
    private final String agentName;
    private final RuntimeInbox rootInbox;

    /**
     * Creates the tool for one session.
     *
     * @param agentId the sending sub agent id
     * @param agentName display name of the sending agent
     * @param rootInbox the parent conversation's inbox
     */
    public AgentNotifyRootTool(String agentId, String agentName, RuntimeInbox rootInbox) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.rootInbox = rootInbox;
    }

    /** {@inheritDoc} */
    @Override public String name() { return NAME; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Send a progress note or question to the parent agent without ending your"
                + " current turn. The parent reads it at its next input point.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("message", "Message body delivered to the parent agent")
                .required("message").build();
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Message to parent", ToolIcon.AGENT_NOTIFY,
                arguments == null ? "" : arguments.optString("message", ""),
                ToolPresentation.reason(arguments));
    }

    /** {@inheritDoc} */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String message = arguments == null ? "" : arguments.optString("message", "").trim();
        if (message.isEmpty()) return ToolResult.failure("", "message must not be empty");
        if (rootInbox == null) return ToolResult.failure("", "Parent inbox is unavailable");
        rootInbox.post(new RuntimeEvent.AgentMessage(agentId,
                agentName.isEmpty() ? message : agentName + ": " + message));
        return ToolResult.success("", "Message delivered to the parent agent.");
    }
}
