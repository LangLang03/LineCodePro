package cn.lineai.tool.builtin;

import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
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
        builder.append("调用自定义 Agent「").append(agent.getName()).append("」。");
        if (agent.getTrigger().length() > 0) {
            builder.append("\n触发条件: ").append(agent.getTrigger());
        }
        builder.append("\n能力说明: ").append(agent.getPrompt().length() > 900
                ? agent.getPrompt().substring(0, 900)
                : agent.getPrompt());
        return builder.toString();
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("task", new JSONObject().put("type", "string").put("description", "交给此自定义 Agent 完成的具体任务"))
                        .put("context", new JSONObject().put("type", "string").put("description", "可选补充上下文、文件路径、限制或验收方式"))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "允许读取的文件或目录路径列表"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "允许写入的唯一文件或目录路径列表。没有写入范围时自定义 Agent 不能写文件")))
                .put("required", new JSONArray().put("task"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String task = input == null ? "" : input.optString("task").trim();
        if (task.length() == 0) {
            return error("自定义 Agent 任务不能为空。");
        }
        if (context == null || context.getAgentRunner() == null) {
            return error("Agent 执行器未接入，无法运行自定义 Agent。");
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
            return context.getAgentRunner().runAgent(delegated, context);
        } catch (Exception e) {
            return error("自定义 Agent 执行失败: " + e.getMessage());
        }
    }

    private String buildPrompt(String task, String extraContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是自定义 Agent「").append(agent.getName()).append("」（").append(agent.getSlug()).append("）。\n\n");
        builder.append("## Agent 定义\n").append(agent.getPrompt()).append("\n\n");
        if (agent.getTrigger().length() > 0) {
            builder.append("## 触发条件\n").append(agent.getTrigger()).append("\n\n");
        }
        if (!agent.getToolNames().isEmpty()) {
            builder.append("## 期望工具范围\n").append(agent.getToolNames()).append("\n\n");
        }
        if (!agent.getMcpIds().isEmpty()) {
            builder.append("## 期望 MCP 范围\n").append(agent.getMcpIds()).append("\n\n");
        }
        builder.append("## 当前任务\n").append(task);
        if (extraContext != null && extraContext.trim().length() > 0) {
            builder.append("\n\n## 补充上下文\n").append(extraContext.trim());
        }
        return builder.toString();
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
