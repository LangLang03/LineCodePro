package cn.lineai.terminalprovider;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import cn.lineai.ipc.service.AbstractIpcProviderService;
import cn.lineai.ipc.service.IpcServerExecutors;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;
import cn.lineai.ipc.terminal.TerminalShellBackgroundState;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The terminal provider AIDL service: blocking shell execution, background shell handles and
 * chunked file access. Background tasks live in {@link BackgroundShellTasks}; process-tree
 * teardown lives in {@link ProcessKiller}.
 */
public final class TerminalProviderService extends AbstractIpcProviderService {
    private static final String TAG = "TerminalProvider";
    private static final String SHELL = "/system/bin/sh";
    // Default chunk cap for one transfer (1 MB): keeps a single AIDL transaction below the binder limit.
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_CHUNK_SIZE = DEFAULT_CHUNK_SIZE;
    // Byte cap for one readFile call (32 MB): avoids exhausting the heap on huge files.
    private static final long MAX_READ_FILE_BYTES = 32L * 1024L * 1024L;
    /** Error strings are a protocol with the host app; keep them stable and CJK-free. */
    static final String ERROR_EMPTY_COMMAND = "empty command";
    static final String ERROR_TIMEOUT = "command timed out";

    // Process-wide shared executor owned by the :ipc library.
    private final ExecutorService executor = IpcServerExecutors.shared();
    /** Created lazily: getFilesDir() is unavailable before the service context is attached. */
    private BackgroundShellTasks backgroundTasks;

    /** Delivers an error to the callback, tolerating a null callback or a dead binder. */
    private static void notifyError(ITerminalProviderCallback callback, String message) {
        if (callback == null) {
            return;
        }
        try {
            callback.onError(message);
        } catch (RemoteException callbackGone) {
            Log.w(TAG, "onError not delivered", callbackGone);
        }
    }

