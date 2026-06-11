package cn.lineai.tool;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class ToolExecutor {
    private final ToolRegistry registry;
    private final ToolSettingsRepository settingsRepository;
    private final DiffRepository diffRepository;

    public ToolExecutor(ToolRegistry registry, ToolSettingsRepository settingsRepository) {
        this(registry, settingsRepository, null);
    }

    public ToolExecutor(ToolRegistry registry, ToolSettingsRepository settingsRepository, DiffRepository diffRepository) {
        this.registry = registry;
        this.settingsRepository = settingsRepository;
        this.diffRepository = diffRepository;
    }

    public ToolResult execute(ToolCall toolCall, ToolContext context) {
        return execute(toolCall, context, false);
    }

    public ToolResult executeConfirmed(ToolCall toolCall, ToolContext context) {
        return execute(toolCall, context, true);
    }

    private ToolResult execute(ToolCall toolCall, ToolContext context, boolean confirmed) {
        if (toolCall == null) {
            return new ToolResult("", "", "工具调用为空", true);
        }
        ToolContext callContext = (context == null ? new ToolContext("") : context).withToolCallId(toolCall.getId());
        BaseTool tool = registry.get(toolCall.getName());
        if (tool == null) {
            return new ToolResult(toolCall.getId(), toolCall.getName(), "未知工具: " + toolCall.getName(), true);
        }
        PermissionResult permission = settingsRepository.canExecuteTool(tool.getName(), tool.getCategory());
        if (!permission.isAllowed()) {
            return new ToolResult(toolCall.getId(), tool.getName(), permission.getReason(), true);
        }
        if (tool.requiresConfirmation() && settingsRepository.needsConfirmation(tool.getName()) && !confirmed) {
            return new ToolResult(toolCall.getId(), tool.getName(), "工具需要确认后才能执行: " + tool.getName(), true);
        }
        try {
            JSONObject input = toolCall.getArguments().trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(toolCall.getArguments());
            ToolResult result = shouldRecordDiff(tool.getName())
                    ? executeWithDiff(tool, input, callContext)
                    : tool.execute(input, callContext);
            return result.withCall(toolCall.getId(), tool.getName());
        } catch (Exception e) {
            return new ToolResult(toolCall.getId(), tool.getName(), "参数解析失败: " + e.getMessage(), true);
        }
    }

    private boolean shouldRecordDiff(String toolName) {
        return diffRepository != null && ("file_write".equals(toolName) || "file_edit".equals(toolName));
    }

    private ToolResult executeWithDiff(BaseTool tool, JSONObject input, ToolContext context) {
        String path = input.optString("file_path");
        File file;
        boolean existed;
        String oldContent = "";
        try {
            file = FileToolPathPolicy.resolve(context, path);
            existed = file.exists();
            if (existed && file.isDirectory()) {
                return new ToolResult("", tool.getName(), "路径是一个目录，无法写入文件: " + path, true);
            }
            if (existed) {
                oldContent = readUtf8(file);
            }
        } catch (Exception e) {
            return new ToolResult("", tool.getName(), "无法读取原文件: " + path + "\n" + e.getMessage(), true);
        }

        ToolResult result = tool.execute(input, context);
        if (result.isError()) {
            return result;
        }

        String newContent;
        try {
            newContent = file.exists() ? readUtf8(file) : "";
        } catch (Exception ignored) {
            newContent = input.optString("content");
        }
        if (!oldContent.equals(newContent)) {
            DiffRecord diff = diffRepository.recordDiff(file.getPath(), oldContent, newContent, existed);
            return result.withDiffId(diff.getId());
        }
        return result;
    }

    private String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
