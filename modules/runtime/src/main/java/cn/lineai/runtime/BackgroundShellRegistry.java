package cn.lineai.runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.lineai.tool.api.BackgroundTasks;

/**
 * App-scoped registry of background shell tasks started by the {@code shell} tool.
 *
 * <p>Three backends: a local {@link Process} handle, an SSH launch via {@code setsid} plus a
 * remote temp log polled over the existing blocking remote executor, and the terminal provider
 * AIDL v2 background handles. The registry is in-memory only: after an app restart the ids are
 * gone even though a remote process may still be running.</p>
 */
public final class BackgroundShellRegistry implements BackgroundTasks {

    /** One blocking remote command execution, wired to the active workspace by the app graph. */
    public interface RemoteExecutor {
        String execute(String command, int timeoutMs);
    }

    /** Terminal provider AIDL v2 background shell; wired to the connected provider, or null. */
    public interface ProviderBackground {

        /** Streams output chunks to the sink; returns the provider handle, or <= 0 on failure. */
        long start(String command, String cwd, OutputSink sink);

        /** Returns {running(1/0), exitCode(-1 when unknown)} and appends output after offset. */
        int[] read(long handle, long offset, StringBuilder chunkOut);

        boolean kill(long handle);

        /** Frees the provider-side buffer for a finished handle. */
        void forget(long handle);
    }

    /** Output stream of a provider background task. */
    public interface OutputSink {
        void onChunk(String chunk);

        void onComplete(int exitCode);
    }

    private static final int MAX_CHARS = 32_000;
    private static final int REMOTE_TIMEOUT_MS = 10_000;
    private static final long REMOTE_POLL_INTERVAL_MS = 3_000;
    private static final long BLOCK_POLL_SLEEP_MS = 300;

    private static final Pattern LAUNCH_PATTERN =
            Pattern.compile("__BG_PID__:(\\d+)\\s+__BG_LOG__:(\\S+)");
    private static final Pattern EXIT_PATTERN = Pattern.compile("__BG_EXIT__:(-?\\d+)");

    private final RemoteExecutor remoteExecutor;
    private final ProviderBackground providerBackground;
    private final java.util.function.BooleanSupplier providerActive;
    private final Object lock = new Object();
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private long counter;

    private static final int BACKEND_LOCAL = 0;
    private static final int BACKEND_SSH = 1;
    private static final int BACKEND_PROVIDER = 2;

    private static final class Task {
        final String id;
        final String command;
        final int backend;
        final long createdAt = System.currentTimeMillis();
        String status = "running";
        int exitCode = Integer.MIN_VALUE;
        final StringBuilder output = new StringBuilder();
        boolean truncated;
        boolean reported;
        volatile boolean killRequested;
        // LOCAL
        Process process;
        Thread reader;
        // SSH
        int pid;
        String logPath;
        long offset;
        long lastPoll;
        // PROVIDER
        long handle;
        OutputSink sink;

