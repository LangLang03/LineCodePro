package cn.lineai.core.model;

/** One item in the session TODO list. */
public final class TodoItem {

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED;

        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Status fromWire(String value) {
            if (IN_PROGRESS.wireName().equals(value)) return IN_PROGRESS;
            if (COMPLETED.wireName().equals(value)) return COMPLETED;
            return PENDING;
        }
    }

    public final String content;
    public final Status status;

    public TodoItem(String content, Status status) {
        this.content = content == null ? "" : content.trim();
        this.status = status == null ? Status.PENDING : status;
    }
}
