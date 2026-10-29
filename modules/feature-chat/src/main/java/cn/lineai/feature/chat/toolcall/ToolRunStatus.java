package cn.lineai.feature.chat.toolcall;

/**
 * Wire values for tool/review/agent progress states.
 *
 * <p>The architecture guard only allows the state words {@code pending}, {@code streaming},
 * {@code accepted}, {@code running}, {@code complete}, {@code failed} and {@code cancelled} inside
 * enum bodies; every comparison goes through this enum so the state machine stays explicit.</p>
 */
public enum ToolRunStatus {

    PENDING("pending"),
    STREAMING("streaming"),
    ACCEPTED("accepted"),
    RUNNING("running"),
    COMPLETE("complete"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    DONE("done"),
    ERROR("error"),
    WAITING("waiting"),
    REQUESTED("requested"),
    WAITING_UNLOCK("waiting_unlock"),
    REJECTED("rejected"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    public final String wire;

    ToolRunStatus(String wire) {
        this.wire = wire;
    }

    public boolean matches(String value) {
        return wire.equals(value);
    }

    /** Enum for a wire value, or {@code null} when it is unknown. */
    public static ToolRunStatus parse(String value) {
        if (value == null) return null;
        for (ToolRunStatus status : values()) {
            if (status.matches(value)) return status;
        }
        return null;
    }
}
