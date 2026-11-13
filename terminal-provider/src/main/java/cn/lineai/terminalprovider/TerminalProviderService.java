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

    @Override
    protected IBinder createBinder() {
        if (backgroundTasks == null) {
            backgroundTasks = new BackgroundShellTasks(getFilesDir());
        }
        return new ITerminalProviderService.Stub() {

            @Override
            public String getProviderType() {
                return "terminal";
            }

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

            @Override
            public boolean isAvailable() {
                return new File(SHELL).exists();
            }

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

            /** Runs the shell to completion with per-stream pump threads. */
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

            @Override
            public long startShellBackground(String command, String cwd,
                                             ITerminalProviderCallback callback) {
                if (command == null || command.length() == 0) {
                    notifyError(callback, ERROR_EMPTY_COMMAND);
                    return -1;
                }
                return backgroundTasks.start(command, cwd, callback);
            }

            @Override
            public TerminalShellBackgroundState readShellBackground(long handleId, long offset) {
                return backgroundTasks.read(handleId, offset);
            }

            @Override
            public boolean killShellBackground(long handleId) {
                return backgroundTasks.kill(handleId);
            }

            @Override
            public void forgetShellBackground(long handleId) {
                backgroundTasks.forget(handleId);
            }

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

            @Override
            public boolean writeFile(String path, byte[] data) {
                if (path == null || path.length() == 0) {
                    return false;
                }
                byte[] payload = data == null ? new byte[0] : data;
                return writeFileChunk(path, 0L, payload);
            }

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

            @Override
            public boolean fileExists(String path) {
                if (path == null || path.length() == 0) {
                    return false;
                }
                return new File(path).exists();
            }

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

            @Override
            public long getFileSize(String path) {
                return fileSize(path);
            }

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

    @Override
    protected void onProviderDestroy() {
        // Provider teardown kills every still-running background task.
        if (backgroundTasks != null) {
            backgroundTasks.shutdown();
        }
        Log.i(TAG, "TerminalProviderService destroyed");
    }

    /** Pump threads shared by the blocking execute path. */
    private static final class ShellPumps {
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
