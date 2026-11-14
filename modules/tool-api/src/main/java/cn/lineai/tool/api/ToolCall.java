package cn.lineai.tool.api;

import java.util.Objects;

/**
 * A model-requested tool invocation.
 *
 * <p>The raw {@code arguments} text is kept unparsed so the repair pipeline
 * ({@link ToolJsonRepair}) can run once, exactly where the call is executed.</p>
 */
public final class ToolCall {

    /** Identifier the model uses to match a result to its request; empty when unknown. */
    public final String id;

    /** Requested tool name; empty marks an empty or unparseable call. */
    public final String name;

    /** Raw argument JSON as emitted by the model; may be malformed or empty. */
    public final String arguments;

    /**
     * Creates a call; {@code null} fields normalize to empty strings.
     *
     * @param id model-supplied call identifier
     * @param name requested tool name
     * @param arguments raw argument JSON text
     */
    public ToolCall(String id, String name, String arguments) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
    }

    /**
     * Whether this call carries no tool name and must be skipped.
     *
     * @return {@code true} when the call has no tool name
     */
    public boolean isEmpty() {
        return name.isEmpty();
    }

    /**
     * Compares calls by id, name and arguments.
     *
     * @param other object to compare against
     * @return {@code true} when all three fields are equal
     */
    @Override public boolean equals(Object other) {
        if (!(other instanceof ToolCall)) return false;
        ToolCall that = (ToolCall) other;
        return id.equals(that.id) && name.equals(that.name) && arguments.equals(that.arguments);
    }

    /**
     * Hash code consistent with {@link #equals(Object)}.
     *
     * @return hash over id, name and arguments
     */
    @Override public int hashCode() {
        return Objects.hash(id, name, arguments);
    }
}
