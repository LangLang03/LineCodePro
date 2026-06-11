package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentPipelineTool extends BaseTool {
    @Override
    public String getName() {
        return "agent_pipeline";
    }

    @Override
    public String getDescription() {
        return "创建多个有依赖关系的 Agent 任务流水线。sub-coding 必须声明唯一 write_scope，多个 Agent 不能写同一文件或重叠目录。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        JSONObject agentSchema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("id", new JSONObject()
                                .put("type", "string")
                                .put("description", "流水线内唯一标识"))
                        .put("type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put(AgentTool.TYPE_EXPLORE).put(AgentTool.TYPE_SUB_CODING))
                                .put("description", "Agent 类型"))
                        .put("description", new JSONObject()
                                .put("type", "string")
                                .put("description", "简短任务标题"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "详细任务描述。必须写明任务边界、验收方式，以及不能修改 write_scope 外的文件"))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "允许读取的文件或目录路径列表。为空时仍应只读取完成任务所需的最小范围"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "sub-coding 允许写入的唯一文件或目录路径列表；explore 必须留空。多个 Agent 的 write_scope 不能相同、包含或被包含"))
                        .put("depends_on", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "依赖的 Agent ID 列表")))
                .put("required", new JSONArray().put("id").put("type").put("description").put("prompt"));

        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("agents", new JSONObject()
                                .put("type", "array")
                                .put("items", agentSchema)
                                .put("description", "Agent 任务列表")))
                .put("required", new JSONArray().put("agents"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        JSONArray agents = input.optJSONArray("agents");
        if (agents == null || agents.length() == 0) {
            return error("agent_pipeline.agents 不能为空。");
        }
        HashSet<String> ids = new HashSet<>();
        ArrayList<WriteScopeOwner> writeScopes = new ArrayList<>();
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            if (agent == null) {
                return error("agents[" + i + "] 必须是对象。");
            }
            if (agent.optString("id").trim().length() == 0) {
                return error("agents[" + i + "].id 不能为空。");
            }
            String id = agent.optString("id").trim();
            if (ids.contains(id)) {
                return error("Agent id 重复: " + id);
            }
            ids.add(id);
            JSONArray dependencies = agent.optJSONArray("depends_on");
            if (dependencies != null) {
                for (int j = 0; j < dependencies.length(); j++) {
                    if (id.equals(dependencies.optString(j).trim())) {
                        return error("Agent 不能依赖自身: " + id);
                    }
                }
            }
            String type = AgentTool.normalizeType(agent.optString("type"));
            if (!AgentTool.TYPE_EXPLORE.equals(type) && !AgentTool.TYPE_SUB_CODING.equals(type)) {
                return error("agents[" + i + "].type 只能是 explore 或 sub-coding。");
            }
            ArrayList<String> writeScope = scopeList(agent.optJSONArray("write_scope"));
            if (AgentTool.TYPE_EXPLORE.equals(type) && !writeScope.isEmpty()) {
                return error("explore Agent 不能声明 write_scope，也不能写入文件: " + id);
            }
            if (AgentTool.TYPE_SUB_CODING.equals(type) && writeScope.isEmpty()) {
                return error("sub-coding Agent 必须声明 write_scope，避免多个 Agent 操纵同一文件: " + id);
            }
            HashSet<String> localScopes = new HashSet<>();
            for (String scope : writeScope) {
                String normalizedScope = normalizeScope(scope);
                if (normalizedScope.length() == 0) {
                    continue;
                }
                if (localScopes.contains(normalizedScope)) {
                    return error("Agent " + id + " 的 write_scope 重复: " + scope);
                }
                localScopes.add(normalizedScope);
                for (WriteScopeOwner owner : writeScopes) {
                    if (scopesOverlap(normalizedScope, owner.scope)) {
                        return error("多个 Agent 不能写同一文件或重叠目录: "
                                + owner.agentId + " 的 " + owner.originalScope
                                + " 与 " + id + " 的 " + scope
                                + " 冲突。请合并为一个 Agent，或拆成不同文件/目录后再分配。");
                    }
                }
                writeScopes.add(new WriteScopeOwner(id, normalizedScope, scope));
            }
            if (agent.optString("description").trim().length() == 0) {
                return error("agents[" + i + "].description 不能为空。");
            }
            if (agent.optString("prompt").trim().length() == 0) {
                return error("agents[" + i + "].prompt 不能为空。");
            }
        }
        if (context == null || context.getAgentRunner() == null) {
            return error("Agent 流水线执行器未接入，无法运行。");
        }
        return context.getAgentRunner().runAgentPipeline(input, context);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private ArrayList<String> scopeList(JSONArray array) {
        ArrayList<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    private String normalizeScope(String value) {
        String text = value == null ? "" : value.trim().replace('\\', '/');
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        while (text.contains("//")) {
            text = text.replace("//", "/");
        }
        while (text.endsWith("/") && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private boolean scopesOverlap(String left, String right) {
        if (left.length() == 0 || right.length() == 0) {
            return false;
        }
        if (".".equals(left) || ".".equals(right) || "/".equals(left) || "/".equals(right)) {
            return true;
        }
        return left.equals(right)
                || left.startsWith(right + "/")
                || right.startsWith(left + "/");
    }

    private static final class WriteScopeOwner {
        private final String agentId;
        private final String scope;
        private final String originalScope;

        WriteScopeOwner(String agentId, String scope, String originalScope) {
            this.agentId = agentId == null ? "" : agentId;
            this.scope = scope == null ? "" : scope;
            this.originalScope = originalScope == null ? "" : originalScope;
        }
    }
}
