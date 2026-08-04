package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentPipelineTool extends BaseTool {
    public static final String NAME = "agent_pipeline";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isAllowedInReadonlyMode() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Create a pipeline of Agent tasks with dependencies. sub-coding must declare a unique write_scope; multiple Agents cannot write the same file or overlapping directories.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.AGENT_PIPELINE;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        JSONObject agentSchema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("id", new JSONObject()
                                .put("type", "string")
                                .put("description", "Unique identifier within the pipeline"))
                        .put("type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put(AgentTool.TYPE_EXPLORE).put(AgentTool.TYPE_SUB_CODING))
                                .put("description", "Agent type"))
                        .put("description", new JSONObject()
                                .put("type", "string")
                                .put("description", "Short task title"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "Detailed task description. Must state the task boundaries, acceptance criteria, and that files outside write_scope must not be modified"))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "List of files or directories allowed to read. When empty, still read only the minimum scope needed to complete the task"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "Unique list of files or directories sub-coding is allowed to write; explore must be empty. Multiple Agents' write_scope must not be identical, nor contain or be contained by one another"))
                        .put("depends_on", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "List of dependent Agent IDs")))
                .put("required", new JSONArray().put("id").put("type").put("description").put("prompt"));

        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("agents", new JSONObject()
                                .put("type", "array")
                                .put("items", agentSchema)
                                .put("description", "List of Agent tasks")))
                .put("required", new JSONArray().put("agents"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        JSONArray agents = input.optJSONArray("agents");
        if (agents == null || agents.length() == 0) {
            return error(context.getString(R.string.tool_pipeline_agents_empty));
        }
        HashSet<String> ids = new HashSet<>();
        ArrayList<WriteScopeOwner> writeScopes = new ArrayList<>();
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            if (agent == null) {
                return error(context.getString(R.string.tool_pipeline_agent_not_object, i));
            }
            if (agent.optString("id").trim().length() == 0) {
                return error(context.getString(R.string.tool_pipeline_agent_id_empty, i));
            }
            String id = agent.optString("id").trim();
            if (ids.contains(id)) {
                return error(context.getString(R.string.tool_pipeline_agent_id_duplicate, id));
            }
            ids.add(id);
            JSONArray dependencies = agent.optJSONArray("depends_on");
            if (dependencies != null) {
                for (int j = 0; j < dependencies.length(); j++) {
                    if (id.equals(dependencies.optString(j).trim())) {
                        return error(context.getString(R.string.tool_pipeline_agent_self_depend, id));
                    }
                }
            }
            String type = AgentTool.normalizeType(agent.optString("type"));
            if (!AgentTool.TYPE_EXPLORE.equals(type) && !AgentTool.TYPE_SUB_CODING.equals(type)) {
                return error(context.getString(R.string.tool_pipeline_agent_invalid_type, i));
            }
            ArrayList<String> writeScope = scopeList(agent.optJSONArray("write_scope"));
            if (AgentTool.TYPE_EXPLORE.equals(type) && !writeScope.isEmpty()) {
                return error(context.getString(R.string.tool_pipeline_explore_no_write, id));
            }
            if (AgentTool.TYPE_SUB_CODING.equals(type) && writeScope.isEmpty()) {
                return error(context.getString(R.string.tool_pipeline_coding_needs_write, id));
            }
            HashSet<String> localScopes = new HashSet<>();
            for (String scope : writeScope) {
                String normalizedScope = normalizeScope(scope);
                if (normalizedScope.length() == 0) {
                    continue;
                }
                if (localScopes.contains(normalizedScope)) {
                    return error(context.getString(R.string.tool_pipeline_scope_duplicate, id, scope));
                }
                localScopes.add(normalizedScope);
                for (WriteScopeOwner owner : writeScopes) {
                    if (scopesOverlap(normalizedScope, owner.scope)) {
                        return error(context.getString(R.string.tool_pipeline_scope_overlap,
                                owner.agentId, owner.originalScope, id, scope));
                    }
                }
                writeScopes.add(new WriteScopeOwner(id, normalizedScope, scope));
            }
            if (agent.optString("description").trim().length() == 0) {
                return error(context.getString(R.string.tool_pipeline_agent_description_empty, i));
            }
            if (agent.optString("prompt").trim().length() == 0) {
                return error(context.getString(R.string.tool_pipeline_agent_prompt_empty, i));
            }
        }
        if (context == null || context.getAgentRunner() == null) {
            return error(context.getString(R.string.tool_pipeline_runner_not_available));
        }
        return context.getAgentRunner().runAgentPipeline(input, context);
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

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallAgentPipelineView.class;
    }
}
