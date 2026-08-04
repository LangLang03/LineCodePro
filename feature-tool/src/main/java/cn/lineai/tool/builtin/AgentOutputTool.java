package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.StoredAgentResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolNames;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AgentOutputTool extends BaseTool {
    public static final String NAME = ToolNames.AGENT_OUTPUT;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Fetch a previously started agent result by agent_id. "
                + "The agent / agent_pipeline tools return a compact ref with agent_id; "
                + "call this tool when you need the full output (or while async agents are still running). "
                + "Do not invent agent_id values — only use ids returned by agent tools.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public boolean isAllowedInReadonlyMode() {
        return true;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public int getActionIcon() {
        return ICON_BOT;
    }

    @Override
    public String getActionName(android.content.Context context) {
        if (context == null) {
            return "Agent output";
        }
        return context.getString(R.string.tool_agent_output_action);
    }

    @Override
    public String getDisplayLabel(android.content.Context context, JSONObject input, String workspacePath) {
        String agentId = input == null ? "" : input.optString("agent_id", "").trim();
        if (agentId.length() == 0) {
            return NAME;
        }
        return agentId;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("agent_id", new JSONObject()
                                .put("type", "string")
                                .put("description", "agent_id from a prior agent / agent_pipeline tool result"))
                        .put("include", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put("output").put("meta"))
                                .put("description", "output (default): full body when done; meta: status fields only")))
                .put("required", new JSONArray().put("agent_id"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String agentId = input == null ? "" : input.optString("agent_id", "").trim();
        if (agentId.length() == 0) {
            return error(string(context, R.string.tool_agent_output_id_missing, "agent_id is required."));
        }
        ToolContext.AgentResultStore store = context == null ? null : context.getAgentResultStore();
        if (store == null) {
            return error(string(context, R.string.tool_agent_output_store_missing, "Agent result store is not available."));
        }
        StoredAgentResult result = store.get(agentId);
        if (result == null) {
            return error(string(context, R.string.tool_agent_output_not_found, "Unknown agent_id: %1$s", agentId));
        }
        String include = input.optString("include", "output").trim();
        if ("meta".equals(include)) {
            return ok(metaJson(result));
        }
        if (result.isRunning()) {
            return ok(runningJson(result, string(context, R.string.tool_agent_output_still_running,
                    "Agent is still running. Poll again later or wait for completion.")));
        }
        String body = result.getFullOutput();
        if (body == null || body.trim().length() == 0) {
            body = result.getPreview();
        }
        if (body == null) {
            body = "";
        }
        if (body.length() > ToolResult.MAX_TOOL_RESULT_CHARS) {
            body = ToolResult.truncateContent(body);
        }
        if (result.isError()) {
            return error(body.length() > 0 ? body : string(context, R.string.tool_agent_output_failed, "Agent finished with error."));
        }
        return ok(body.length() > 0 ? body : string(context, R.string.tool_agent_output_empty, "Agent finished with empty output."));
    }

    private static String metaJson(StoredAgentResult result) {
        try {
            return new JSONObject()
                    .put("agent_id", result.getAgentId())
                    .put("status", result.getStatus())
                    .put("type", result.getType())
                    .put("description", result.getDescription())
                    .put("preview", result.getPreview())
                    .put("error", result.isError())
                    .put("async", result.isAsync())
                    .put("tool_call_count", result.getToolCallCount())
                    .toString();
        } catch (Exception e) {
            return "{\"agent_id\":\"" + result.getAgentId() + "\",\"status\":\"" + result.getStatus() + "\"}";
        }
    }

    private static String runningJson(StoredAgentResult result, String message) {
        try {
            return new JSONObject()
                    .put("agent_id", result.getAgentId())
                    .put("status", result.getStatus())
                    .put("type", result.getType())
                    .put("description", result.getDescription())
                    .put("preview", result.getPreview())
                    .put("async", result.isAsync())
                    .put("message", message)
                    .toString();
        } catch (Exception e) {
            return message;
        }
    }

    private static String string(ToolContext context, int resId, String fallback, Object... args) {
        if (context == null) {
            return formatFallback(fallback, args);
        }
        try {
            if (args == null || args.length == 0) {
                String value = context.getString(resId);
                return value == null || value.length() == 0 ? fallback : value;
            }
            String value = context.getString(resId, args);
            return value == null || value.length() == 0 ? formatFallback(fallback, args) : value;
        } catch (Exception ignored) {
            return formatFallback(fallback, args);
        }
    }

    private static String formatFallback(String fallback, Object... args) {
        if (args == null || args.length == 0) {
            return fallback;
        }
        try {
            return String.format(fallback, args);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
