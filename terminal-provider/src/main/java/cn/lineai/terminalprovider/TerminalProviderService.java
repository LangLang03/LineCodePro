package cn.lineai.terminalprovider;

import android.os.IBinder;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;
import cn.lineai.ipc.service.AbstractIpcProviderService;
import cn.lineai.ipc.service.IpcServerExecutors;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;
import cn.lineai.ipc.terminal.TerminalShellBackgroundState;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

public final class TerminalProviderService extends AbstractIpcProviderService {
    private static final String TAG = "TerminalProvider";
    private static final String SHELL = "/system/bin/sh";
    // 单次分块传输的默认上限（1MB），避免 AIDL Binder 事务超限。
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_CHUNK_SIZE = DEFAULT_CHUNK_SIZE;
    // readFile 一次性读入内存的字节上限（32MB），避免超大文件耗尽堆内存。
    private static final long MAX_READ_FILE_BYTES = 32L * 1024L * 1024L;
    // 后台任务缓冲上限：超过后停止追加（保留前缀），偏移量语义保持稳定。
    private static final int MAX_BACKGROUND_CHARS = 512 * 1024;

    // 进程级共享线程池，由 :ipc 库统一管理。
    private final ExecutorService executor = IpcServerExecutors.shared();
    private final Map<Long, BackgroundTask> backgroundTasks = new ConcurrentHashMap<>();
    private final AtomicLong backgroundHandleSeq = new AtomicLong();

    /** 后台任务：进程、输出缓冲与回调；句柄在任务被 kill/forget 后移除。 */
    private static final class BackgroundTask {
        final long handleId;
        final Process process;
        final ITerminalProviderCallback callback;
        final StringBuilder buffer = new StringBuilder();
        volatile boolean running = true;
        volatile int exitCode = -1;
        volatile boolean truncated;

        BackgroundTask(long handleId, Process process, ITerminalProviderCallback callback) {
            this.handleId = handleId;
            this.process = process;
            this.callback = callback;
        }

        synchronized void append(String text) {
            if (buffer.length() >= MAX_BACKGROUND_CHARS) {
                truncated = true;
                return;
            }
            buffer.append(text);
            if (buffer.length() > MAX_BACKGROUND_CHARS) {
                buffer.setLength(MAX_BACKGROUND_CHARS);
                truncated = true;
            }
        }

        synchronized String chunkFrom(long offset) {
            if (offset < 0 || offset >= buffer.length()) {
                return "";
            }
            return buffer.substring((int) offset);
        }
    }

