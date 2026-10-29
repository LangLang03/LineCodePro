package cn.lineai.tool.api;

import org.json.JSONObject;

/** One callable tool. */
public interface Tool {

    String name();

    String description();

    /** JSON-Schema object for the arguments. */
    String parametersJson();

    boolean mutating();

    ToolResult execute(ToolContext context, JSONObject arguments);

    /** Semantic family for mode filtering; tools never expose names to callers. */
    default ToolKind kind() {
        return ToolKind.GENERAL;
    }

    /**
     * True when the tool can only work on the on-device filesystem.
     *
     * <p>An SSH workspace runs commands on a remote host; these tools are excluded from the
     * catalog there instead of silently operating on the phone's storage.</p>
     */
    default boolean needsLocalWorkspace() {
        return false;
    }

    /** Presentation metadata for one call; the UI never hard-codes tool names. */
    default ToolPresentation presentation(JSONObject arguments) {
        return ToolPresentation.generic(name(), arguments);
    }
}
