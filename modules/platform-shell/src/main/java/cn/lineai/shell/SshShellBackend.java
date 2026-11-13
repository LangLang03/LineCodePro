package cn.lineai.shell;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSH backend of the background shell registry: {@code setsid} launch, a remote temp log and
 * poll/kill scripts executed over the blocking remote executor. The {@code __BG_*__} markers
 * are the wire protocol between {@link #launchScript} and the poll/kill scripts.
 */
final class SshShellBackend {

    static final Pattern LAUNCH_PATTERN =
            Pattern.compile("__BG_PID__:(\\d+)\\s+__BG_LOG__:(\\S+)");
    /** Also used by the registry to strip exit markers from finished task output. */
    static final Pattern EXIT_PATTERN = Pattern.compile("__BG_EXIT__:(-?\\d+)");

    private static final int REMOTE_TIMEOUT_MS = 10_000;

    private final BackgroundShellRegistry registry;
    private final BackgroundShellRegistry.RemoteExecutor remoteExecutor;

    SshShellBackend(BackgroundShellRegistry registry,
                    BackgroundShellRegistry.RemoteExecutor remoteExecutor) {
        this.registry = registry;
        this.remoteExecutor = remoteExecutor;
    }

    void start(BackgroundTask task, String cwd) {
        String response = remoteExecutor.execute(launchScript(cwd, task.command),
                REMOTE_TIMEOUT_MS);
        Matcher matcher = LAUNCH_PATTERN.matcher(response == null ? "" : response);
        if (!matcher.find()) {
            throw new IllegalStateException("background launch failed: " + response);
        }
        synchronized (registry.lock) {
            task.pid = Integer.parseInt(matcher.group(1));
            task.logPath = matcher.group(2);
        }
    }

    void poll(BackgroundTask task) {
        final long requested;
        synchronized (registry.lock) {
            requested = task.offset;
        }
        String response;
        try {
            response = remoteExecutor.execute(pollScript(task, requested), REMOTE_TIMEOUT_MS);
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
        synchronized (registry.lock) {
            if (chunk.length() > 0) {
                registry.appendPolled(task, requested, chunk);
            }
            if (!running) {
                int exit = registry.findExitCode(task);
                registry.finish(task, exit == Integer.MIN_VALUE
                        ? BackgroundTask.Status.FAILED : BackgroundTask.Status.COMPLETED, exit);
            }
        }
    }

    boolean kill(BackgroundTask task) {
        try {
            String response = remoteExecutor.execute(killScript(task), REMOTE_TIMEOUT_MS);
            return response != null && response.contains("__BG_DEAD__");
        } catch (RuntimeException error) {
            return false;
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

    private static String pollScript(BackgroundTask task, long offset) {
        return "if kill -0 " + task.pid + " 2>/dev/null; then echo __BG_RUNNING__; "
                + "else echo __BG_DONE__; fi; tail -c +" + (offset + 1) + " "
                + shellQuote(task.logPath) + " 2>/dev/null || true";
    }

    private static String killScript(BackgroundTask task) {
        return "kill -TERM -- -" + task.pid + " 2>/dev/null; kill -TERM " + task.pid
                + " 2>/dev/null; sleep 0.3; kill -KILL -- -" + task.pid + " 2>/dev/null; "
                + "kill -KILL " + task.pid + " 2>/dev/null; "
                + "if kill -0 " + task.pid + " 2>/dev/null; then echo __BG_ALIVE__; "
                + "else echo __BG_DEAD__; fi";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
