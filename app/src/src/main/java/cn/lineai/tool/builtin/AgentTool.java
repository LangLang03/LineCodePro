package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentTool extends BaseTool {
    public static final String TYPE_EXPLORE = "explore";
    public static final String TYPE_SUB_CODING = "sub-coding";

    @Override
    public String getName() {
        return "agent";
    }

    @Override
    public String getDescription() {
        return "分派一个子 Agent 处理任务。explore 只能只读探索；sub-coding 必须有明确且唯一的写入范围，不能和其他 Agent 操纵同一文件。";
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
                        .put("type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put(TYPE_EXPLORE).put(TYPE_SUB_CODING))
                                .put("description", "Agent 类型：explore 只读探索，sub-coding 编程子任务"))
                        .put("description", new JSONObject()
                                .put("type", "string")
                                .put("description", "3-8 个词的任务标题"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "分派给 Agent 的详细任务、范围、限制和验收方式。必须写明不能修改未授权文件；如需修改范围外文件必须停止并汇报"))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "允许读取的文件或目录路径列表。为空时仍应只读取完成任务所需的最小范围"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "sub-coding 允许写入的唯一文件或目录路径列表；explore 必须留空。不要把同一文件分配给多个 Agent")))
                .put("required", new JSONArray().put("type").put("description").put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String type = normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        if (!TYPE_EXPLORE.equals(type) && !TYPE_SUB_CODING.equals(type)) {
            return error("Agent 类型只能是 explore 或 sub-coding。");
        }
        if (TYPE_EXPLORE.equals(type) && hasScope(input.optJSONArray("write_scope"))) {
            return error("explore Agent 不能声明 write_scope，也不能写入文件。");
        }
        if (description.length() == 0) {
            return error("Agent description 不能为空。");
        }
        if (prompt.length() == 0) {
            return error("Agent prompt 不能为空。");
        }
        if (context == null || context.getAgentRunner() == null) {
            return error("Agent 执行器未接入，无法运行子 Agent。");
        }
        try {
            JSONObject normalized = new JSONObject(input.toString())
                    .put("type", type)
                    .put("description", description)
                    .put("prompt", prompt);
            return context.getAgentRunner().runAgent(normalized, context);
        } catch (Exception e) {
            return error("Agent 参数解析失败: " + e.getMessage());
        }
    }

    public static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(type) || "subcoding".equals(type) || "coding".equals(type)) {
            return TYPE_SUB_CODING;
        }
        return type;
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private boolean hasScope(JSONArray array) {
        if (array == null) {
            return false;
        }
        for (int i = 0; i < array.length(); i++) {
            if (array.optString(i).trim().length() > 0) {
                return true;
            }
        }
        return false;
    }
}
