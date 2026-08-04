package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolArgs;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class FileWriteTool extends BaseTool {
    public static final String NAME = "file_write";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Write content to a file. Automatically creates the file or directory if it does not exist.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.WRITE;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.WRITE;
    }

    @Override
    public boolean shouldRecordDiff() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("file_path", new JSONObject().put("type", "string").put("description", "Absolute or relative file path"))
                        .put("content", new JSONObject().put("type", "string").put("description", "Content to write")))
                .put("required", new org.json.JSONArray().put("file_path").put("content"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String path = input.optString("file_path");
            ToolArgs.requireNonEmpty(path, "file_path");
            File file = FileToolPathPolicy.resolve(context, path);
            if (file.exists() && file.isDirectory()) {
                return error(context.getString(R.string.tool_file_write_is_directory, path));
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return error(context.getString(R.string.tool_file_write_mkdir_failed, parent.getPath()));
            }
            boolean existed = file.exists();
            byte[] bytes = input.optString("content").getBytes(StandardCharsets.UTF_8);
            FileOutputStream output = new FileOutputStream(file, false);
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
            int lineCount = input.optString("content").split("\n", -1).length;
            return ok(context.getString(existed ? R.string.tool_file_write_updated : R.string.tool_file_write_created, path, lineCount));
        } catch (Exception e) {
            return error(context.getString(R.string.tool_file_write_failed, e.getMessage()));
        }
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallWriteView.class;
    }
}
