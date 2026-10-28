package cn.lineai.core.kernel.error;

/**
 * The single exception type crossing layer boundaries. It carries a machine code plus a technical
 * detail string that is logged, never rendered.
 */
public final class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final String detail;

    public DomainException(ErrorCode code, String detail) {
        super(code.name() + (detail == null || detail.isEmpty() ? "" : ": " + detail));
        this.code = code;
        this.detail = detail == null ? "" : detail;
    }

    public DomainException(ErrorCode code, String detail, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.detail = detail == null ? "" : detail;
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
