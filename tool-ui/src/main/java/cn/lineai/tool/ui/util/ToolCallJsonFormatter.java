package cn.lineai.tool.ui;

import org.json.JSONObject;

/**
 * 工具调用 JSON 格式化工具：将 JSONObject 转换为带缩进的可读字符串，用于卡片输入展示。
 */
final class ToolCallJsonFormatter {
    private ToolCallJsonFormatter() {
    }

    static String prettyJson(JSONObject input) {
        if (input == null) {
            return "{}";
        }
        try {
            return input.toString(2);
        } catch (Exception ignored) {
            return input.toString();
        }
    }
}
