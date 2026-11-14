package cn.lineai.core.model;

import java.util.Objects;

/** One renderable slice of a message: prose, reasoning, an attachment reference or a tool link. */
public final class ContentBlock {

    /** Kind of content carried by a block. */
    public enum Type {
        TEXT,
        REASONING,
        IMAGE,
        FILE,
        TOOL_CALL,
        TOOL_RESULT,
        DIFF,
        COMPACT,
        ERROR
    }

    public final Type type;
    public final String text;
    public final String refId;
    public final String status;

    /** Creates a block, normalising null strings to empty. */
    private ContentBlock(Type type, String text, String refId, String status) {
        this.type = type;
        this.text = text == null ? "" : text;
        this.refId = refId == null ? "" : refId;
        this.status = status == null ? "" : status;
    }

    /**
     * Creates a prose block.
     * @param text message text
     * @return a TEXT block
     */
    public static ContentBlock text(String text) {
        return new ContentBlock(Type.TEXT, text, "", "");
    }

    /**
     * Creates a reasoning block.
     * @param text reasoning text
     * @return a REASONING block
     */
    public static ContentBlock reasoning(String text) {
        return new ContentBlock(Type.REASONING, text, "", "");
    }

    /**
     * Creates an image reference block.
     * @param refId identifier resolving the image content
     * @return an IMAGE block
     */
    public static ContentBlock image(String refId) {
        return new ContentBlock(Type.IMAGE, "", refId, "");
    }

    /**
     * Creates an attachment block.
     * @param refId identifier resolving the attachment
     * @param content attachment text content
     * @return a FILE block
     */
    public static ContentBlock file(String refId, String content) {
        return new ContentBlock(Type.FILE, content, refId, "");
    }

    /**
     * Creates a tool-call block.
     * @param toolCallId provider tool-call identifier
     * @param status call status label
     * @return a TOOL_CALL block
     */
    public static ContentBlock toolCall(String toolCallId, String status) {
        return new ContentBlock(Type.TOOL_CALL, "", toolCallId, status);
    }

    /**
     * Creates a tool-result block.
     * @param toolCallId provider tool-call identifier the result belongs to
     * @return a TOOL_RESULT block
     */
    public static ContentBlock toolResult(String toolCallId) {
        return new ContentBlock(Type.TOOL_RESULT, "", toolCallId, "");
    }

    /**
     * Creates a file-diff block.
     * @param diffId identifier resolving a {@link FileDiff}
     * @return a DIFF block
     */
    public static ContentBlock diff(String diffId) {
        return new ContentBlock(Type.DIFF, "", diffId, "");
    }

    /** Context-compaction marker; status is the compact row state (running/complete/error). */
    public static ContentBlock compact(String status) {
        return new ContentBlock(Type.COMPACT, "", "", status);
    }

    /** Visible failure reason for a failed assistant turn. */
    public static ContentBlock error(String text) {
        return new ContentBlock(Type.ERROR, text, "", "");
    }

    /**
     * Returns whether the block carries no content for its type.
     * @return true when the relevant field is empty for this block type
     */
    public boolean isEmpty() {
        if (type == Type.COMPACT) {
            return status.isEmpty();
        }
        return type == Type.TEXT || type == Type.REASONING || type == Type.FILE
                || type == Type.ERROR
                ? text.isEmpty() : refId.isEmpty();
    }

    /** Blocks are equal on type, text, reference and status. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContentBlock)) {
            return false;
        }
        ContentBlock that = (ContentBlock) other;
        return type == that.type
                && text.equals(that.text)
                && refId.equals(that.refId)
                && status.equals(that.status);
    }

    /** Hashes all four fields. */
    @Override
    public int hashCode() {
        return Objects.hash(type, text, refId, status);
    }
}
