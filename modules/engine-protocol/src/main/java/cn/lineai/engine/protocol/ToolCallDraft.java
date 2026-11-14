package cn.lineai.engine.protocol;

/**
 * Partially or fully assembled tool call.
 *
 * <p>Providers stream one call as several fragments; a draft therefore represents the state at one
 * point in the stream, and any field may still be empty until the stream ends.</p>
 */
public final class ToolCallDraft {

    /** Zero-based position of this call within the response; stable across fragments. */
    public final int index;
    /** Provider-issued call identifier; empty until the provider sends it. */
    public final String id;
    /** Tool name; empty until known, which marks the draft incomplete. */
    public final String name;
    /** JSON arguments accumulated so far; valid JSON only once the stream ends. */
    public final String arguments;

    /**
     * Creates a draft; {@code null} id, name or arguments are normalised to empty strings.
     *
     * @param index zero-based position within the response
     * @param id provider call identifier, may be null
     * @param name tool name, may be null while the call is still streaming
     * @param arguments accumulated JSON arguments, may be null
     */
    public ToolCallDraft(int index, String id, String name, String arguments) {
        this.index = index;
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
    }

    /** Returns {@code true} once the tool name is known. */
    public boolean complete() {
        return !name.isEmpty();
    }
}
