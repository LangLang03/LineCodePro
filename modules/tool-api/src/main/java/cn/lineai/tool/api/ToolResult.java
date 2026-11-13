package cn.lineai.tool.api;

/** Result of one tool invocation. */
public final class ToolResult {

    /** Identifier echoed from the originating {@link ToolCall}; empty when unknown. */
    public final String callId;

    /** Text handed back to the model as the tool output; never {@code null}. */
    public final String content;

    /** True when the invocation failed and {@link #content} carries the failure message. */
    public final boolean error;

    /** True when {@link #content} was cut short by an output limit. */
    public final boolean truncated;

    /** Path of a locally stored image attached to this result; empty when none. */
    public final String imagePath;

    /**
     * Creates a result; use the static factories so the outcome flags stay consistent.
     *
     * @param callId identifier of the originating call
     * @param content tool output or failure message
     * @param error whether the invocation failed
     * @param truncated whether the content was cut by an output limit
     * @param imagePath optional image path; {@code null} normalizes to the empty string
     */
    private ToolResult(String callId, String content, boolean error, boolean truncated,
                       String imagePath) {
        this.callId = callId == null ? "" : callId;
        this.content = content == null ? "" : content;
        this.error = error;
        this.truncated = truncated;
        this.imagePath = imagePath == null ? "" : imagePath;
    }

    /**
     * Builds a successful text result.
     *
     * @param callId identifier of the originating call
     * @param content tool output handed to the model
     * @return a non-error, non-truncated result
     */
    public static ToolResult success(String callId, String content) {
        return new ToolResult(callId, content, false, false, "");
    }

    /**
     * Builds a failure result whose content is the failure message.
     *
     * @param callId identifier of the originating call
     * @param message human-readable failure description for the model
     * @return an error result
     */
    public static ToolResult failure(String callId, String message) {
        return new ToolResult(callId, message, true, false, "");
    }

    /**
     * Builds a successful result that also references a stored image.
     *
     * @param callId identifier of the originating call
     * @param content text handed to the model alongside the image
     * @param imagePath local path of the stored image
     * @return a non-error result carrying an image reference
     */
    public static ToolResult successWithImage(String callId, String content, String imagePath) {
        return new ToolResult(callId, content, false, false, imagePath);
    }

    /**
     * Builds a successful result whose content was cut by an output limit.
     *
     * @param callId identifier of the originating call
     * @param content the truncated output actually handed to the model
     * @return a truncated result
     */
    public static ToolResult truncated(String callId, String content) {
        return new ToolResult(callId, content, false, true, "");
    }
}
