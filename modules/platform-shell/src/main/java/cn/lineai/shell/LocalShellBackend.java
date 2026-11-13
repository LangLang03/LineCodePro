package cn.lineai.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Local {@link Process} backend of the background shell registry.
 *
 * <p>All state transitions run under the registry lock. The one-shot reader thread is the
 * documented exception to the no-raw-threads rule (verify plugin whitelist): a blocking drain
 * of a process pipe cannot time out without it, and poll-based draining would stall tasks
 * whose output nobody observes once the 64 KB pipe buffer fills.</p>
 */
final class LocalShellBackend {

    private final BackgroundShellRegistry registry;

    LocalShellBackend(BackgroundShellRegistry registry) {
        this.registry = registry;
    }

    /**
     * Spawns the command through {@code sh -c} and starts the daemon reader thread that
     * drains the merged stdout/stderr pipe. Start failures mark the task failed instead of
     * propagating.
     *
     * @param task task to run; the process and reader are stored under the registry lock
     * @param cwd working directory; ignored when empty or not an existing directory
     */
    void start(BackgroundTask task, String cwd) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", task.command);
            if (cwd != null && !cwd.isEmpty()) {
                File dir = new File(cwd);
                if (dir.isDirectory()) builder.directory(dir);
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            Thread reader = new Thread(() -> drain(task, process), "bg-shell-" + task.id);
            reader.setDaemon(true);
            synchronized (registry.lock) {
                task.process = process;
                task.reader = reader;
            }
            reader.start();
        } catch (Exception error) {
            synchronized (registry.lock) {
                registry.appendOutput(task, message(error));
                registry.finish(task, BackgroundTask.Status.FAILED, Integer.MIN_VALUE);
            }
        }
    }

    /**
     * Reader-thread body: streams output into the registry ring buffer until the process
     * exits, then applies the kill-flag-over-exit-code rule documented at the call sites.
     */
    private void drain(BackgroundTask task, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                synchronized (registry.lock) {
                    registry.appendOutput(task, new String(buffer, 0, count));
                }
            }
            int exit = process.waitFor();
            synchronized (registry.lock) {
                if (task.status == BackgroundTask.Status.RUNNING) {
                    // A SIGKILL surfaces as a normal exit (e.g. 137); the kill flag decides.
                    registry.finish(task,
                            task.killRequested ? BackgroundTask.Status.KILLED
                                    : BackgroundTask.Status.COMPLETED,
                            task.killRequested ? Integer.MIN_VALUE : exit);
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            synchronized (registry.lock) {
                if (task.status == BackgroundTask.Status.RUNNING) {
                    // A broken stream right after a kill request is the kill itself taking
                    // effect: isAlive() can still report true while the SIGKILL is being
                    // reaped, so the kill flag always wins over the FAILED escalation.
                    if (task.killRequested || task.process == null
                            || !task.process.isAlive()) {
                        registry.finish(task, BackgroundTask.Status.KILLED, Integer.MIN_VALUE);
                    } else {
                        registry.appendOutput(task, "\n" + message(error));
                        registry.finish(task, BackgroundTask.Status.FAILED, Integer.MIN_VALUE);
                    }
                }
            }
        }
    }

    /**
     * Completes a finished task if the reader thread has also finished draining; a still
     * running process or a live reader leaves the task untouched.
     *
     * @param task task to reconcile; caller holds the registry lock
     */
    void refresh(BackgroundTask task) {
        Process process = task.process;
        if (process == null) {
            registry.finish(task, BackgroundTask.Status.FAILED, Integer.MIN_VALUE);
            return;
        }
        if (process.isAlive()) return;
        // The reader thread may still be draining the last bytes; completion waits for it.
        Thread reader = task.reader;
        if (reader != null && reader.isAlive()) return;
        if (task.killRequested) {
            registry.finish(task, BackgroundTask.Status.KILLED, Integer.MIN_VALUE);
            return;
        }
        registry.finish(task, BackgroundTask.Status.COMPLETED, tryExitValue(process));
    }

    /**
     * Kills the direct child and every descendant found under {@code /proc}, because
     * {@code sh -c} spawns grandchildren that {@code destroyForcibly()} does not reach.
     *
     * @param task task to kill; the kill flag is set first so the reader thread classifies
     *             the resulting exit as {@code KILLED}
     */
    void stop(BackgroundTask task) {
        synchronized (registry.lock) {
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

    /** Best available message for an exception, falling back to {@code toString}. */
    private static String message(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    /** Exit value when available; {@link Integer#MIN_VALUE} when the process still runs. */
    private static int tryExitValue(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException error) {
            return Integer.MIN_VALUE;
        }
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
            // pid reflection is best-effort; without it only the direct child is killed.
            System.err.println("process pid reflection unavailable: " + ignored);
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
                    continue; // /proc entries are not all numeric pids
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

    /** Parent pid parsed from {@code /proc/<pid>/stat}; -1 for unreadable entries. */
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
            return -1; // unreadable or vanished entry; skip it
        }
    }

    /** Best-effort SIGKILL through a detached {@code kill} process; failures are logged only. */
    private static void execKill(int pid) {
        if (pid <= 0) return;
        try {
            new ProcessBuilder("sh", "-c", "kill -KILL " + pid + " 2>/dev/null").start();
        } catch (Exception ignored) {
            // The pid may have exited between the /proc walk and the kill; nothing to do.
            System.err.println("kill -KILL failed for pid " + pid + ": " + ignored);
        }
    }
}
