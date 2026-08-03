package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CustomAgentExtensionTool extends BaseTool {
    private final String name;
    private final ExtensionAgentConfig agent;

    public CustomAgentExtensionTool(String name, ExtensionAgentConfig agent) {
        this.name = name == null ? "" : name;
        this.agent = agent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("Invoke the custom Agent \"").append(agent.getName()).append("\".");
        if (agent.getTrigger().length() > 0) {
            builder.append("\nTrigger: ").append(agent.getTrigger());
        }
        builder.append("\nCapabilities: ").append(agent.getPrompt().length() > 900
                ? agent.getPrompt().substring(0, 900)
                : agent.getPrompt());
        return builder.toString();
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.AGENT;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("task", new JSONObject().put("type", "string").put("description", "The specific task to hand to this custom Agent"))
                        .put("context", new JSONObject().put("type", "string").put("description", "Optional supplementary context, file paths, constraints, or acceptance criteria"))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "List of files or directories allowed to read"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "List of unique files or directories allowed to write. Without a write scope, the custom Agent cannot write files")))
                .put("required", new JSONArray().put("task"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String task = input == null ? "" : input.optString("task").trim();
        if (task.length() == 0) {
            return error(context != null ? context.getString(R.string.tool_custom_agent_task_empty) : "Custom Agent task cannot be empty.");
        }
        if (context == null || context.getAgentRunner() == null) {
            return error(context != null ? context.getString(R.string.tool_custom_agent_runner_not_available) : "Agent runner not available, cannot run custom Agent.");
        }
        try {
            String prompt = buildPrompt(task, input.optString("context"));
            JSONObject delegated = new JSONObject()
                    .put("type", AgentTool.TYPE_SUB_CODING)
                    .put("description", agent.getName())
                    .put("prompt", prompt);
            JSONArray readScope = input.optJSONArray("read_scope");
            if (readScope != null) {
                delegated.put("read_scope", readScope);
            }
            JSONArray writeScope = input.optJSONArray("write_scope");
            if (writeScope != null) {
                delegated.put("write_scope", writeScope);
            }
            if (!agent.getToolNames().isEmpty()) {
                delegated.put("custom_tool_names", new JSONArray(agent.getToolNames()));
            }
            if (!agent.getMcpIds().isEmpty()) {
                delegated.put("custom_mcp_ids", new JSONArray(agent.getMcpIds()));
            }
            return context.getAgentRunner().runAgent(delegated, context);
        } catch (Exception e) {
            return error(context.getString(R.string.tool_custom_agent_failed, e.getMessage()));
        }
    }

    private String buildPrompt(String task, String extraContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the custom Agent \"").append(agent.getName()).append("\" (").append(agent.getSlug()).append(").\n\n");
        builder.append("## Agent Definition\n").append(agent.getPrompt()).append("\n\n");
        if (agent.getTrigger().length() > 0) {
            builder.append("## Trigger\n").append(agent.getTrigger()).append("\n\n");
        }
        if (!agent.getToolNames().isEmpty()) {
            builder.append("## Expected Tool Scope\n").append(agent.getToolNames()).append("\n\n");
        }
        if (!agent.getMcpIds().isEmpty()) {
            builder.append("## Expected MCP Scope\n").append(agent.getMcpIds()).append("\n\n");
        }
        builder.append("## Current Task\n").append(task);
        if (extraContext != null && extraContext.trim().length() > 0) {
            builder.append("\n\n## Supplementary Context\n").append(extraContext.trim());
        }
        return builder.toString();
    }
}
