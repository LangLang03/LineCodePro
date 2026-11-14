package cn.lineai.feature.chat.toolcall;

/**
 * Immutable view data for one tool call: the model's request plus its persisted result.
 *
 * <p>The chat timeline pairs an assistant message's {@code ToolCallRecord} with the following
 * {@code Role.TOOL} message by call id and hands the pair to {@link ToolCallGroupView}. Keeping
 * this a plain value object lets the timeline stay independent of the card renderers.</p>
 */
public final class ToolCallEntry {

    /** Call id used to pair the request with its result. */
    public final String callId;
    /** Registry tool name; UI never branches on it. */
    public final String name;
    /** Raw JSON arguments of the request. */
    public final String arguments;
    /** Plain-text result content, empty while running. */
    public final String result;
    /** True when the tool reported an error. */
    public final boolean failed;
    /** True while the call has no result yet. */
    public final boolean running;
    /** Persisted file-diff id, or an empty string when the call produced no reviewable diff. */
    public final String diffId;
    /** Persisted review state: {@code pending}, {@code accepted}, {@code rejected} or empty. */
    public final String reviewState;

    /** Convenience constructor for calls without diff or review state. */
    public ToolCallEntry(String callId, String name, String arguments, String result,
                         boolean failed, boolean running) {
        this(callId, name, arguments, result, failed, running, "", "");
    }

    /** Full constructor; null inputs normalize to empty values. */
    public ToolCallEntry(String callId, String name, String arguments, String result,
                         boolean failed, boolean running, String diffId, String reviewState) {
        this.callId = callId == null ? "" : callId;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
        this.result = result == null ? "" : result;
        this.failed = failed;
        this.running = running;
        this.diffId = diffId == null ? "" : diffId;
        this.reviewState = reviewState == null ? "" : reviewState;
    }

    /** True when the call already produced a terminal result (either success or failure). */
    public boolean finished() {
        return !running && (!result.isEmpty() || failed);
    }
}
