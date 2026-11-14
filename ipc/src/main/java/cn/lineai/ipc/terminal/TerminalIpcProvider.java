package cn.lineai.ipc.terminal;

import android.os.RemoteException;
import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import org.json.JSONObject;

/**
 * Client for the terminal provider AIDL service.
 *
 * <p>Exposes the three capability groups of the frozen contract: blocking and background
 * shell execution, file access (whole-file and chunked), and provider metadata. Every
 * remote method requires a live binding; callers observe the {@link BaseIpcProvider}
 * connection state before invoking. All calls throw {@link RemoteException} when the
 * provider process is unreachable.
 */
public final class TerminalIpcProvider extends BaseIpcProvider {

    /**
     * Creates an unbound terminal provider.
     *
     * @param config configuration describing the provider service to bind
     */
    public TerminalIpcProvider(IpcProviderConfig config) {
        super(config);
    }

    /** Returns {@link IpcProviderType#TERMINAL}. */
    @Override
    public IpcProviderType getProviderType() {
        return IpcProviderType.TERMINAL;
    }

    /**
     * Shell execution is a privileged capability, so hosts are asked to show an explicit
     * user confirmation before using this provider.
     */
    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    /** Returns the AIDL interface, failing fast when called before the binding is live. */
    private ITerminalProviderService getService() {
        if (!isBound() || serviceBinder == null) {
            throw new IllegalStateException("terminal provider service is not bound");
        }
        return ITerminalProviderService.Stub.asInterface(serviceBinder);
    }

    /**
     * Runs one shell command on the provider and blocks the Binder call until it exits or
     * the timeout elapses.
     *
     * @param command the shell command line for {@code /system/bin/sh -c}
     * @param cwd working directory; empty or null selects the provider home
     * @param timeoutMs timeout in millis; non-positive values default to 30 seconds
     * @param callback streaming sink for stdout, stderr and completion; may be null
     * @return the wrapped return code: greater than or equal to zero is the guest exit
     *         status, {@code -1} an empty command or failed launch, {@code -2} a timeout
     * @throws RemoteException when the provider process is unreachable
     */
    public TerminalShellResult executeShell(String command, String cwd, long timeoutMs,
                                            TerminalShellCallback callback) throws RemoteException {
        ITerminalProviderService service = getService();
        ITerminalProviderCallback aidlCallback = adapt(callback);
        int exitCode = service.executeShell(command, cwd, timeoutMs, aidlCallback);
        return new TerminalShellResult(exitCode);
    }

    /**
     * Starts a shell command in the background and returns immediately.
     *
     * @param command the shell command line for {@code /system/bin/sh -c}
     * @param cwd working directory; empty or null selects the provider home
     * @param callback streaming sink for output and completion; may be null
     * @return the provider-side handle for polling and teardown, or a negative value on failure
     * @throws RemoteException when the provider process is unreachable
     */
    public long startShellBackground(String command, String cwd,
                                     TerminalShellCallback callback) throws RemoteException {
        ITerminalProviderService service = getService();
        return service.startShellBackground(command, cwd, adapt(callback));
    }

    /**
     * Polls the output and status of a background task.
     *
     * @param handleId the handle returned by {@link #startShellBackground}
     * @param offset byte offset into the recorded output; pass the previous read length
     * @return the current snapshot; a handle for a finished or unknown task yields an
     *         empty chunk
     * @throws RemoteException when the provider process is unreachable
     */
    public TerminalShellBackgroundState readShellBackground(long handleId, long offset)
            throws RemoteException {
        return getService().readShellBackground(handleId, offset);
    }

    /**
     * Kills the task's whole process tree and frees its handle.
     *
     * @param handleId the handle returned by {@link #startShellBackground}
     * @return true when a live task existed and was killed
     * @throws RemoteException when the provider process is unreachable
     */
    public boolean killShellBackground(long handleId) throws RemoteException {
        return getService().killShellBackground(handleId);
    }

    /**
     * Releases the handle without killing the process, so an intentionally detached task
     * keeps running while the provider drops its bookkeeping.
     *
     * @param handleId the handle returned by {@link #startShellBackground}
     * @throws RemoteException when the provider process is unreachable
     */
    public void forgetShellBackground(long handleId) throws RemoteException {
        getService().forgetShellBackground(handleId);
    }

