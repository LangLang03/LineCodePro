package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.ipc.terminal.TerminalShellCallback;
import cn.lineai.ipc.terminal.TerminalShellResult;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ExceptionUtils;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ShellExecuteTool extends BaseTool {
    public static final String NAME = "shell_execute";
    private final SshService sshService;
    private final IpcProviderManager ipcProviderManager;

    public ShellExecuteTool(Context context) {
        this(context, null);
    }

    public ShellExecuteTool(Context context, IpcProviderManager ipcProviderManager) {
        sshService = context == null ? null : new SshService(context);
        this.ipcProviderManager = ipcProviderManager;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Execute a shell command via the current execution target: SSH mode uses SSH, terminal provider mode uses IPC. The command requires user confirmation before execution.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.SHELL;
    }

    @Override
    public boolean needsConfirmation() {
        return true;
    }

    @Override
    public boolean isAllowedInReadonlyMode() {
        return true;
    }

    @Override
    public String promptSupplement(String executionMode, boolean isSsh) {
        if (isSsh) {
            return "shell_execute runs in the current workspace directory by default; set cwd explicitly to switch temporarily.";
        }
        return "shell_execute runs via the terminal provider IPC; it runs in the current workspace directory by default; set cwd explicitly to switch temporarily.";
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("command", new JSONObject()
                                .put("type", "string")
                                .put("description", "The shell command to execute"))
                        .put("cwd", new JSONObject()
                                .put("type", "string")
                                .put("description", "Optional working directory; the command runs after cd into it"))
                        .put("timeoutMs", new JSONObject()
                                .put("type", "number")
                                .put("description", "Optional timeout in milliseconds, default 30000, max 300000")))
                .put("required", new JSONArray().put("command"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String inputCommand = input.optString("command", "");
        String inputCwd = input.optString("cwd", "");
        if (inputCommand.trim().length() == 0) {
            return error(context.getString(R.string.tool_shell_command_empty));
        }
        int timeoutMs = Math.max(1000, Math.min(input.optInt("timeoutMs", 30000), 300000));
        String cwd = inputCwd.trim().length() > 0
                ? inputCwd.trim()
                : context == null ? "" : context.getHomePath().trim();
        ToolSettingsStore settings = resolveSettings(context);
        if (isTerminalProviderMode(settings)) {
            return executeViaTerminalProvider(inputCommand, cwd, timeoutMs, context);
        }
        return executeViaSsh(inputCommand, cwd, timeoutMs, context);
    }

    private ToolSettingsStore resolveSettings(ToolContext context) {
        if (context != null && context.getToolSettingsStore() != null) {
            return context.getToolSettingsStore();
        }
        return null;
    }

    private boolean isTerminalProviderMode(ToolSettingsStore settings) {
        return settings != null
                && ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(settings.getExecutionMode());
    }

    private ToolResult executeViaTerminalProvider(String command, String cwd, long timeoutMs, ToolContext context) {
        if (ipcProviderManager == null) {
            return error(context.getString(R.string.tool_shell_ipc_not_init));
        }
        TerminalIpcProvider provider = ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL) instanceof TerminalIpcProvider
                ? (TerminalIpcProvider) ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL)
                : null;
        if (provider == null) {
            return error(context.getString(R.string.tool_shell_no_provider));
        }
        if (!provider.isBound()) {
            return error(context.getString(R.string.tool_shell_provider_not_bound));
        }
        if (context != null) {
            context.reportToolProgress(getName(), "", false);
        }
        StringBuilder streamedOutput = new StringBuilder();
        try {
            TerminalShellResult result = provider.executeShell(command, cwd, timeoutMs, new TerminalShellCallback() {
                @Override
                public void onOutput(String content) {
                    synchronized (streamedOutput) {
                        streamedOutput.setLength(0);
                        streamedOutput.append(content == null ? "" : content);
                    }
                    if (context != null) {
                        context.reportToolProgress(getName(), content, false);
                    }
                }

                @Override
                public void onError(String error) {
                }

                @Override
                public void onComplete(int exitCode) {
                }
            });
            String output = streamedOutput.toString().trim();
            if (!result.isSuccess()) {
                String message = context.getString(R.string.tool_shell_exec_failed_exit, result.getExitCode());
                return error(output.length() == 0 ? message : truncateOutput(output, context) + "\n" + message);
            }
            if (output.length() == 0) {
                return ok(context.getString(R.string.tool_shell_exec_no_output));
            }
            return ok(truncateOutput(output, context));
        } catch (Exception e) {
            restoreInterrupt(e);
            String existing;
            synchronized (streamedOutput) {
                existing = streamedOutput.toString().trim();
            }
            String message = context.getString(R.string.tool_shell_exec_failed, describeException(e));
            return error(existing.length() == 0 ? message : truncateOutput(existing, context) + "\n" + message);
        }
    }

    private ToolResult executeViaSsh(String inputCommand, String cwd, long timeoutMs, ToolContext context) {
        if (sshService == null) {
            return error(context.getString(R.string.tool_shell_ssh_not_init));
        }
        String command = cwd.length() > 0
                ? "cd " + shellQuote(cwd) + " && " + inputCommand
                : inputCommand;
        StringBuilder streamedOutput = new StringBuilder();
        if (context != null) {
            context.reportToolProgress(getName(), "", false);
        }
        try {
            String output = sshService.executeCommand(command, (int) timeoutMs, null, streamed -> {
                synchronized (streamedOutput) {
                    streamedOutput.setLength(0);
                    streamedOutput.append(streamed == null ? "" : streamed);
                }
                if (context != null) {
                    context.reportToolProgress(getName(), streamed, false);
                }
            });
            if (output.length() == 0) {
                return ok(context.getString(R.string.tool_shell_exec_no_output));
            }
            return ok(truncateOutput(output, context));
        } catch (Exception e) {
            restoreInterrupt(e);
            String existing;
            synchronized (streamedOutput) {
                existing = streamedOutput.toString().trim();
            }
            String message = context.getString(R.string.tool_shell_exec_failed, describeException(e));
            return error(existing.length() == 0 ? message : truncateOutput(existing, context) + "\n" + message);
        }
    }

    private String truncateOutput(String output, ToolContext context) {
        if (output == null || output.length() <= ToolResult.MAX_TOOL_RESULT_CHARS) {
            return output;
        }
        int lines = 1;
        for (int i = 0; i < output.length(); i++) {
            if (output.charAt(i) == '\n') lines++;
        }
        String truncated = ToolResult.truncateContent(output);
        return context.getString(R.string.tool_shell_output_line_count, lines) + truncated;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void restoreInterrupt(Exception error) {
        ExceptionUtils.restoreInterrupt(error);
    }

    private static String describeException(Exception error) {
        return ExceptionUtils.describeException(error);
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallShellView.class;
    }
}
