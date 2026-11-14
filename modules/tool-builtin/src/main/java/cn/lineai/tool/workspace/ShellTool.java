package cn.lineai.tool.workspace;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import cn.lineai.tool.api.BackgroundTasks;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Runs one shell command with the workspace as working directory; local, SSH and terminal
 * provider targets share this single tool name and are routed by the execution mode.
 *
 * <p>{@code timeout_ms} caps blocking calls; {@code background=true} starts the command without
 * waiting, returns a task id, and hands observation to {@code shell_output} / {@code shell_kill}.</p>
 *
 * <p>Local execution follows the sanctioned reader-thread pattern (shared with
 * {@code LocalShellBackend}): a daemon thread drains the merged process pipe while the caller
 * waits. Blocking process pipes cannot be drained under a timeout without a dedicated reader
 * thread — {@code Process.waitFor(timeout)} does not consume output, so a command that fills the
 * pipe buffer would deadlock until the timeout kills it, losing all output. Do not replace the
 * reader thread with synchronous reading between waits.</p>
 */
public final class ShellTool implements Tool {

    /** Model-facing tool name; referenced by {@link LocalToolPolicy} and {@link BackgroundTasks}. */
    public static final String NAME = "shell";

    /** Upper bound on returned command output, applied by the reader and {@link #cap(String)}. */
    static final int MAX_CHARS = 32_000;
    /** Blocking-call timeout applied when the model does not supply {@code timeout_ms}. */
    static final long DEFAULT_TIMEOUT_MS = 60_000;
    /** {@code shell_output} blocking timeout applied when the model omits {@code timeout_ms}. */
    static final long DEFAULT_OUTPUT_TIMEOUT_MS = 30_000;
    /** Lower clamp for {@code timeout_ms}; smaller values would race process startup. */
    static final long MIN_TIMEOUT_MS = 1_000;
    /** Upper clamp for {@code timeout_ms}; keeps an unresponsive command from pinning a session. */
    static final long MAX_TIMEOUT_MS = 600_000;

