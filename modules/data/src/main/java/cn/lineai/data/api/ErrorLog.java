package cn.lineai.data.api;

/** One recorded failure, shown in the error-log screen. */
public final class ErrorLog {

    public final String id;
    public final String title;
    public final String subtitle;
    public final String detail;
    public final long createdAt;

    public ErrorLog(String id, String title, String subtitle, String detail, long createdAt) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.detail = detail == null ? "" : detail;
        this.createdAt = createdAt;
    }
}
