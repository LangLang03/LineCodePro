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
 */
public final class ShellTool implements Tool {

    public static final String NAME = "shell";

    static final int MAX_CHARS = 32_000;
    static final long DEFAULT_TIMEOUT_MS = 60_000;
    static final long DEFAULT_OUTPUT_TIMEOUT_MS = 30_000;
    static final long MIN_TIMEOUT_MS = 1_000;
    static final long MAX_TIMEOUT_MS = 600_000;

    @Override public String name() { return NAME; }

    @Override public String description() {
        return "Run a shell command with the workspace as working directory. Set background=true "
                + "to start it without waiting; the returned task id works with shell_output and "
                + "shell_kill.";
    }

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

    @Override public boolean mutating() { return true; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        String cwd = arguments.optString("cwd", "").trim();
        String command = arguments.optString("command", "");
        boolean background = arguments.optBoolean("background", false);
        String action = (cwd.isEmpty() ? command : cwd + "\n" + command)
                + (background ? "\n[background]" : "");
        return new ToolPresentation("Terminal", ToolIcon.TERMINAL,
                action, ToolPresentation.reason(arguments));
    }

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

    /** SSH workspace: run the command on the remote host inside the project directory. */
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

    static String cap(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "\n… truncated" : text;
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