    /** {@inheritDoc} */
    @Override public String name() { return NAME; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Run a shell command with the workspace as working directory. Set background=true "
                + "to start it without waiting; the returned task id works with shell_output and "
                + "shell_kill.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("command", "Shell command")
                .string("cwd", "Workspace-relative directory")
                .string("reason", "Why this command is needed")
                .number("timeout_ms", "Max wait in milliseconds (default 60000, max 600000)")
                .bool("background", "Start in the background and return a task id immediately "
                        + "instead of waiting")
                .required("command").build();
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return true; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        String cwd = arguments.optString("cwd", "").trim();
        String command = arguments.optString("command", "");
        boolean background = arguments.optBoolean("background", false);
        String action = (cwd.isEmpty() ? command : cwd + "\n" + command)
                + (background ? "\n[background]" : "");
        return new ToolPresentation("Terminal", ToolIcon.TERMINAL,
                action, ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Routing: {@code background=true} delegates to {@link BackgroundTasks}; otherwise remote
     * workspaces run through {@link ToolContext#remoteExecute} and local workspaces through
     * {@link #runLocal} with the resolved working directory.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String command = arguments.optString("command", "").trim();
        if (command.isEmpty()) return ToolResult.failure("", "command must not be empty");
        if (!context.mutationAllowed()) return ToolResult.failure("", "Mutation not allowed");
        if (arguments.optBoolean("background", false)) {
            return startBackground(context, command, arguments.optString("cwd", "").trim());
        }
        int timeoutMs = (int) clampTimeout(arguments, DEFAULT_TIMEOUT_MS);
        if (context.remoteWorkspace()) {
            return executeRemote(context, command, arguments.optString("cwd", "").trim(),
                    timeoutMs);
        }
        try {
            File cwd = context.resolvePath(arguments.optString("cwd", "."));
            if (!cwd.isDirectory()) return ToolResult.failure("", "Not a directory: " + cwd);
            return runLocal(command, cwd, timeoutMs);
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }

    /**
     * Runs the command locally under {@code sh -c}, applying the sanctioned reader-thread
     * pattern: the daemon reader drains the merged pipe while {@code waitFor} enforces the
     * timeout. On timeout the process is destroyed and the partial output discarded.
     *
     * @param command shell command line to execute
     * @param cwd existing working directory for the child process
     * @param timeoutMs maximum wait before the child is killed
     * @return success with capped output, or failure carrying the exit status
     */
    static ToolResult runLocal(String command, File cwd, long timeoutMs) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            // Drain the pipe while waiting, or long output deadlocks until the timeout kills it.
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> drain(process, output));
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                return ToolResult.failure("", "Command timed out after " + timeoutMs + " ms");
            }
            reader.join(1000);
            String text = cap(output.toString());
            int exit = process.exitValue();
            if (exit != 0) {
                return ToolResult.failure("", "exit " + exit + "\n" + text);
            }
            return text.isEmpty() ? ToolResult.success("", "(no output)")
                    : ToolResult.success("", text);
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }

    /**
     * Reader-thread body of the sanctioned pattern: consumes the merged pipe into the buffer
     * until the process exits, stopping at {@link #MAX_CHARS}. Failures are tolerated because
     * the partial output read before the break is still usable.
     *
     * @param process running child process whose stdout/stderr is drained
     * @param output buffer collecting the streamed output
     */
    private static void drain(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0 && output.length() < MAX_CHARS) {
                output.append(buffer, 0, count);
            }
        } catch (Exception ignored) {
            // The process was killed or its stream broke; whatever was read is kept.
            System.err.println("shell output drain interrupted: " + ignored);
        }
    }

    /**
     * Registers the command as a background task and returns its id with follow-up hints;
     * the working directory is resolved locally or remotely according to the execution mode.
     *
     * @param context execution environment providing the background registry
     * @param command shell command line to start
     * @param cwd requested working directory; empty selects the workspace or login directory
     * @return success carrying the task id, or failure when no registry is available
     */
    private ToolResult startBackground(ToolContext context, String command, String cwd) {
        BackgroundTasks backgrounds = context.backgrounds();
        if (backgrounds == null) {
            return ToolResult.failure("", "Background tasks are unavailable");
        }
        boolean remote = context.remoteWorkspace();
        String target = remote
                ? (cwd.isEmpty() ? context.remoteDirectory() : cwd)
                : context.resolvePath(cwd.isEmpty() ? "." : cwd).getAbsolutePath();
        try {
            String id = backgrounds.start(command, target, remote);
            return ToolResult.success("", "Started background task id=" + id
                    + ". Use shell_output with id=\"" + id + "\" to read its output "
                    + "(block=true to wait for completion) and shell_kill with id=\"" + id
                    + "\" to stop it.");
        } catch (RuntimeException error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }

    /**
     * SSH workspace: run the command on the remote host inside the project directory.
     *
     * @param context execution environment providing the remote transport
     * @param command shell command line to execute remotely
     * @param cwd remote directory; empty selects the remote workspace directory
     * @param timeoutMs maximum wait before the transport gives up
     * @return success with capped output, or failure carrying the transport error
     */
    private ToolResult executeRemote(ToolContext context, String command, String cwd,
                                     int timeoutMs) {
        String directory = cwd.isEmpty() ? context.remoteDirectory() : cwd;
        String full = directory.isEmpty() ? command
                : "cd " + shellQuote(directory) + " && " + command;
        try {
            String output = context.remoteExecute(full, timeoutMs);
            String text = cap(output == null ? "" : output);
            return text.trim().isEmpty() ? ToolResult.success("", "(no output)")
                    : ToolResult.success("", text);
        } catch (RuntimeException error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }

    /**
     * Reads {@code timeout_ms} from the arguments (number or numeric string) and clamps it to
     * the supported range; unparseable or missing input falls back to the default.
     *
     * @param arguments call arguments
     * @param defaultMs timeout used when no usable value is supplied
     * @return the clamped timeout in milliseconds
     */
    static long clampTimeout(JSONObject arguments, long defaultMs) {
        Object raw = arguments.opt("timeout_ms");
        long value = -1;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
            } else if (raw instanceof String) {
                try {
                    value = Long.parseLong(((String) raw).trim());
                } catch (NumberFormatException ignored) {
                    value = -1; // unparseable input falls through to the default below
                }
            }
        if (value <= 0) value = defaultMs;
        return Math.min(Math.max(value, MIN_TIMEOUT_MS), MAX_TIMEOUT_MS);
    }

    /** Truncates command output to {@link #MAX_CHARS} with an explicit truncation marker. */
    static String cap(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "\n… truncated" : text;
    }

    /** Single-quotes a path for safe interpolation into the remote command line. */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