    /** Builds the AIDL stub implementing the frozen terminal provider contract. */
    @Override
    protected IBinder createBinder() {
        if (backgroundTasks == null) {
            backgroundTasks = new BackgroundShellTasks(getFilesDir());
        }
        return new ITerminalProviderService.Stub() {

            /** Returns the stable provider id; must match {@code IpcProviderType.TERMINAL}. */
            @Override
            public String getProviderType() {
                return "terminal";
            }

            /** Returns the provider metadata JSON: name, version, shell path, home and capabilities. */
            @Override
            public String getProviderInfo() {
                JSONObject info = new JSONObject();
                try {
                    info.put("name", "Android Shell Terminal Provider");
                    info.put("version", "2.0");
                    info.put("shell", SHELL);
                    info.put("home", getFilesDir().getAbsolutePath());
                    info.put("capabilities", new JSONArray()
                            .put("executeShell")
                            .put("backgroundShell")
                            .put("readFile")
                            .put("writeFile")
                            .put("deleteFile")
                            .put("listDir")
                            .put("fileExists")
                            .put("fileSize"));
                } catch (Exception e) {
                    Log.w(TAG, "provider info build failed", e);
                }
                return info.toString();
            }

            /** Returns true only when the guest shell binary exists on this device. */
            @Override
            public boolean isAvailable() {
                return new File(SHELL).exists();
            }

            /**
             * Runs one shell command to completion on the shared executor and blocks the
             * Binder thread until it exits or the timeout elapses.
             *
             * <p>Return-code contract: a value greater than or equal to zero is the guest
             * shell exit status; {@code -1} means an empty command or a failed launch;
             * {@code -2} means the timeout elapsed, in which case the whole process tree
             * is killed and {@code ERROR_TIMEOUT} is delivered to the callback. Failure
             * details are reported through {@code onError} with the stable
             * {@code ERROR_*} protocol strings, never by throwing.
             *
             * @param command the command line for {@code /system/bin/sh -c}
             * @param cwd working directory; empty or null selects the provider home
             * @param timeoutMs timeout in millis; non-positive values default to 30 seconds
             * @param callback streaming sink for output and completion; may be null
             * @return the guest exit status, or the negative error codes described above
             */
            @Override
            public int executeShell(String command, String cwd, long timeoutMs,
                                     ITerminalProviderCallback callback) {
                if (command == null || command.length() == 0) {
                    notifyError(callback, ERROR_EMPTY_COMMAND);
                    return -1;
                }
                File workingDir = (cwd != null && cwd.length() > 0)
                        ? new File(cwd)
                        : getFilesDir();
                if (!workingDir.exists()) {
                    workingDir = getFilesDir();
                }
                final File finalWorkingDir = workingDir;
                final AtomicReference<Process> processRef = new AtomicReference<>();
                final AtomicBoolean finished = new AtomicBoolean(false);
                Future<Integer> future = executor.submit(() -> {
                    Process process = null;
                    try {
                        ProcessBuilder pb = new ProcessBuilder(SHELL, "-c", command);
                        pb.directory(finalWorkingDir);
                        pb.redirectErrorStream(false);
                        process = pb.start();
                        processRef.set(process);
                        int exitCode = runWithPumps(process, callback, finished);
                        return exitCode;
                    } catch (Exception e) {
                        Log.e(TAG, "executeShell failed", e);
                        if (callback != null && !finished.get()) {
                            notifyError(callback, e.getMessage() == null
                                    ? e.toString() : e.getMessage());
                        }
                        return -1;
                    } finally {
                        if (process != null) {
                            // On normal completion and on errors destroy only the shell itself and
                            // keep user-spawned background children (e.g. `nohup server &`); the
                            // process-tree kill is reserved for the timeout path.
                            process.destroy();
                        }
                    }
                });
                try {
                    long effectiveTimeout = timeoutMs > 0 ? timeoutMs : 30000L;
                    return future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    // Timeout: kill the whole process tree and mark the outcome finished so no further callbacks fire.
                    if (finished.compareAndSet(false, true)) {
                        ProcessKiller.killTree(processRef.get());
                        notifyError(callback, ERROR_TIMEOUT);
                    }
                    return -2;
                } catch (Exception e) {
                    Log.e(TAG, "executeShell wait failed", e);
                    if (callback != null) {
                        notifyError(callback, e.getMessage() == null
                                ? e.toString() : e.getMessage());
                    }
                    return -1;
                }
            }

            /**
             * Runs the shell to completion. Output is drained by the sanctioned daemon
             * pump threads of {@link ShellPumps#pump} so the guest never blocks on full
             * pipes; {@code finished} gates further output once the exit code is known.
             */
            private int runWithPumps(Process process, ITerminalProviderCallback callback,
                                     AtomicBoolean finished) throws InterruptedException {
                Thread stdoutThread = ShellPumps.pump(process.getInputStream(), callback,
                        finished, "sh-stdout");
                Thread stderrThread = ShellPumps.pump(process.getErrorStream(), callback,
                        finished, "sh-stderr");
                stdoutThread.start();
                stderrThread.start();
                int exitCode = process.waitFor();
                stdoutThread.join(1000);
                stderrThread.join(1000);
                // Marking finished stops post-completion onOutput spam from pumps that are
                // still blocked on an inherited background child (e.g. `nohup server &`).
                if (callback != null && finished.compareAndSet(false, true)) {
                    try {
                        callback.onComplete(exitCode);
                    } catch (RemoteException callbackGone) {
                        Log.w(TAG, "onComplete not delivered", callbackGone);
                    }
                }
                return exitCode;
            }

            /**
             * Starts a detached shell command managed by {@link BackgroundShellTasks};
             * empty commands report {@code ERROR_EMPTY_COMMAND} and return {@code -1}.
             *
             * @return the background handle, or a negative value on failure
             */
            @Override
            public long startShellBackground(String command, String cwd,
                                             ITerminalProviderCallback callback) {
                if (command == null || command.length() == 0) {
                    notifyError(callback, ERROR_EMPTY_COMMAND);
                    return -1;
                }
                return backgroundTasks.start(command, cwd, callback);
            }

            /** Returns the task snapshot and output recorded after the given offset. */
            @Override
            public TerminalShellBackgroundState readShellBackground(long handleId, long offset) {
                return backgroundTasks.read(handleId, offset);
            }

            /** Kills the task's whole process tree and frees its handle. */
            @Override
            public boolean killShellBackground(long handleId) {
                return backgroundTasks.kill(handleId);
            }

            /** Drops the handle without killing the intentionally detached process. */
            @Override
            public void forgetShellBackground(long handleId) {
                backgroundTasks.forget(handleId);
            }

            /**
             * Reads a whole file in chunked sub-reads, rejecting anything above the
             * 32 MB cap; callers of larger files must fall back to {@code readFileChunk}.
             *
             * @return the file content, or an empty array for missing, unreadable or
             *         oversized files
             */
            @Override
            public byte[] readFile(String path) {
                if (path == null || path.length() == 0) {
                    return new byte[0];
                }
                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    return new byte[0];
                }
                long size = file.length();
                if (size <= 0L) {
                    return new byte[0];
                }
                if (size > MAX_READ_FILE_BYTES) {
                    Log.w(TAG, "readFile: " + path + " exceeds the "
                            + MAX_READ_FILE_BYTES + " byte cap (size=" + size
                            + "); use readFileChunk instead");
                    return new byte[0];
                }
                if (size > Integer.MAX_VALUE) {
                    Log.w(TAG, "readFile: " + path + " exceeds the int range");
                    return new byte[0];
                }
                int total = (int) size;
                try {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(total);
                    long offset = 0L;
                    while (offset < total) {
                        int want = (int) Math.min((long) MAX_CHUNK_SIZE, (long) total - offset);
                        byte[] chunk = readFileChunk(path, offset, want);
                        if (chunk.length == 0) {
                            break;
                        }
                        buffer.write(chunk);
                        offset += chunk.length;
                    }
                    return buffer.toByteArray();
                } catch (Exception e) {
                    Log.e(TAG, "readFile failed: " + path, e);
                    return new byte[0];
                }
            }

            /** Writes (truncating) a whole file; null data writes an empty file. */
            @Override
            public boolean writeFile(String path, byte[] data) {
                if (path == null || path.length() == 0) {
                    return false;
                }
                byte[] payload = data == null ? new byte[0] : data;
                return writeFileChunk(path, 0L, payload);
            }

            /** Deletes an existing file; directories are not removed. */
            @Override
            public boolean deleteFile(String path) {
                if (path == null || path.length() == 0) {
                    return false;
                }
                File file = new File(path);
                if (!file.exists()) {
                    return false;
                }
                return file.delete();
            }

            /** Lists entry names; null or empty resolves to the provider home. */
            @Override
            public String[] listDir(String path) {
                if (path == null || path.length() == 0) {
                    path = getFilesDir().getAbsolutePath();
                }
                File dir = new File(path);
                if (!dir.exists() || !dir.isDirectory()) {
                    return new String[0];
                }
                File[] files = dir.listFiles();
                if (files == null) {
                    return new String[0];
                }
                List<String> result = new ArrayList<>();
                for (File f : files) {
                    result.add(f.getName());
                }
                return result.toArray(new String[0]);
            }

            /** Reports whether the path exists. */
            @Override
            public boolean fileExists(String path) {
                if (path == null || path.length() == 0) {
                    return false;
                }
                return new File(path).exists();
            }

            /** Returns the file size, or {@code -1} when the path is missing or not a regular file. */
            @Override
            public long fileSize(String path) {
                if (path == null || path.length() == 0) {
                    return -1;
                }
                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    return -1;
                }
                return file.length();
            }

