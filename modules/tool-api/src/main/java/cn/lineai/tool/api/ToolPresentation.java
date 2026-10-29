package cn.lineai.tool.api;

import org.json.JSONObject;

/**
 * How one tool call is presented to the user.
 *
 * <p>Tools supply this metadata themselves, so adding a tool never means editing the chat UI.
 * {@link #generic} keeps unknown or removed tools renderable.</p>
 */
public final class ToolPresentation {

    public final String title;
    public final ToolIcon icon;
    public final String action;
    public final String reason;

    public ToolPresentation(String title, ToolIcon icon, String action, String reason) {
        this.title = title == null ? "" : title;
        this.icon = icon == null ? ToolIcon.GENERIC : icon;
        this.action = action == null ? "" : action;
        this.reason = reason == null ? "" : reason;
    }

    public static ToolPresentation generic(String name) {
        return generic(name, (JSONObject) null);
    }

    public static ToolPresentation generic(String name, String argumentsJson) {
        JSONObject arguments;
        try {
            arguments = argumentsJson == null || argumentsJson.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(argumentsJson);
        } catch (Exception ignored) {
            arguments = new JSONObject();
        }
        return generic(name, arguments);
    }

    public static ToolPresentation generic(String name, JSONObject arguments) {
        String title = name == null || name.isEmpty() ? "Tool" : name;
        String action = arguments == null ? "" : first(arguments,
                "file_path", "path", "pattern", "command", "query", "url");
        return new ToolPresentation(title, ToolIcon.GENERIC, action, reason(arguments));
    }

    public static String reason(JSONObject arguments) {
        if (arguments == null) return "";
        return arguments.optString("reason", arguments.optString("description", "")).trim();
    }

    public static String first(JSONObject arguments, String... keys) {
        for (String key : keys) {
            String value = arguments.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
