package cn.lineai.data.api;

/** One recorded failure, shown in the error-log screen. */
public final class ErrorLog {

    public final String id;
    public final String title;
    public final String subtitle;
    public final String detail;
    public final long createdAt;

    /**
     * Creates one log entry. Null text fields are normalized to the empty string so the screen
     * never renders a null placeholder.
     *
     * @param id stable entry identifier
     * @param title short headline shown in the log list
     * @param subtitle optional secondary line
     * @param detail full technical description
     * @param createdAt time of the failure in epoch milliseconds
     */
    public ErrorLog(String id, String title, String subtitle, String detail, long createdAt) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.detail = detail == null ? "" : detail;
        this.createdAt = createdAt;
    }
}
