package cn.lineai.tool.agent;

import org.json.JSONObject;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * One enabled custom agent exposed as a tool.
 *
 * <p>The tool description embeds the agent's trigger, tool names and a clipped prompt so the
 * outer model can decide when delegation is appropriate without reading the full prompt.</p>
 */
final class AgentTool implements Tool {

    private final AgentExtension agent;
    private final String name;
    private final AgentRunner runner;

    /**
     * Creates the tool.
     *
     * @param agent extension describing the custom agent
     * @param name local, collision-free tool name assigned by {@link AgentTools}
     * @param runner runner executing the agent task in a nested loop
     */
    AgentTool(AgentExtension agent, String name, AgentRunner runner) {
        this.agent = agent;
        this.name = name;
        this.runner = runner;
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
        text.append("Invoke the custom Agent \"").append(agent.name).append("\".");
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
                .required("task").build();
    }

    /** {@inheritDoc} */
    @Override public boolean needsLocalWorkspace() { return true; }

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
     * before the nested loop runs. Empty agent output becomes a failure result so the outer
     * loop can retry instead of treating silence as success.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String task = arguments == null ? "" : arguments.optString("task", "").trim();
        if (task.isEmpty()) return ToolResult.failure("", "task must not be empty");
        String extra = arguments.optString("context", "").trim();
        if (!extra.isEmpty()) task = task + "\n\nContext:\n" + extra;
        try {
            String result = runner.run(agent, task);
            return result == null || result.isEmpty()
                    ? ToolResult.failure("", "Agent returned no text")
                    : ToolResult.success("", result);
        } catch (Exception error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
}