        Task(String id, String command, int backend) {
            this.id = id;
            this.command = command;
            this.backend = backend;
        }
    }

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
        this.remoteExecutor = remoteExecutor;
        this.providerBackground = providerBackground;
        this.providerActive = providerActive;
    }

    @Override public String start(String command, String cwd, boolean remote) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        int backend = !remote ? BACKEND_LOCAL
                : providerActive.getAsBoolean() && providerBackground != null
                ? BACKEND_PROVIDER : BACKEND_SSH;
        Task task;
        synchronized (lock) {
            String id = "bg-" + (++counter);
            task = new Task(id, command, backend);
            tasks.put(id, task);
        }
        try {
            if (backend == BACKEND_LOCAL) {
                startLocal(task, cwd);
            } else if (backend == BACKEND_SSH) {
                startSsh(task, cwd);
            } else {
                startProvider(task, cwd);
            }
        } catch (RuntimeException error) {
            synchronized (lock) {
                appendOutput(task, message(error));
                task.status = "failed";
            }
            throw error;
        }
        return task.id;
    }

    private void startProvider(Task task, String cwd) {
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
                    if ("running".equals(task.status)) {
                        finish(task, exitCode < 0 ? "failed" : "completed", exitCode);
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

    private void startLocal(Task task, String cwd) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", task.command);
            if (cwd != null && !cwd.isEmpty()) {
                File dir = new File(cwd);
                if (dir.isDirectory()) builder.directory(dir);
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            Thread reader = new Thread(() -> drainLocal(task, process), "bg-shell-" + task.id);
            reader.setDaemon(true);
            synchronized (lock) {
                task.process = process;
                task.reader = reader;
            }
            reader.start();
        } catch (Exception error) {
            synchronized (lock) {
                appendOutput(task, message(error));
                task.status = "failed";
            }
        }
    }

    private void drainLocal(Task task, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                synchronized (lock) {
                    appendOutput(task, new String(buffer, 0, count));
                }
            }
            int exit = process.waitFor();
            synchronized (lock) {
                if ("running".equals(task.status)) {
                    // A SIGKILL surfaces as a normal exit (e.g. 137); the kill flag decides.
                    finish(task, task.killRequested ? "killed" : "completed",
                            task.killRequested ? Integer.MIN_VALUE : exit);
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            synchronized (lock) {
                if ("running".equals(task.status)) {
                    boolean alive = task.process != null && task.process.isAlive();
                    if (!alive) {
                        // The stream broke because the task was killed; keep that status.
                        finish(task, "killed", Integer.MIN_VALUE);
                    } else {
                        appendOutput(task, "\n" + message(error));
                        finish(task, "failed", Integer.MIN_VALUE);
                    }
                }
            }
        }
    }

    private void startSsh(Task task, String cwd) {
        String response = remoteExecutor.execute(launchScript(cwd, task.command), REMOTE_TIMEOUT_MS);
        Matcher matcher = LAUNCH_PATTERN.matcher(response == null ? "" : response);
        if (!matcher.find()) {
            throw new IllegalStateException("background launch failed: " + response);
        }
        synchronized (lock) {
            task.pid = Integer.parseInt(matcher.group(1));
            task.logPath = matcher.group(2);
        }
    }

    /** cd + setsid launch; the child writes its own exit marker into the temp log. */
    private static String launchScript(String cwd, String command) {
        StringBuilder out = new StringBuilder();
        if (cwd != null && !cwd.isEmpty()) {
            out.append("cd ").append(shellQuote(cwd)).append(" && ");
        }
        out.append("LOG=$(mktemp) && setsid sh -c ")
                .append(shellQuote(command + "; echo __BG_EXIT__:$?"))
                .append(" >\"$LOG\" 2>&1 </dev/null & echo \"__BG_PID__:$! __BG_LOG__:$LOG\"");
        return out.toString();
    }

    private static String pollScript(Task task) {
        return "if kill -0 " + task.pid + " 2>/dev/null; then echo __BG_RUNNING__; "
                + "else echo __BG_DONE__; fi; tail -c +" + (task.offset + 1) + " "
                + shellQuote(task.logPath) + " 2>/dev/null || true";
    }

    private static String killScript(Task task) {
        return "kill -TERM -- -" + task.pid + " 2>/dev/null; kill -TERM " + task.pid
                + " 2>/dev/null; sleep 0.3; kill -KILL -- -" + task.pid + " 2>/dev/null; "
                + "kill -KILL " + task.pid + " 2>/dev/null; "
                + "if kill -0 " + task.pid + " 2>/dev/null; then echo __BG_ALIVE__; "
                + "else echo __BG_DEAD__; fi";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    @Override public String output(String id, boolean block, int timeoutMs) {
        Task task;
        synchronized (lock) {
            task = tasks.get(id);
            if (task == null) return "Unknown background task: " + id;
        }
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        // One observation even for non-blocking reads, so remote/provider state is current.
        refresh(task, true);
        while (true) {
            synchronized (lock) {
                if (!"running".equals(task.status) || !block
                        || System.currentTimeMillis() >= deadline) {
                    break;
                }
            }
            try {
                Thread.sleep(BLOCK_POLL_SLEEP_MS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
            refresh(task, true);
        }
        synchronized (lock) {
            if (!"running".equals(task.status)) {
                task.reported = true;
            }
            return describe(task);
        }
    }

    @Override public String kill(String id) {
        Task task;
        synchronized (lock) {
            task = tasks.get(id);
            if (task == null) return "Unknown background task: " + id;
            if (!"running".equals(task.status)) {
                task.reported = true;
                return "Task " + id + " already " + task.status
                        + (task.exitCode == Integer.MIN_VALUE ? ""
                        : " (exit " + task.exitCode + ")");
            }
        }
        boolean stopped;
        switch (task.backend) {
            case BACKEND_SSH:
                stopped = remoteKill(task);
                break;
            case BACKEND_PROVIDER:
                stopped = providerBackground != null && providerBackground.kill(task.handle);
                break;
            default:
                stopLocal(task);
                stopped = true;
        }
        synchronized (lock) {
            if (stopped && "running".equals(task.status)) {
                finish(task, "killed", Integer.MIN_VALUE);
            }
            task.reported = true;
            return "Task " + id + (stopped ? " killed" : " still running; kill attempt failed")
                    + describeTail(task);
        }
    }

    private boolean remoteKill(Task task) {
        try {
            String response = remoteExecutor.execute(killScript(task), REMOTE_TIMEOUT_MS);
            return response != null && response.contains("__BG_DEAD__");
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void stopLocal(Task task) {
        synchronized (lock) {
            task.killRequested = true;
        }
        Process process = task.process;
        if (process == null) return;
        process.destroyForcibly();
        int pid = processPid(process);
        if (pid > 0) {
            for (int descendant : collectDescendants(pid)) {
                execKill(descendant);
            }
            execKill(pid);
        }
    }

    @Override public List<String> drainCompleted() {
        List<String> notices = new ArrayList<>();
        synchronized (lock) {
            for (Task task : tasks.values()) {
                if ("running".equals(task.status)) {
                    refreshQuietly(task);
                }
                if ("completed".equals(task.status) && !task.reported) {
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

    @Override public void shutdown() {
        synchronized (lock) {
            for (Task task : tasks.values()) {
                if ("running".equals(task.status) && task.backend == BACKEND_LOCAL) {
                    stopLocal(task);
                    finish(task, "killed", Integer.MIN_VALUE);
                }
            }
        }
    }

    /**
     * Updates one task's state from its backend. Blocking waits poll aggressively; passive
     * drains rate-limit SSH/provider round trips.
     */
    private void refresh(Task task, boolean aggressive) {
        synchronized (lock) {
            if (!"running".equals(task.status)) return;
            if (task.backend == BACKEND_LOCAL) {
                refreshLocal(task);
                return;
            }
            long elapsed = System.currentTimeMillis() - task.lastPoll;
            if (!aggressive && elapsed < REMOTE_POLL_INTERVAL_MS) {
                return;
            }
            task.lastPoll = System.currentTimeMillis();
        }
        if (task.backend == BACKEND_SSH) {
            pollSsh(task);
        } else if (providerBackground != null) {
            pollProvider(task);
        }
    }

    private void refreshQuietly(Task task) {
        try {
            refresh(task, false);
        } catch (RuntimeException ignored) {
            // A failed poll keeps the task running; the next drain retries.
        }
    }

    private void refreshLocal(Task task) {
        Process process = task.process;
        if (process == null) {
            finish(task, "failed", Integer.MIN_VALUE);
            return;
        }
        if (process.isAlive()) return;
        // The reader thread may still be draining the last bytes; completion waits for it.
        Thread reader = task.reader;
        if (reader != null && reader.isAlive()) return;
        if (task.killRequested) {
            finish(task, "killed", Integer.MIN_VALUE);
            return;
        }
        finish(task, "completed", tryExitValue(process));
    }

    private void pollSsh(Task task) {
        String response;
        try {
            response = remoteExecutor.execute(pollScript(task), REMOTE_TIMEOUT_MS);
        } catch (RuntimeException error) {
            return; // transient transport failure; retry on the next poll
        }
        if (response == null) return;
        boolean running;
        String chunk;
        int newline = response.indexOf('\n');
        if (newline < 0) {
            running = response.contains("__BG_RUNNING__");
            chunk = "";
        } else {
            running = response.substring(0, newline).contains("__BG_RUNNING__");
            chunk = response.substring(newline + 1);
        }
        synchronized (lock) {
            if (chunk.length() > 0) {
                appendOutput(task, chunk);
                task.offset += chunk.length();
            }
            if (!running) {
                int exit = findExitCode(task);
                finish(task, exit == Integer.MIN_VALUE ? "failed" : "completed", exit);
            }
        }
    }

    private void pollProvider(Task task) {
        if (providerBackground == null) return;
        StringBuilder chunk = new StringBuilder();
        int[] state = providerBackground.read(task.handle, task.offset, chunk);
        if (state == null) return;
        synchronized (lock) {
            if (chunk.length() > 0) {
                appendOutput(task, chunk.toString());
                task.offset += chunk.length();
            }
            if (state[0] == 0) {
                int exit = state[1];
                finish(task, exit < 0 ? "failed" : "completed", exit);
            }
        }
    }

    private void appendOutput(Task task, String chunk) {
        task.output.append(chunk);
        if (task.output.length() > MAX_CHARS) {
            task.output.delete(0, task.output.length() - MAX_CHARS);
            task.truncated = true;
        }
    }

    private void finish(Task task, String status, int exitCode) {
        task.status = status;
        task.exitCode = exitCode;
        if (task.backend == BACKEND_PROVIDER && providerBackground != null && task.handle > 0) {
            providerBackground.forget(task.handle);
        }
        lock.notifyAll();
    }

    private int findExitCode(Task task) {
        Matcher matcher = EXIT_PATTERN.matcher(task.output);
        int exit = Integer.MIN_VALUE;
        while (matcher.find()) {
            exit = Integer.parseInt(matcher.group(1));
        }
        return exit;
    }

    private String describe(Task task) {
        StringBuilder out = new StringBuilder("Task ").append(task.id);
        if ("running".equals(task.status)) {
            out.append(" (running, started ").append(elapsed(task.createdAt)).append(" ago)");
        } else if ("completed".equals(task.status)) {
            out.append(" finished (exit ").append(task.exitCode).append(')');
        } else {
            out.append(' ').append(task.status);
        }
        String text = displayText(task);
        out.append('\n').append(text.isEmpty() ? "(no output)" : text);
        return out.toString();
    }

    private String describeTail(Task task) {
        String text = displayText(task);
        return text.isEmpty() ? "" : "\n" + (text.length() > 2000
                ? text.substring(text.length() - 2000) : text);
    }

    private static String displayText(Task task) {
        String text = task.output.toString();
        if ("completed".equals(task.status) || "failed".equals(task.status)) {
            text = EXIT_PATTERN.matcher(text).replaceAll("").trim();
        }
        return task.truncated ? text + "\n… truncated" : text;
    }

    private static String elapsed(long since) {
        long seconds = Math.max(0, (System.currentTimeMillis() - since) / 1000);
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private static int tryExitValue(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException error) {
            return Integer.MIN_VALUE;
        }
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    /** Android's Process does not expose pid(); reflect the internal field like the provider. */
    private static int processPid(Process process) {
        try {
            Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            Object value = field.get(process);
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Long) return (int) (long) (Long) value;
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /** /proc walk collecting all descendant pids of rootPid (same approach as the provider). */
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
                int ppid = ppidOf(dir);
                if (ppid > 0 && pid != rootPid) ppidByPid.put(pid, ppid);
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

    private static int ppidOf(File procDir) {
        File stat = new File(procDir, "stat");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(stat), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) return -1;
            int close = line.lastIndexOf(')');
            if (close < 0) return -1;
            String[] fields = line.substring(close + 1).trim().split("\\s+");
            return fields.length < 2 ? -1 : Integer.parseInt(fields[1]);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static void execKill(int pid) {
        if (pid <= 0) return;
        try {
            new ProcessBuilder("sh", "-c", "kill -KILL " + pid + " 2>/dev/null").start();
        } catch (Exception ignored) {
        }
    }
}