            /**
             * Reads at most {@code size} bytes from the offset, keeping each AIDL
             * transaction below the binder limit; non-positive sizes select the default
             * chunk size, oversized requests are capped.
             */
            @Override
            public byte[] readFileChunk(String path, long offset, int size) {
                if (path == null || path.length() == 0) {
                    return new byte[0];
                }
                if (offset < 0L) {
                    Log.w(TAG, "readFileChunk: negative offset treated as 0: " + path);
                    offset = 0L;
                }
                int want = size <= 0 ? DEFAULT_CHUNK_SIZE : size;
                if (want > MAX_CHUNK_SIZE) {
                    want = MAX_CHUNK_SIZE;
                }
                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    return new byte[0];
                }
                long fileLength = file.length();
                if (offset >= fileLength) {
                    return new byte[0];
                }
                long remaining = fileLength - offset;
                int toRead = (int) Math.min((long) want, remaining);
                byte[] out = new byte[toRead];
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(offset);
                    int read = 0;
                    while (read < toRead) {
                        int n = raf.read(out, read, toRead - read);
                        if (n <= 0) {
                            break;
                        }
                        read += n;
                    }
                    if (read < toRead) {
                        byte[] trimmed = new byte[read];
                        System.arraycopy(out, 0, trimmed, 0, read);
                        return trimmed;
                    }
                    return out;
                } catch (IOException e) {
                    Log.e(TAG, "readFileChunk failed: " + path + "@" + offset, e);
                    return new byte[0];
                }
            }

            /** Writes at the offset, creating parent directories; a negative offset appends. */
            @Override
            public boolean writeFileChunk(String path, long offset, byte[] data) {
                if (path == null || path.length() == 0) {
                    return false;
                }
                byte[] payload = data == null ? new byte[0] : data;
                File file = new File(path);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        return false;
                    }
                }
                boolean append = offset < 0L;
                if (append) {
                    try (FileOutputStream fos = new FileOutputStream(file, true)) {
                        fos.write(payload);
                        fos.flush();
                        return true;
                    } catch (IOException e) {
                        Log.e(TAG, "writeFileChunk(append) failed: " + path, e);
                        return false;
                    }
                }
                long at = Math.max(0L, offset);
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.seek(at);
                    raf.write(payload);
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "writeFileChunk failed: " + path + "@" + at, e);
                    return false;
                }
            }

            /** Alias of {@link #fileSize(String)} kept for contract compatibility. */
            @Override
            public long getFileSize(String path) {
                return fileSize(path);
            }

            /** Lists entries as a JSON array of name/dir/size objects; empty input resolves to the home. */
            @Override
            public String listDirDetailed(String path) {
                if (path == null || path.length() == 0) {
                    path = getFilesDir().getAbsolutePath();
                }
                File dir = new File(path);
                if (!dir.exists() || !dir.isDirectory()) {
                    return "[]";
                }
                File[] files = dir.listFiles();
                if (files == null) {
                    return "[]";
                }
                JSONArray array = new JSONArray();
                for (File f : files) {
                    try {
                        JSONObject entry = new JSONObject();
                        entry.put("name", f.getName());
                        entry.put("dir", f.isDirectory());
                        entry.put("size", f.length());
                        array.put(entry);
                    } catch (Exception e) {
                        Log.w(TAG, "listDirDetailed entry skipped", e);
                    }
                }
                return array.toString();
            }
        };
    }

    /** Tears down every still-running background task so no process outlives the service. */
    @Override
    protected void onProviderDestroy() {
        // Provider teardown kills every still-running background task.
        if (backgroundTasks != null) {
            backgroundTasks.shutdown();
        }
        Log.i(TAG, "TerminalProviderService destroyed");
    }

    /**
     * Pump threads shared by the blocking execute path. The blocking reads are the
     * sanctioned drain pattern: without them a guest writing more than the pipe buffer
     * would deadlock, so these daemon threads are expected to outlive short commands and
     * are stopped by the stream closing or the finished gate.
     */
    private static final class ShellPumps {
        /** Returns a started-not-yet daemon thread forwarding each line to the callback. */
        static Thread pump(java.io.InputStream stream, ITerminalProviderCallback callback,
                           AtomicBoolean finished, String name) {
            return new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (callback != null && !finished.get()) {
                            callback.onOutput(line + "\n");
                        }
                    }
                } catch (IOException streamClosed) {
                    Log.w(TAG, "shell stream closed", streamClosed);
                } catch (RemoteException callbackGone) {
                    Log.w(TAG, "shell onOutput not delivered", callbackGone);
                }
            }, name);
        }
    }
}
