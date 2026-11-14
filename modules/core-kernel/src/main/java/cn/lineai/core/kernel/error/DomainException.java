package cn.lineai.core.kernel.error;

/**
 * The single exception type crossing layer boundaries. It carries a machine code plus a technical
 * detail string that is logged, never rendered.
 *
 * <p>Unchecked by design: layer boundaries translate it into user-facing state, and {@link #code()}
 * is the only stable part of that contract. The detail string is diagnostic output.</p>
 */
public final class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final String detail;

    /**
     * Creates an exception without an underlying cause.
     *
     * @param code stable classification used by UI mapping and logging
     * @param detail technical context for diagnostics; never rendered to the user
     */
    public DomainException(ErrorCode code, String detail) {
        super(code.name() + (detail == null || detail.isEmpty() ? "" : ": " + detail));
        this.code = code;
        this.detail = detail == null ? "" : detail;
    }

    /**
     * Creates an exception with an underlying cause.
     *
     * @param code stable classification used by UI mapping and logging
     * @param detail technical context for diagnostics; never rendered to the user
     * @param cause originating failure, preserved for diagnostics
     */
    public DomainException(ErrorCode code, String detail, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.detail = detail == null ? "" : detail;
    }

    /** Returns the stable classification of this failure. */
    public ErrorCode code() {
        return code;
    }

    /** Returns the technical detail string; empty when absent, never user-facing. */
    public String detail() {
        return detail;
    }
}