    /** Wraps the Java callback into the AIDL stub; null callbacks are tolerated per event. */
    private static ITerminalProviderCallback adapt(TerminalShellCallback callback) {
        return new ITerminalProviderCallback.Stub() {
            @Override
            public void onOutput(String content) {
                if (callback != null) {
                    callback.onOutput(content);
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }

            @Override
            public void onComplete(int exitCode) {
                if (callback != null) {
                    callback.onComplete(exitCode);
                }
            }
        };
    }

    /**
     * Reads a whole file, subject to the provider's size cap; large files should use
     * {@link #readFileChunk(String, long, int)} instead.
     *
     * @param path absolute file path on the provider side
     * @return the file content; an empty array for missing, unreadable or oversized files
     * @throws RemoteException when the provider process is unreachable
     */
    public byte[] readFile(String path) throws RemoteException {
        return getService().readFile(path);
    }

    /**
     * Writes (truncating) a whole file.
     *
     * @param path absolute file path on the provider side
     * @param data content to write; null writes an empty file
     * @return true when the write succeeded
     * @throws RemoteException when the provider process is unreachable
     */
    public boolean writeFile(String path, byte[] data) throws RemoteException {
        return getService().writeFile(path, data);
    }

    /**
     * Reads one chunk of a file at an offset, keeping each AIDL transaction below the
     * binder limit.
     *
     * @param path absolute file path on the provider side
     * @param offset byte offset to start at; values past the end yield an empty array
     * @param size requested byte count; capped by the provider chunk limit
     * @return at most {@code size} bytes actually read; never null
     * @throws RemoteException when the provider process is unreachable
     */
    public byte[] readFileChunk(String path, long offset, int size) throws RemoteException {
        return getService().readFileChunk(path, offset, size);
    }

    /**
     * Writes one chunk of a file at an offset; a negative offset appends.
     *
     * @param path absolute file path on the provider side
     * @param offset byte offset to write at; negative appends to the end
     * @param data content to write; null writes nothing
     * @return true when the write succeeded
     * @throws RemoteException when the provider process is unreachable
     */
    public boolean writeFileChunk(String path, long offset, byte[] data) throws RemoteException {
        return getService().writeFileChunk(path, offset, data);
    }

    /**
     * Returns the file size, or {@code -1} for missing or unreadable paths.
     *
     * @throws RemoteException when the provider process is unreachable
     */
    public long getFileSize(String path) throws RemoteException {
        return getService().getFileSize(path);
    }

    /**
     * Deletes a file; directories are not removed.
     *
     * @param path absolute file path on the provider side
     * @return true when an existing file was deleted
     * @throws RemoteException when the provider process is unreachable
     */
    public boolean deleteFile(String path) throws RemoteException {
        return getService().deleteFile(path);
    }

    /**
     * Lists the entry names of a directory; null or empty resolves to the provider home.
     *
     * @return the entry names; empty for missing or unreadable directories
     * @throws RemoteException when the provider process is unreachable
     */
    public String[] listDir(String path) throws RemoteException {
        return getService().listDir(path);
    }

    /**
     * Reports whether the path exists.
     *
     * @throws RemoteException when the provider process is unreachable
     */
    public boolean fileExists(String path) throws RemoteException {
        return getService().fileExists(path);
    }

    /**
     * Returns the file size, or {@code -1} when the path is missing or not a regular file.
     *
     * @throws RemoteException when the provider process is unreachable
     */
    public long fileSize(String path) throws RemoteException {
        return getService().fileSize(path);
    }

    /**
     * Lists a directory with name, type and size per entry as a JSON array; null or empty
     * resolves to the provider home.
     *
     * @return the JSON array text, or {@code "[]"} for missing or unreadable directories
     * @throws RemoteException when the provider process is unreachable
     */
    public String listDirDetailed(String path) throws RemoteException {
        return getService().listDirDetailed(path);
    }

    /**
     * Extracts the home directory from the provider info JSON.
     *
     * @return the home path, or the empty string when the provider info is absent or malformed
     * @throws RemoteException when the provider process is unreachable
     */
    public String getHomePath() throws RemoteException {
        String info = getService().getProviderInfo();
        if (info == null || info.length() == 0) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(info);
            return json.optString("home", "");
        } catch (Exception e) {
            return "";
        }
    }
}
