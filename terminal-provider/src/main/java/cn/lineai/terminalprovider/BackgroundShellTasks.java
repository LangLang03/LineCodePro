package cn.lineai.terminalprovider;

import android.os.RemoteException;
import android.util.Log;
import cn.lineai.ipc.service.IpcServerExecutors;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.TerminalShellBackgroundState;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provider-side registry of background shell tasks: process handle, output ring buffer and
 * streamed callbacks. Handles are freed by {@link #kill} / {@link #forget}; everything still
 * running is killed when {@link #shutdown} fires on provider destroy.
 */
final class BackgroundShellTasks {

    private static final String TAG = "TerminalProvider";
    // Output buffer cap: appending stops beyond it (the prefix is kept) so the offset
    // semantics of polled reads stay stable.
    private static final int MAX_BACKGROUND_CHARS = 512 * 1024;

    private final ExecutorService executor = IpcServerExecutors.shared();
    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong handleSeq = new AtomicLong();
    private final File defaultDir;

    /**
     * Creates the registry.
     *
     * @param defaultDir working directory used when the caller passes no existing cwd
     */
    BackgroundShellTasks(File defaultDir) {
        this.defaultDir = defaultDir;
    }

    /** One background task: process handle, output buffer and callback; freed by kill/forget. */
    private static final class Task {
        /** Provider-side handle identifying this task to the host. */
        final long handleId;
        /** Guest shell process; destroyed by kill/shutdown. */
        final Process process;
        /** AIDL sink for output and completion events; may be null. */
        final ITerminalProviderCallback callback;
        /** Accumulated output, capped at {@code MAX_BACKGROUND_CHARS} to keep offsets stable. */
        final StringBuilder buffer = new StringBuilder();
        /** Written by the runner and pump threads, read by polled AIDL calls. */
        volatile boolean running = true;
        /** Valid only once {@code running} is false; -1 while unknown. */
        volatile int exitCode = -1;

        Task(long handleId, Process process, ITerminalProviderCallback callback) {
            this.handleId = handleId;
            this.process = process;
            this.callback = callback;
        }

        /** Appends output under the buffer cap, dropping anything beyond the cap. */
        synchronized void append(String text) {
            if (buffer.length() >= MAX_BACKGROUND_CHARS) {
                return;
            }
            buffer.append(text);
            if (buffer.length() > MAX_BACKGROUND_CHARS) {
                buffer.setLength(MAX_BACKGROUND_CHARS);
            }
        }

        /** Returns the output recorded after the offset, or the empty string when out of range. */
        synchronized String chunkFrom(long offset) {
            if (offset < 0 || offset >= buffer.length()) {
                return "";
            }
            return buffer.substring((int) offset);
        }
    }

    /** Launches {@code command} in the background and returns its handle, or -1 on failure. */
    long start(String command, String cwd, ITerminalProviderCallback callback) {
        File workingDir = (cwd != null && cwd.length() > 0) ? new File(cwd) : defaultDir;
        if (!workingDir.exists()) {
            workingDir = defaultDir;
        }
        long handle = handleSeq.incrementAndGet();
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
            pb.directory(workingDir);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            Task task = new Task(handle, process, callback);
            tasks.put(handle, task);
            executor.submit(() -> run(task));
            return handle;
        } catch (Exception e) {
            Log.e(TAG, "startShellBackground failed", e);
            tasks.remove(handle);
            notifyError(callback, e.getMessage() == null ? e.toString() : e.getMessage());
            return -1;
        }
    }

    /**
     * Polls the state and incremental output of a task.
     *
     * @param handleId the handle returned by {@link #start}
     * @param offset byte offset into the recorded output; pass the previous read length
     * @return the snapshot; unknown handles report not running with an unknown exit code
     */
    TerminalShellBackgroundState read(long handleId, long offset) {
        Task task = tasks.get(handleId);
        if (task == null) {
            return new TerminalShellBackgroundState(handleId, false, -1, "");
        }
        return new TerminalShellBackgroundState(handleId, task.running, task.exitCode,
                task.chunkFrom(offset));
    }

    /**
     * Removes the task and tears down its whole process tree, so orphaned descendants of
     * the shell cannot outlive the handle.
     *
     * @param handleId the handle returned by {@link #start}
     * @return true when a live task existed and was killed
     */
    boolean kill(long handleId) {
        Task task = tasks.remove(handleId);
        if (task == null) {
            return false;
        }
        task.running = false;
        ProcessKiller.killTree(task.process);
        return true;
    }

    /**
     * Removes the task without killing the process, for shells that intentionally
     * detached their own children.
     *
     * @param handleId the handle returned by {@link #start}
     */
    void forget(long handleId) {
        tasks.remove(handleId);
    }

    /** Destroys every still-running task; called when the provider service dies. */
    void shutdown() {
        for (Long handle : tasks.keySet().toArray(new Long[0])) {
            Task task = tasks.remove(handle);
            if (task != null) {
                task.running = false;
                ProcessKiller.killTree(task.process);
            }
        }
        Log.i(TAG, "background shell tasks shut down");
    }

    /**
     * Runs one task to completion on the shared executor. Output is drained by the
     * sanctioned daemon pump threads of {@link #pump} so the process never blocks on full
     * pipes; the exit code is recorded and the callback completed once both pumps finish.
     */
    private void run(Task task) {
        final Process process = task.process;
        Thread stdoutThread = pump(process.getInputStream(), task, "bg-stdout-" + task.handleId);
        Thread stderrThread = pump(process.getErrorStream(), task, "bg-stderr-" + task.handleId);
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
            } catch (RemoteException callbackGone) {
                Log.w(TAG, "background onComplete not delivered", callbackGone);
            }
        } catch (Exception e) {
            Log.e(TAG, "background task failed", e);
            task.exitCode = -1;
            task.running = false;
            notifyError(task.callback, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** One daemon pump thread per stream; blocking read is the documented drain pattern. */
    private static Thread pump(java.io.InputStream stream, Task task, String name) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String chunk = line + "\n";
                    task.append(chunk);
                    try {
                        task.callback.onOutput(chunk);
                    } catch (RemoteException callbackGone) {
                        Log.w(TAG, "background onOutput not delivered", callbackGone);
                    }
                }
            } catch (java.io.IOException streamClosed) {
                Log.w(TAG, "background stream closed", streamClosed);
            }
        }, name);
    }

    /** Delivers an error to the callback, tolerating a null callback or a dead binder. */
    private static void notifyError(ITerminalProviderCallback callback, String message) {
        if (callback == null) {
            return;
        }
        try {
            callback.onError(message);
        } catch (RemoteException callbackGone) {
            Log.w(TAG, "background onError not delivered", callbackGone);
        }
    }
}
