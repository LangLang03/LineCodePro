package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ShellExecuteTool extends BaseTool {
    private final SshService sshService;

    public ShellExecuteTool(Context context) {
        sshService = context == null ? null : new SshService(context);
    }

    @Override
    public String getName() {
        return "shell_execute";
    }

    @Override
    public String getDescription() {
        return "通过 SSH 执行 shell 命令。用于 Termux 或远程 Linux 主机，命令会在执行前请求用户确认。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("command", new JSONObject()
                                .put("type", "string")
                                .put("description", "要执行的 shell 命令"))
                        .put("cwd", new JSONObject()
                                .put("type", "string")
                                .put("description", "可选工作目录，会通过 cd 后执行命令"))
                        .put("timeoutMs", new JSONObject()
                                .put("type", "number")
                                .put("description", "可选超时时间，单位毫秒，默认 30000，最大 300000")))
                .put("required", new JSONArray().put("command"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (sshService == null) {
            return error("当前环境未初始化 SSH 服务。");
        }
        String inputCommand = input.optString("command", "");
        String inputCwd = input.optString("cwd", "");
        if (inputCommand.trim().length() == 0) {
            return error("命令不能为空");
        }
        int timeoutMs = Math.max(1000, Math.min(input.optInt("timeoutMs", 30000), 300000));
        String cwd = inputCwd.trim().length() > 0
                ? inputCwd.trim()
                : context == null ? "" : context.getHomePath().trim();
        String command = cwd.length() > 0
                ? "cd " + shellQuote(cwd) + " && " + inputCommand
                : inputCommand;
        StringBuilder streamedOutput = new StringBuilder();
        if (context != null) {
            context.reportToolProgress(getName(), "", false);
        }
        try {
            String output = sshService.executeCommand(command, timeoutMs, null, streamed -> {
                synchronized (streamedOutput) {
                    streamedOutput.setLength(0);
                    streamedOutput.append(streamed == null ? "" : streamed);
                }
                if (context != null) {
                    context.reportToolProgress(getName(), streamed, false);
                }
            });
            return ok(output.length() == 0 ? "命令执行完成，无输出" : output);
        } catch (Exception e) {
            String existing;
            synchronized (streamedOutput) {
                existing = streamedOutput.toString().trim();
            }
            String message = "命令执行失败: " + e.getMessage();
            return error(existing.length() == 0 ? message : existing + "\n" + message);
        }
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
