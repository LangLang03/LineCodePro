package cn.lineai.shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import cn.lineai.tool.api.BackgroundTasks;

/**
 * App-scoped registry of background shell tasks started by the {@code shell} tool.
 *
 * <p>Three backends: a local {@link Process} handle ({@link LocalShellBackend}), an SSH launch
 * via {@code setsid} plus a remote temp log ({@link SshShellBackend}), and the terminal provider
 * AIDL v2 background handles. The registry is in-memory only: after an app restart the ids are
 * gone even though a remote process may still be running.</p>
 */
public final class BackgroundShellRegistry implements BackgroundTasks {

    /** One blocking remote command execution, wired to the active workspace by the app graph. */
    public interface RemoteExecutor {
        /**
         * Executes one command synchronously on the calling thread.
         *
         * @param command the command line to run on the remote host
         * @param timeoutMs timeout applied to the round trip
         * @return the captured output; may be {@code null} on transport failure
         */
        String execute(String command, int timeoutMs);
    }

    /** Terminal provider AIDL v2 background shell; wired to the connected provider, or null. */
    public interface ProviderBackground {

        /** Streams output chunks to the sink; returns the provider handle, or <= 0 on failure. */
        long start(String command, String cwd, OutputSink sink);

        /** Returns {running(1/0), exitCode(-1 when unknown)} and appends output after offset. */
        int[] read(long handle, long offset, StringBuilder chunkOut);

        /**
         * Terminates the provider-side process for a handle.
         *
         * @param handle handle returned by {@link #start}
         * @return {@code true} when the provider confirmed the termination
         */
        boolean kill(long handle);

        /** Frees the provider-side buffer for a finished handle. */
        void forget(long handle);
    }

    /** Output stream of a provider background task. */
    public interface OutputSink {
        /**
         * Receives one output chunk from a binder thread; the registry synchronizes on
         * its lock before touching task state.
         *
         * @param chunk decoded output bytes
         */
        void onChunk(String chunk);

        /**
         * Signals termination of the provider-side process.
         *
         * @param exitCode the remote exit code, or a negative value when unknown
         */
        void onComplete(int exitCode);
    }

    private static final int MAX_CHARS = 32_000;
    private static final long REMOTE_POLL_INTERVAL_MS = 3_000;
    /** Cadence of the blocking {@code output} wait; woken early by {@link #finish}. */
    private static final long BLOCK_WAIT_MS = 300;

    /** Single lock guarding every task; backends in this package synchronize on it too. */
    final Object lock = new Object();
    private final Map<String, BackgroundTask> tasks = new LinkedHashMap<>();
    private final LocalShellBackend localBackend;
    private final SshShellBackend sshBackend;
    private final ProviderBackground providerBackground;
    private final java.util.function.BooleanSupplier providerActive;
    private long counter;

    /**
     * Creates a registry with no provider support; remote tasks always use the SSH backend.
     *
     * @param remoteExecutor blocking remote command executor, or {@code null} to disable
     *                       remote tasks
     * @param providerBackground provider background bridge, or {@code null} when no
     *                           provider is available
     */
    public BackgroundShellRegistry(RemoteExecutor remoteExecutor,
                                   ProviderBackground providerBackground) {
        this(remoteExecutor, providerBackground, () -> false);
    }

    /**
     * @param providerActive whether a terminal provider (AIDL v2) is the active remote target;
     *                       when false, remote tasks run through the SSH-style temp-log backend.
     */
    public BackgroundShellRegistry(RemoteExecutor remoteExecutor,
                                   ProviderBackground providerBackground,
                                   java.util.function.BooleanSupplier providerActive) {
        this.localBackend = new LocalShellBackend(this);
        this.sshBackend = remoteExecutor == null ? null : new SshShellBackend(this, remoteExecutor);
        this.providerBackground = providerBackground;
        this.providerActive = providerActive;
    }

