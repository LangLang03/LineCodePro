package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class FileWriteTool extends BaseTool {
    @Override
    public String getName() {
        return "file_write";
    }

    @Override
    public String getDescription() {
        return "将内容写入文件。如果文件或目录不存在会自动创建。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.WRITE;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("file_path", new JSONObject().put("type", "string").put("description", "文件的绝对或相对路径"))
                        .put("content", new JSONObject().put("type", "string").put("description", "要写入的内容")))
                .put("required", new org.json.JSONArray().put("file_path").put("content"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String path = input.optString("file_path");
            if (path.trim().length() == 0) {
                return error("file_path 不能为空");
            }
            File file = FileToolPathPolicy.resolve(context, path);
            if (file.exists() && file.isDirectory()) {
                return error("路径是一个目录，无法写入文件: " + path + "\n如需创建文件，请指定完整文件路径。");
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return error("无法创建父目录: " + parent.getPath());
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
            return ok((existed ? "成功更新文件 " : "成功创建文件 ") + path + " (" + lineCount + " 行)");
        } catch (Exception e) {
            return error("写入文件失败: " + e.getMessage());
        }
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
