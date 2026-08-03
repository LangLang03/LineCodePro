package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentTool extends BaseTool {
    public static final String NAME = "agent";
    public static final String TYPE_EXPLORE = "explore";
    public static final String TYPE_SUB_CODING = "sub-coding";

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
        return "Dispatch a sub-Agent to handle a task. explore is read-only; sub-coding must have a clear and unique write scope. "
                + "Returns a compact ref with agent_id (not full transcript). Use agent_output(agent_id) to fetch full output when needed. "
                + "Optional async=true returns immediately for explore agents.";
    }

    @Override
    public int getActionIcon() {
        return ICON_BOT;
    }

    @Override
    public String getActionName(android.content.Context context) {
        if (context == null) {
            return "Agent";
        }
        return context.getString(R.string.tool_agent_action);
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
                        .put("type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put(TYPE_EXPLORE).put(TYPE_SUB_CODING))
                                .put("description", "Agent type: explore for read-only exploration, sub-coding for programming subtasks"))
                        .put("description", new JSONObject()
                                .put("type", "string")
                                .put("description", "Task title of 3-8 words"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "Detailed task assigned to the Agent, including scope, constraints, and acceptance criteria. Must state that unauthorized files must not be modified; if out-of-scope files must be modified, stop and report."))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "List of files or directories allowed to read. When empty, still read only the minimum scope needed to complete the task"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "Unique list of files or directories sub-coding is allowed to write; explore must be empty. Do not assign the same file to multiple Agents"))
                        .put("async", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "If true, return immediately with agent_id (explore only; default false). Fetch full output later via agent_output.")))
                .put("required", new JSONArray().put("type").put("description").put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String type = normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        if (!TYPE_EXPLORE.equals(type) && !TYPE_SUB_CODING.equals(type)) {
            return error(context.getString(R.string.tool_agent_invalid_type));
        }
        if (TYPE_EXPLORE.equals(type) && hasScope(input.optJSONArray("write_scope"))) {
            return error(context.getString(R.string.tool_agent_explore_no_write));
        }
        if (description.length() == 0) {
            return error(context.getString(R.string.tool_agent_description_empty));
        }
        if (prompt.length() == 0) {
            return error(context.getString(R.string.tool_agent_prompt_empty));
        }
        if (context == null || context.getAgentRunner() == null) {
            return error(context.getString(R.string.tool_agent_runner_not_available));
        }
        try {
            JSONObject normalized = new JSONObject(input.toString())
                    .put("type", type)
                    .put("description", description)
                    .put("prompt", prompt);
            return context.getAgentRunner().runAgent(normalized, context);
        } catch (Exception e) {
            return error(context.getString(R.string.tool_agent_parse_failed, e.getMessage()));
        }
    }

    public static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(type) || "subcoding".equals(type) || "coding".equals(type)) {
            return TYPE_SUB_CODING;
        }
        return type;
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

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallAgentView.class;
    }
}