    /**
     * Starts a background task on the backend selected by the {@code remote} flag and the
     * active provider state, then hands execution to that backend. A backend start failure
     * records the error, marks the task failed and rethrows.
     *
     * @param command the command line to execute
     * @param cwd working directory (local path, remote path or provider home path)
     * @param remote {@code true} to route through the SSH or provider backend
     * @return the task id used for {@link #output} and {@link #kill}
     * @throws IllegalArgumentException when the command is empty
     * @throws RuntimeException when the selected backend fails to start the task
     */
    @Override public String start(String command, String cwd, boolean remote) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        BackgroundTask.Backend backend = !remote ? BackgroundTask.Backend.LOCAL
                : providerActive.getAsBoolean() && providerBackground != null
                ? BackgroundTask.Backend.PROVIDER : BackgroundTask.Backend.SSH;
        BackgroundTask task;
        synchronized (lock) {
            String id = "bg-" + (++counter);
            task = new BackgroundTask(id, command, backend);
            tasks.put(id, task);
        }
        try {
            if (backend == BackgroundTask.Backend.LOCAL) {
                localBackend.start(task, cwd);
            } else if (backend == BackgroundTask.Backend.SSH) {
                sshBackend.start(task, cwd);
            } else {
                startProvider(task, cwd);
            }
        } catch (RuntimeException error) {
            synchronized (lock) {
                appendOutput(task, message(error));
                finish(task, BackgroundTask.Status.FAILED, Integer.MIN_VALUE);
            }
            throw error;
        }
        return task.id;
    }

    /** Registers the streaming sink, asks the provider to start, and records the handle. */
    private void startProvider(BackgroundTask task, String cwd) {
        OutputSink sink = new OutputSink() {
            @Override public void onChunk(String chunk) {
                synchronized (lock) {
                    appendOutput(task, chunk);
                    // Streamed bytes count towards the consumed offset, so the next
                    // readShellBackground(offset) only returns what was not delivered yet.
                    task.offset += chunk == null ? 0 : chunk.length();
                }
            }

            @Override public void onComplete(int exitCode) {
                synchronized (lock) {
                    if (task.status == BackgroundTask.Status.RUNNING) {
                        finish(task, exitCode < 0 ? BackgroundTask.Status.FAILED
                                : BackgroundTask.Status.COMPLETED, exitCode);
                    }
                }
            }
        };
        synchronized (lock) {
            task.sink = sink;
        }
        long handle = providerBackground.start(task.command, cwd == null ? "" : cwd, sink);
        if (handle <= 0) {
            throw new IllegalStateException("provider refused to start the background task");
        }
        synchronized (lock) {
            task.handle = handle;
        }
    }

    /**
     * Returns a status report for one task, optionally blocking until it finishes or the
     * timeout elapses. Blocking waits refresh the backend aggressively; a single refresh
     * also precedes a non-blocking read so remote state is current.
     *
     * @param id task id returned by {@link #start}
     * @param block {@code true} to wait for completion within the timeout
     * @param timeoutMs upper bound on the blocking wait, in milliseconds
     * @return the status line and the collected output, or an unknown-id message
     */
    @Override public String output(String id, boolean block, int timeoutMs) {
        BackgroundTask task;
        synchronized (lock) {
            task = tasks.get(id);
            if (task == null) return "Unknown background task: " + id;
        }
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        // One observation even for non-blocking reads, so remote/provider state is current.
        refresh(task, true);
        while (block) {
            synchronized (lock) {
                if (task.status != BackgroundTask.Status.RUNNING
                        || System.currentTimeMillis() >= deadline) {
                    break;
                }
                long waitMs = Math.min(BLOCK_WAIT_MS, deadline - System.currentTimeMillis());
                try {
                    lock.wait(Math.max(1, waitMs));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            refresh(task, true);
        }
        synchronized (lock) {
            if (task.status != BackgroundTask.Status.RUNNING) {
                task.reported = true;
            }
            return describe(task);
        }
    }

    /**
     * Kills a running task through its backend, or reports the recorded terminal state.
     * The task is marked reported either way so {@link #drainCompleted} stays silent.
     *
     * @param id task id returned by {@link #start}
     * @return a user-facing result line including the output tail when the task kept running
     */
    @Override public String kill(String id) {
        BackgroundTask task;
        synchronized (lock) {
            task = tasks.get(id);
            if (task == null) return "Unknown background task: " + id;
            if (task.status != BackgroundTask.Status.RUNNING) {
                task.reported = true;
                return "Task " + id + " already " + task.status.label()
                        + (task.exitCode == Integer.MIN_VALUE ? ""
                        : " (exit " + task.exitCode + ")");
            }
        }
        boolean stopped;
        switch (task.backend) {
            case SSH:
                stopped = sshBackend.kill(task);
                break;
            case PROVIDER:
                stopped = providerBackground != null && providerBackground.kill(task.handle);
                break;
            default:
                localBackend.stop(task);
                stopped = true;
        }
        synchronized (lock) {
            if (stopped && task.status == BackgroundTask.Status.RUNNING) {
                finish(task, BackgroundTask.Status.KILLED, Integer.MIN_VALUE);
            }
            task.reported = true;
            return "Task " + id + (stopped ? " killed" : " still running; kill attempt failed")
                    + describeTail(task);
        }
    }

    /**
     * Collects one notice per completed task that has not been reported yet, refreshing
     * running tasks on the way so remotely finished tasks are not missed.
     *
     * @return the unread completion notices, in task creation order
     */
    @Override public List<String> drainCompleted() {
        List<String> notices = new ArrayList<>();
        synchronized (lock) {
            for (BackgroundTask task : tasks.values()) {
                if (task.status == BackgroundTask.Status.RUNNING) {
                    refreshQuietly(task);
                }
                if (task.status == BackgroundTask.Status.COMPLETED && !task.reported) {
                    task.reported = true;
                    notices.add("Background shell task " + task.id + " finished"
                            + (task.exitCode == Integer.MIN_VALUE ? ""
                            : " (exit " + task.exitCode + ")")
                            + ". Command: " + task.command
                            + ". Its output was not read yet - call shell_output with id=\""
                            + task.id + "\" to view it.");
                }
            }
        }
        return notices;
    }

    /**
     * Kills every still running local task. Remote tasks are left alone: their processes
     * survive the app and the registry cannot reconnect after a restart.
     */
    @Override public void shutdown() {
        synchronized (lock) {
            for (BackgroundTask task : tasks.values()) {
                if (task.status == BackgroundTask.Status.RUNNING
                        && task.backend == BackgroundTask.Backend.LOCAL) {
                    localBackend.stop(task);
                    finish(task, BackgroundTask.Status.KILLED, Integer.MIN_VALUE);
                }
            }
        }
    }

    /**
     * Updates one task's state from its backend. Blocking waits poll aggressively; passive
     * drains rate-limit SSH/provider round trips.
     */
    private void refresh(BackgroundTask task, boolean aggressive) {
        synchronized (lock) {
            if (task.status != BackgroundTask.Status.RUNNING) return;
            if (task.backend == BackgroundTask.Backend.LOCAL) {
                localBackend.refresh(task);
                return;
            }
            long elapsed = System.currentTimeMillis() - task.lastPoll;
            if (!aggressive && elapsed < REMOTE_POLL_INTERVAL_MS) {
                return;
            }
            task.lastPoll = System.currentTimeMillis();
        }
        if (task.backend == BackgroundTask.Backend.SSH) {
            sshBackend.poll(task);
        } else if (providerBackground != null) {
            pollProvider(task);
        }
    }

    /** Rate-limited refresh that keeps poll failures from breaking the drain loop. */
    private void refreshQuietly(BackgroundTask task) {
        try {
            refresh(task, false);
        } catch (RuntimeException ignored) {
            // A failed poll keeps the task running; the next drain retries.
            System.err.println("background poll failed for " + task.id + ": " + ignored);
        }
    }

    /** One AIDL read of new output and liveness; completes the task when not running. */
    private void pollProvider(BackgroundTask task) {
        if (providerBackground == null) return;
        final long requested;
        synchronized (lock) {
            requested = task.offset;
        }
        StringBuilder chunk = new StringBuilder();
        int[] state = providerBackground.read(task.handle, requested, chunk);
        if (state == null) return;
        synchronized (lock) {
            if (chunk.length() > 0) {
                appendPolled(task, requested, chunk.toString());
            }
            if (state[0] == 0) {
                int exit = state[1];
                finish(task, exit < 0 ? BackgroundTask.Status.FAILED
                        : BackgroundTask.Status.COMPLETED, exit);
            }
        }
    }

    /** Called by the backends for polled bytes; reconciles against streamed delivery. */
    void appendPolled(BackgroundTask task, long requested, String chunk) {
        // A concurrent streamed callback or an overlapping aggressive poll may already have
        // delivered part of that range while the round trip was in flight; only the still-
        // undelivered suffix is appended, and the consumed offset never overshoots (an
        // overshoot loses all further polled output).
        long overlap = task.offset - requested;
        if (overlap < 0) {
            overlap = 0;
        }
        if (overlap < chunk.length()) {
            appendOutput(task, chunk.substring((int) overlap));
        }
        if (task.offset < requested + chunk.length()) {
            task.offset = requested + chunk.length();
        }
    }

    /** Ring-buffer append; caller holds {@link #lock}. */
    void appendOutput(BackgroundTask task, String chunk) {
        task.output.append(chunk);
        if (task.output.length() > MAX_CHARS) {
            task.output.delete(0, task.output.length() - MAX_CHARS);
            task.truncated = true;
        }
    }

    /** Terminal state transition; caller holds {@link #lock} except the start-failure path. */
    void finish(BackgroundTask task, BackgroundTask.Status status, int exitCode) {
        task.status = status;
        task.exitCode = exitCode;
        if (task.backend == BackgroundTask.Backend.PROVIDER && providerBackground != null
                && task.handle > 0) {
            providerBackground.forget(task.handle);
        }
        lock.notifyAll();
    }

    /** Last {@code __BG_EXIT__} marker in the buffered output; MIN_VALUE when absent. */
    int findExitCode(BackgroundTask task) {
        Matcher matcher = SshShellBackend.EXIT_PATTERN.matcher(task.output);
        int exit = Integer.MIN_VALUE;
        while (matcher.find()) {
            exit = Integer.parseInt(matcher.group(1));
        }
        return exit;
    }

    /** Builds the user-facing status line followed by the task output. */
    private String describe(BackgroundTask task) {
        StringBuilder out = new StringBuilder("Task ").append(task.id);
        if (task.status == BackgroundTask.Status.RUNNING) {
            out.append(" (running, started ").append(elapsed(task.createdAt)).append(" ago)");
        } else if (task.status == BackgroundTask.Status.COMPLETED) {
            out.append(" finished (exit ").append(task.exitCode).append(')');
        } else {
            out.append(' ').append(task.status.label());
        }
        String text = displayText(task);
        out.append('\n').append(text.isEmpty() ? "(no output)" : text);
        return out.toString();
    }

    /** Output tail (at most 2000 chars) appended to kill results; empty when no output. */
    private String describeTail(BackgroundTask task) {
        String text = displayText(task);
        return text.isEmpty() ? "" : "\n" + (text.length() > 2000
                ? text.substring(text.length() - 2000) : text);
    }

    /** Buffered output with exit markers stripped and a truncation notice appended. */
    private String displayText(BackgroundTask task) {
        String text = task.output.toString();
        if (task.status == BackgroundTask.Status.COMPLETED
                || task.status == BackgroundTask.Status.FAILED) {
            text = SshShellBackend.EXIT_PATTERN.matcher(text).replaceAll("").trim();
        }
        return task.truncated ? text + "\n… truncated" : text;
    }

    /** Human-readable elapsed time in seconds or minutes. */
    private static String elapsed(long since) {
        long seconds = Math.max(0, (System.currentTimeMillis() - since) / 1000);
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    /** Best available message for an exception, falling back to {@code toString}. */
    private static String message(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
