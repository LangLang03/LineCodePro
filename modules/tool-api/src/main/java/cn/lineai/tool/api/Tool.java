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

    /** Presentation metadata for one call; the UI never hard-codes tool names. */
    default ToolPresentation presentation(JSONObject arguments) {
        return ToolPresentation.generic(name(), arguments);
    }
}
