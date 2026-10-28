package cn.lineai.tool.workspace;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import cn.lineai.tool.api.PathPolicy;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** Runs one shell command inside the workspace with a hard timeout. */
public final class ShellTool implements Tool {

    private static final int MAX_CHARS = 32_000;
    private static final long TIMEOUT_SECONDS = 60;

    @Override public String name() { return "shell_execute"; }

    @Override public String description() {
        return "Run a shell command with the workspace as working directory.";
    }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("command", "Shell command")
                .string("cwd", "Workspace-relative directory")
                .string("reason", "Why this command is needed")
                .required("command").build();
    }

    @Override public boolean mutating() { return true; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        String cwd = arguments.optString("cwd", "").trim();
        String command = arguments.optString("command", "");
        return new ToolPresentation("Terminal", ToolIcon.TERMINAL,
                cwd.isEmpty() ? command : cwd + "\n" + command,
                ToolPresentation.reason(arguments));
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String command = arguments.optString("command", "").trim();
        if (command.isEmpty()) return ToolResult.failure("", "command must not be empty");
        if (!context.mutationAllowed()) return ToolResult.failure("", "Mutation not allowed");
        try {
            File cwd = context.resolvePath(arguments.optString("cwd", "."));
            if (!cwd.isDirectory()) return ToolResult.failure("", "Not a directory: " + cwd);
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("", "Command timed out after " + TIMEOUT_SECONDS + "s");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (output.length() < MAX_CHARS) output.append(buffer, 0, count);
                }
            }
            String text = output.toString();
            if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS) + "\n… truncated";
            int exit = process.exitValue();
            if (exit != 0) {
                return ToolResult.failure("", "exit " + exit + "\n" + text);
            }
            return text.isEmpty() ? ToolResult.success("", "(no output)") : ToolResult.success("", text);
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }
}