    private static void notifyError(ITerminalProviderCallback callback, String message) {
        if (callback == null) {
            return;
        }
        try {
            callback.onError(message);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    protected IBinder createBinder() {
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
                    info.put("capabilities", new org.json.JSONArray()
                            .put("executeShell")
                            .put("backgroundShell")
                            .put("readFile")
                            .put("writeFile")
                            .put("deleteFile")
                            .put("listDir")
                            .put("fileExists")
                            .put("fileSize"));
                } catch (Exception ignored) {
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
                    if (callback != null) {
                        try {
                            callback.onError("命令为空");
                        } catch (RemoteException ignored) {
                        }
                    }
                    return -1;
                }
                File workingDir = (cwd != null && cwd.length() > 0)
                        ? new File(cwd)
                        : new File(getFilesDir().getAbsolutePath());
                if (!workingDir.exists()) {
                    workingDir = getFilesDir();
                }
                final File finalWorkingDir = workingDir;
                final AtomicReference<Process> processRef = new AtomicReference<>();
                final AtomicBoolean finished = new AtomicBoolean(false);
                Future<Integer> future = executor.submit(() -> {
                    Process process = null;
                    BufferedReader stdoutReader = null;
                    BufferedReader stderrReader = null;
                    try {
                        ProcessBuilder pb = new ProcessBuilder(SHELL, "-c", command);
                        pb.directory(finalWorkingDir);
                        pb.redirectErrorStream(false);
                        process = pb.start();
                        processRef.set(process);
                        stdoutReader = new BufferedReader(
                                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                        stderrReader = new BufferedReader(
                                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                        final BufferedReader finalStdout = stdoutReader;
                        final BufferedReader finalStderr = stderrReader;
                        final Process finalProcess = process;
                        Thread stdoutThread = new Thread(() -> {
                            String line;
                            try {
                                while ((line = finalStdout.readLine()) != null) {
                                    if (callback != null && !finished.get()) {
                                        callback.onOutput(line + "\n");
                                    }
                                }
                            } catch (IOException | RemoteException ignored) {
                            }
                        });
                        Thread stderrThread = new Thread(() -> {
                            String line;
                            try {
                                while ((line = finalStderr.readLine()) != null) {
                                    if (callback != null && !finished.get()) {
                                        callback.onOutput(line + "\n");
                                    }
                                }
                            } catch (IOException | RemoteException ignored) {
                            }
                        });
                        stdoutThread.start();
                        stderrThread.start();
                        int exitCode = finalProcess.waitFor();
                        stdoutThread.join(1000);
                        stderrThread.join(1000);
                        if (callback != null && !finished.get()) {
                            callback.onComplete(exitCode);
                        }
                        return exitCode;
                    } catch (Exception e) {
                        Log.e(TAG, "executeShell failed", e);
                        if (callback != null && !finished.get()) {
                            try {
                                callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                            } catch (RemoteException ignored) {
                            }
                        }
                        return -1;
                    } finally {
                        if (stdoutReader != null) {
                            try { stdoutReader.close(); } catch (IOException ignored) {}
                        }
                        if (stderrReader != null) {
                            try { stderrReader.close(); } catch (IOException ignored) {}
                        }
                        if (process != null) {
                            // 正常完成/异常时只销毁 shell 本身，保留用户启动的后台子进程
                            // （如 nohup server &）；进程树击杀仅用于超时路径。
                            process.destroy();
                        }
                    }
                });
                try {
                    long effectiveTimeout = timeoutMs > 0 ? timeoutMs : 30000L;
                    return future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    // 超时：终止整个进程树并把结果状态置为已结束，阻止后续任何回调。
                    if (finished.compareAndSet(false, true)) {
                        Process shell = processRef.get();
                        if (shell != null) {
                            killProcessTree(shell);
                        }
                        if (callback != null) {
                            try {
                                callback.onError("命令执行超时");
                            } catch (RemoteException ignored) {
                            }
                        }
                    }
                    return -2;
                } catch (Exception e) {
                    Log.e(TAG, "executeShell wait failed", e);
                    if (callback != null) {
                        try {
                            callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                        } catch (RemoteException ignored) {
                        }
                    }
                    return -1;
                }
            }

            @Override
            public long startShellBackground(String command, String cwd,
                                             ITerminalProviderCallback callback) {
                if (command == null || command.length() == 0) {
                    notifyError(callback, "命令为空");
                    return -1;
                }
                File workingDir = (cwd != null && cwd.length() > 0)
                        ? new File(cwd)
                        : new File(getFilesDir().getAbsolutePath());
                if (!workingDir.exists()) {
                    workingDir = getFilesDir();
                }
                final long handle = backgroundHandleSeq.incrementAndGet();
                try {
                    ProcessBuilder pb = new ProcessBuilder(SHELL, "-c", command);
                    pb.directory(workingDir);
                    pb.redirectErrorStream(false);
                    Process process = pb.start();
                    BackgroundTask task = new BackgroundTask(handle, process, callback);
                    backgroundTasks.put(handle, task);
                    executor.submit(() -> runBackground(task));
                    return handle;
                } catch (Exception e) {
                    Log.e(TAG, "startShellBackground failed", e);
                    backgroundTasks.remove(handle);
                    notifyError(callback, e.getMessage() == null ? e.toString() : e.getMessage());
                    return -1;
                }
            }

            @Override
            public TerminalShellBackgroundState readShellBackground(long handleId, long offset) {
                BackgroundTask task = backgroundTasks.get(handleId);
                if (task == null) {
                    return new TerminalShellBackgroundState(handleId, false, -1, "");
                }
                return new TerminalShellBackgroundState(handleId, task.running, task.exitCode,
                        task.chunkFrom(offset));
            }

            @Override
            public boolean killShellBackground(long handleId) {
                BackgroundTask task = backgroundTasks.remove(handleId);
                if (task == null) {
                    return false;
                }
                task.running = false;
                killProcessTree(task.process);
                return true;
            }

            @Override
            public void forgetShellBackground(long handleId) {
                backgroundTasks.remove(handleId);
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
                    Log.e(TAG, "readFile: 文件超过读取上限 " + MAX_READ_FILE_BYTES + " 字节: " + path
                            + " (size=" + size + ")，请使用 readFileChunk 分块读取");
                    return new byte[0];
                }
                if (size > Integer.MAX_VALUE) {
                    Log.e(TAG, "readFile: 文件超过 int 范围: " + path);
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
                    Log.w(TAG, "readFileChunk: offset<0 已按 0 处理: " + path);
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
                if (offset < 0L) {
                    offset = 0L;
                }
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.seek(offset);
                    raf.write(payload);
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "writeFileChunk failed: " + path + "@" + offset, e);
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
                org.json.JSONArray array = new org.json.JSONArray();
                for (File f : files) {
                    try {
                        JSONObject entry = new JSONObject();
                        entry.put("name", f.getName());
                        entry.put("dir", f.isDirectory());
                        entry.put("size", f.length());
                        array.put(entry);
                    } catch (Exception ignored) {
                    }
                }
                return array.toString();
            }
        };
    }

