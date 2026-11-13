package cn.lineai.tool.api;

import org.json.JSONObject;

/**
 * How one tool call is presented to the user.
 *
 * <p>Tools supply this metadata themselves, so adding a tool never means editing the chat UI.
 * {@link #generic} keeps unknown or removed tools renderable.</p>
 */
public final class ToolPresentation {

    /** Short card title shown to the user; never {@code null}. */
    public final String title;

    /** Semantic icon category; never {@code null}. */
    public final ToolIcon icon;

    /** Primary action string (path, command, query) shown under the title; never {@code null}. */
    public final String action;

    /** Optional model-supplied rationale echoed on the card; never {@code null}. */
    public final String reason;

    /**
     * Creates card metadata; {@code null} fields normalize to safe defaults.
     *
     * @param title card title
     * @param icon semantic icon category
     * @param action primary action string, or the empty string when none
     * @param reason optional rationale, or the empty string when none
     */
    public ToolPresentation(String title, ToolIcon icon, String action, String reason) {
        this.title = title == null ? "" : title;
        this.icon = icon == null ? ToolIcon.GENERIC : icon;
        this.action = action == null ? "" : action;
        this.reason = reason == null ? "" : reason;
    }

    /**
     * Builds a generic card named after the tool.
     *
     * @param name tool name used as the card title
     * @return a generic presentation without action text
     */
    public static ToolPresentation generic(String name) {
        return generic(name, (JSONObject) null);
    }

    /**
     * Builds a generic card, extracting action text from raw argument JSON.
     *
     * @param name tool name used as the card title
     * @param argumentsJson raw argument JSON; malformed input is tolerated
     * @return a generic presentation with the first recognizable action value
     */
    public static ToolPresentation generic(String name, String argumentsJson) {
        return generic(name, ToolJson.object(argumentsJson));
    }

    /**
     * Builds a generic card, deriving action text from known argument keys.
     *
     * @param name tool name used as the card title
     * @param arguments parsed arguments; may be {@code null}
     * @return a generic presentation
     */
    public static ToolPresentation generic(String name, JSONObject arguments) {
        String title = name == null || name.isEmpty() ? "Tool" : name;
        String action = arguments == null ? "" : first(arguments,
                "file_path", "path", "pattern", "command", "query", "url");
        return new ToolPresentation(title, ToolIcon.GENERIC, action, reason(arguments));
    }

    /**
     * Extracts the model-supplied rationale from the conventional keys.
     *
     * @param arguments parsed arguments; may be {@code null}
     * @return the trimmed {@code reason} or {@code description} value, or the empty string
     */
    public static String reason(JSONObject arguments) {
        if (arguments == null) return "";
        return arguments.optString("reason", arguments.optString("description", "")).trim();
    }

    /**
     * Returns the first non-empty string among the given keys.
     *
     * @param arguments parsed arguments; never {@code null}
     * @param keys candidate keys, tried in order
     * @return the first non-empty value, or the empty string when all are absent
     */
    public static String first(JSONObject arguments, String... keys) {
        for (String key : keys) {
            String value = arguments.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
