package cn.lineai.tool.agent;

import org.json.JSONObject;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * One enabled custom agent exposed as a tool.
 *
 * <p>Every invocation starts an asynchronous sub agent session: the tool returns a handle
 * immediately and the sub agent's final output reaches the parent loop through the runtime
 * event inbox. A {@code persistent=true} invocation creates a messageable, revivable
 * session; the default is a one-shot transient run. The tool description embeds the agent's
 * trigger, tool names and a clipped prompt so the outer model can decide when delegation is
 * appropriate without reading the full prompt.</p>
 */
final class AgentTool implements Tool {

    private final AgentExtension agent;
    private final String name;
    private final AgentRuntime runtime;

    /**
     * Creates the tool.
     *
     * @param agent extension describing the custom agent
     * @param name local, collision-free tool name assigned by {@link AgentTools}
     * @param runtime runtime launching and tracking the sub agent session
     */
    AgentTool(AgentExtension agent, String name, AgentRuntime runtime) {
        this.agent = agent;
        this.name = name;
        this.runtime = runtime;
    }

    /** {@inheritDoc} */
    @Override public String name() { return name; }

    /**
     * {@inheritDoc}
     *
     * <p>Composes trigger, tool list and prompt excerpt; the prompt is clipped to 900
     * characters to bound the catalog size.</p>
     */
    @Override public String description() {
        StringBuilder text = new StringBuilder();
        text.append("Invoke the custom Agent \"").append(agent.name).append("\". The agent runs")
                .append(" asynchronously and its final output is delivered back to you.");
        if (!agent.trigger.isEmpty()) text.append("\nTrigger: ").append(agent.trigger);
        if (!agent.toolNames.isEmpty()) text.append("\nTools: ").append(agent.toolNames);
        String prompt = agent.prompt.length() > 900
                ? agent.prompt.substring(0, 900) : agent.prompt;
        if (!prompt.isEmpty()) text.append("\nCapabilities: ").append(prompt);
        return text.toString();
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("task", "Task handed to the agent")
                .string("context", "Optional context")
                .bool("persistent", "Create a messageable session that survives this turn and"
                        + " accepts follow-up messages; default false (one-shot run)")
                .required("task").build();
    }

    /**
     * Agent invocations are workspace-agnostic: the session inherits the same
     * remote-aware {@code ToolContext} (local, SSH or terminal-provider guest), so the
     * tool must stay visible in every workspace mode.
     */
    @Override public boolean needsLocalWorkspace() { return false; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation(agent.name.isEmpty() ? agent.slug : agent.name,
                ToolIcon.AGENT, arguments == null ? "" : arguments.optString("task", ""),
                ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>An empty task is rejected locally; optional context is appended to the task text
     * before the session starts. The returned result is the launch confirmation with the
     * agent identifier, not the agent's answer.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        if (runtime == null) return ToolResult.failure("", "Sub agent runtime is unavailable");
        String task = arguments == null ? "" : arguments.optString("task", "").trim();
        if (task.isEmpty()) return ToolResult.failure("", "task must not be empty");
        String extra = arguments.optString("context", "").trim();
        if (!extra.isEmpty()) task = task + "\n\nContext:\n" + extra;
        boolean persistent = arguments != null && arguments.optBoolean("persistent", false);
        AgentEntry started = runtime.start(agent, task, persistent);
        if (started == null) return ToolResult.failure("", "Sub agent runtime refused the task");
        return ToolResult.success("", "Started sub agent \"" + started.name + "\" id=\""
                + started.id + "\" (" + (persistent ? "persistent" : "transient")
                + ", status " + started.status.name().toLowerCase(java.util.Locale.ROOT)
                + "). Its final output will be delivered to you when it finishes"
                + (persistent ? "; send it messages with agent_send and check status with"
                + " agent_list or agent_wait." : "."));
    }
}
