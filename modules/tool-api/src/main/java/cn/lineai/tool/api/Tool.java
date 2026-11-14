package cn.lineai.tool.api;

import org.json.JSONObject;

/** One callable tool. */
public interface Tool {

    /**
     * Unique registration name the model addresses the tool by, e.g. {@code read_file}.
     *
     * @return the tool name; never empty for a registry-accepted tool
     */
    String name();

    /**
     * Natural-language capability description shown to the model.
     *
     * @return the description used in the model-facing tool catalog
     */
    String description();

    /** JSON-Schema object for the arguments.
     *
     * @return a serialized JSON-Schema object describing the accepted arguments
     */
    String parametersJson();

    /**
     * Whether executing this tool changes files, device state or external systems.
     *
     * @return {@code true} when the tool is suppressed in read-only mutation modes
     */
    boolean mutating();

    /**
     * Runs one invocation of this tool.
     *
     * @param context execution environment supplying workspace, sinks and policy switches
     * @param arguments repaired argument object, never {@code null}
     * @return the result handed back to the model; failures are reported as error results,
     *         never by throwing
     */
    ToolResult execute(ToolContext context, JSONObject arguments);

    /**
     * Semantic family for mode filtering; tools never expose names to callers.
     *
     * @return the tool kind; defaults to {@link ToolKind#GENERAL}
     */
    default ToolKind kind() {
        return ToolKind.GENERAL;
    }

    /**
     * True when the tool can only work on the on-device filesystem.
     *
     * <p>An SSH workspace runs commands on a remote host; these tools are excluded from the
     * catalog there instead of silently operating on the phone's storage.</p>
     *
     * @return {@code true} when the tool must be hidden from remote workspaces
     */
    default boolean needsLocalWorkspace() {
        return false;
    }

    /**
     * Presentation metadata for one call; the UI never hard-codes tool names.
     *
     * @param arguments repaired call arguments used to derive the displayed action
     * @return the card metadata; the default derives a generic card from the tool name
     */
    default ToolPresentation presentation(JSONObject arguments) {
        return ToolPresentation.generic(name(), arguments);
    }
}
