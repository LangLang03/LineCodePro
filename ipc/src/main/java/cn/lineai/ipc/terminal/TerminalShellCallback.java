package cn.lineai.ipc.terminal;

/**
 * Client-side sink for streaming shell output from a terminal provider.
 *
 * <p>The provider client adapts instances to the AIDL callback stub. Implementations are
 * invoked on Binder threads and must only record the data or hand it to their own
 * dispatcher; long work on the callback thread stalls the provider's pump threads.
 */
public interface TerminalShellCallback {
    /**
     * Delivers one chunk of standard output.
     *
     * @param content the UTF-8 decoded text of the chunk, newline terminated
     */
    void onOutput(String content);

    /**
     * Delivers one chunk of standard error, or a provider-side failure message such as
     * the {@code ERROR_*} protocol constants.
     *
     * @param error the error text
     */
    void onError(String error);

    /**
     * Reports completion of the command. Delivered exactly once per execution.
     *
     * @param exitCode the guest shell exit code, or a negative provider error code
     */
    void onComplete(int exitCode);
}
