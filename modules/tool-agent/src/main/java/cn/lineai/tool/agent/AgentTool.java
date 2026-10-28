package cn.lineai.tool.agent;

import org.json.JSONObject;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** One enabled custom agent exposed as a tool. */
final class AgentTool implements Tool {

    private final AgentExtension agent;
    private final String name;
    private final AgentRunner runner;

    AgentTool(AgentExtension agent, String name, AgentRunner runner) {
        this.agent = agent;
        this.name = name;
        this.runner = runner;
    }

    @Override public String name() { return name; }

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

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("task", "Task handed to the agent")
                .string("context", "Optional context")
                .required("task").build();
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation(agent.name.isEmpty() ? agent.slug : agent.name,
                ToolIcon.AGENT, arguments == null ? "" : arguments.optString("task", ""),
                ToolPresentation.reason(arguments));
    }

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
