package cn.lineai.core.model;

/** One item in the session TODO list. */
public final class TodoItem {

    /** Progress state of one TODO entry, serialised in lowercase over the wire. */
    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED;

        /** Returns the lowercase wire name of this status. */
        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /**
         * Decodes a wire name.
         * @param value lowercase status name, may be null
         * @return the matching status, or {@link #PENDING} when unknown
         */
        public static Status fromWire(String value) {
            if (IN_PROGRESS.wireName().equals(value)) return IN_PROGRESS;
            if (COMPLETED.wireName().equals(value)) return COMPLETED;
            return PENDING;
        }
    }

    public final String content;
    public final Status status;

    /**
     * Creates an item, trimming the content and defaulting the status.
     * @param item content description, null becomes empty
     * @param status progress state, null becomes {@link Status#PENDING}
     */
    public TodoItem(String content, Status status) {
        this.content = content == null ? "" : content.trim();
        this.status = status == null ? Status.PENDING : status;
    }
}
