package cn.lineai.core.model;

import java.util.Objects;

/** One renderable slice of a message: prose, reasoning, an attachment reference or a tool link. */
public final class ContentBlock {

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

    private ContentBlock(Type type, String text, String refId, String status) {
        this.type = type;
        this.text = text == null ? "" : text;
        this.refId = refId == null ? "" : refId;
        this.status = status == null ? "" : status;
    }

    public static ContentBlock text(String text) {
        return new ContentBlock(Type.TEXT, text, "", "");
    }

    public static ContentBlock reasoning(String text) {
        return new ContentBlock(Type.REASONING, text, "", "");
    }

    public static ContentBlock image(String refId) {
        return new ContentBlock(Type.IMAGE, "", refId, "");
    }

    public static ContentBlock file(String refId, String content) {
        return new ContentBlock(Type.FILE, content, refId, "");
    }

    public static ContentBlock toolCall(String toolCallId, String status) {
        return new ContentBlock(Type.TOOL_CALL, "", toolCallId, status);
    }

    public static ContentBlock toolResult(String toolCallId) {
        return new ContentBlock(Type.TOOL_RESULT, "", toolCallId, "");
    }

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

    public boolean isEmpty() {
        if (type == Type.COMPACT) {
            return status.isEmpty();
        }
        return type == Type.TEXT || type == Type.REASONING || type == Type.FILE
                || type == Type.ERROR
                ? text.isEmpty() : refId.isEmpty();
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(type, text, refId, status);
    }
}