    @Override
    protected void onProviderDestroy() {
        // 销毁提供方时终止所有仍在运行的后台任务。
        for (Long handle : backgroundTasks.keySet().toArray(new Long[0])) {
            BackgroundTask task = backgroundTasks.remove(handle);
            if (task != null) {
                task.running = false;
                killProcessTree(task.process);
            }
        }
        Log.i(TAG, "TerminalProviderService 销毁");
    }

    /** 后台任务主体：双线程流式排空输出，进程退出后记录退出码并回调。 */
    private void runBackground(BackgroundTask task) {
        final Process process = task.process;
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String chunk = line + "\n";
                    task.append(chunk);
                    try {
                        task.callback.onOutput(chunk);
                    } catch (RemoteException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }, "bg-stdout-" + task.handleId);
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String chunk = line + "\n";
                    task.append(chunk);
                    try {
                        task.callback.onOutput(chunk);
                    } catch (RemoteException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }, "bg-stderr-" + task.handleId);
        stdoutThread.start();
        stderrThread.start();
        try {
            int exitCode = process.waitFor();
            stdoutThread.join(1000);
            stderrThread.join(1000);
            task.exitCode = exitCode;
            task.running = false;
            try {
                task.callback.onComplete(exitCode);
            } catch (RemoteException ignored) {
            }
        } catch (Exception e) {
            Log.e(TAG, "background task failed", e);
            task.exitCode = -1;
            task.running = false;
            notifyError(task.callback, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * 终止指定进程及其全部后代进程（扫描 /proc 下各进程的 stat 文件建立 ppid 链）。
     * 依赖父进程持有该进程树，先杀后代再杀 shell 自身，最后兜底 destroy()。
     */
    private static void killProcessTree(Process process) {
        if (process == null) {
            return;
        }
        int pid = processPid(process);
        if (pid <= 0) {
            process.destroy();
            return;
        }
        for (int descendant : collectDescendants(pid)) {
            safeKill(descendant);
        }
        safeKill(pid);
        process.destroy();
    }

    /**
     * 获取 Process 的 pid。Android 的 {@link java.lang.Process} 未公开 pid() 方法，
     * 通过反射读取内部 pid 字段（ProcessImpl）。
     */
    private static int processPid(Process process) {
        if (process == null) {
            return -1;
        }
        try {
            Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            Object value = field.get(process);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * 扫描 /proc 下各进程的 stat 文件，收集以 rootPid 为根的全部后代进程 pid。
     */
    private static Set<Integer> collectDescendants(int rootPid) {
        Map<Integer, Integer> ppidByPid = new HashMap<>();
        File[] dirs = new File("/proc").listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                int pid;
                try {
                    pid = Integer.parseInt(dir.getName());
                } catch (NumberFormatException ignored) {
                    continue;
                }
                String stat = readStatLine(dir);
                if (stat == null) {
                    continue;
                }
                int ppid = parsePpid(stat);
                if (ppid > 0 && pid != rootPid) {
                    ppidByPid.put(pid, ppid);
                }
            }
        }
        Set<Integer> descendants = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Integer, Integer> entry : ppidByPid.entrySet()) {
                int child = entry.getKey();
                int parent = entry.getValue();
                if ((parent == rootPid || descendants.contains(parent)) && descendants.add(child)) {
                    changed = true;
                }
            }
        } while (changed);
        return descendants;
    }

    /**
     * 从 /proc/<pid>/stat 的单行内容解析 ppid。格式为：pid (comm) state ppid ...
     */
    private static int parsePpid(String stat) {
        int close = stat.lastIndexOf(')');
        if (close < 0) {
            return -1;
        }
        String rest = stat.substring(close + 1).trim();
        if (rest.isEmpty()) {
            return -1;
        }
        String[] fields = rest.split("\\s+");
        if (fields.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(fields[1]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String readStatLine(File procDir) {
        File stat = new File(procDir, "stat");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(stat), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void safeKill(int pid) {
        if (pid <= 0) {
            return;
        }
        try {
            Os.kill(pid, android.system.OsConstants.SIGKILL);
        } catch (Throwable ignored) {
            // 进程可能已退出或权限受限，忽略。
        }
    }
}
