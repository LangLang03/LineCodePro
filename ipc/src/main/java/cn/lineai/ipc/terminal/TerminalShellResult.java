package cn.lineai.ipc.terminal;

/**
 * Immutable carrier for the outcome of a blocking shell execution.
 *
 * <p>Wraps the raw {@code executeShell} return code so callers can branch on success
 * without knowing the provider's error-code convention.
 */
public final class TerminalShellResult {
    private final int exitCode;

    /**
     * Creates a result.
     *
     * @param exitCode the guest shell exit code, or a negative provider error code
     */
    public TerminalShellResult(int exitCode) {
        this.exitCode = exitCode;
    }

    /**
     * Returns the provider return code: greater than or equal to zero is the guest shell
     * exit status, {@code -1} means an empty command or a failed launch, {@code -2} a timeout.
     */
    public int getExitCode() {
        return exitCode;
    }

    /** Returns true only when the guest shell terminated with status zero. */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
