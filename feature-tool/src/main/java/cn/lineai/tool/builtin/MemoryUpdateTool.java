package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MemoryUpdateTool extends BaseTool {
    public static final String NAME = "memory_update";
    private static final int MAX_CONTENT_CHARS = 320;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Save a durable long-term memory for future sessions. "
                + "Call only when the user states a lasting preference, project constraint, or environment fact "
                + "that should persist across chats. Do not save one-off tasks, temporary progress, logs, secrets, "
                + "or ordinary conversation content. "
                + "scope: user (cross-project preference), project (this workspace only), environment (device/build setup).";
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
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_action_memory);
    }

    @Override
    public int getActionIcon() {
        return ICON_BOOK_OPEN;
    }

    @Override
    public String getDisplayLabel(Context context, JSONObject input, String workspacePath) {
        return context.getString(R.string.tool_memory_updated_label);
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("content", new JSONObject()
                                .put("type", "string")
                                .put("description", "Independent durable memory statement, max " + MAX_CONTENT_CHARS + " chars"))
                        .put("scope", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray()
                                        .put(MemoryOverviewState.Memory.SCOPE_USER)
                                        .put(MemoryOverviewState.Memory.SCOPE_PROJECT)
                                        .put(MemoryOverviewState.Memory.SCOPE_ENVIRONMENT))
                                .put("description", "user | project | environment; default user")))
                .put("required", new JSONArray().put("content"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (input == null) {
            return error(context.getString(R.string.tool_memory_params_empty));
        }
        String content = input.optString("content", "").trim();
        if (content.length() == 0) {
            return error(context.getString(R.string.tool_memory_content_empty));
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS - 1).trim() + "。";
        }
        if (looksSensitive(content)) {
            return error(context.getString(R.string.tool_memory_sensitive_rejected));
        }
        String scope = normalizeScope(input.optString("scope"));
        LearningContextStore store = context == null ? null : context.getLearningContextStore();
        if (store == null) {
            return error(context.getString(R.string.tool_memory_store_not_init));
        }
        String projectId = context.getHomePath();
        store.saveMemory("", scope, projectId, content);
        return ok(context.getString(R.string.tool_memory_updated));
    }

    private static String normalizeScope(String scope) {
        String value = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if (MemoryOverviewState.Memory.SCOPE_PROJECT.equals(value)) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        if (MemoryOverviewState.Memory.SCOPE_ENVIRONMENT.equals(value)) {
            return MemoryOverviewState.Memory.SCOPE_ENVIRONMENT;
        }
        return MemoryOverviewState.Memory.SCOPE_USER;
    }

    private static boolean looksSensitive(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("api key")
                || lower.contains("apikey")
                || lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("secret")
                || lower.contains("cookie")
                || lower.contains("token")
                || lower.contains("私钥")
                || lower.contains("密码")
                || lower.contains("密钥")
                || lower.contains("sk-");
    }
}
