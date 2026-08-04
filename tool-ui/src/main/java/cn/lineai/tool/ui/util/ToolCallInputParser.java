package cn.lineai.tool.ui;
import cn.lineai.model.tool.ToolCall;

import android.content.Context;
import cn.lineai.tool.ui.R;
import org.json.JSONObject;

/**
 * 工具调用输入解析工具：从 ToolCall 提取 JSON 输入，并生成用于卡片展示的标签文本。
 */
final class ToolCallInputParser {
    private ToolCallInputParser() {
    }

    static JSONObject parseInput(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments().trim().length() == 0) {
            return new JSONObject();
        }
        try {
            return new JSONObject(toolCall.getArguments());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    static String inputLabel(String name, JSONObject input) {
        if (input == null) {
            return name;
        }
        String[] keys = new String[] {"file_path", "pattern", "query", "url", "path", "command"};
        for (String key : keys) {
            String value = input.optString(key);
            if (value.length() > 0) {
                return value;
            }
        }
        return name == null ? "" : name;
    }

    static String displayInputLabel(Context context, String name, JSONObject input, String workspacePath) {
        if (input == null) {
            return name == null ? "" : name;
        }
        // 优先使用工具自提供的标签
        cn.lineai.tool.ui.ToolInfoResolver resolver = cn.lineai.tool.ui.ToolInfoResolverProvider.getDefault();
        if (resolver != null) {
            String label = resolver.getDisplayLabel(context, name, input, workspacePath);
            if (label != null) {
                return label;
            }
        }
        // fallback 到通用逻辑
        return inputLabel(name, input);
    }

    static String phoneControlActionName(Context context, String name) {
        cn.lineai.tool.ui.ToolInfoResolver resolver = cn.lineai.tool.ui.ToolInfoResolverProvider.getDefault();
        if (resolver != null) {
            String actionName = resolver.getActionName(context, name);
            if (actionName != null) {
                return actionName;
            }
        }
        return context == null ? (name == null ? "" : name) : context.getString(R.string.tool_call_phone_action_default);
    }
}
