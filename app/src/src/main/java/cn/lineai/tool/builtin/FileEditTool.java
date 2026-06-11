package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class FileEditTool extends BaseTool {
    @Override
    public String getName() {
        return "file_edit";
    }

    @Override
    public String getDescription() {
        return "编辑文件内容。通过 old_string/new_string 搜索替换修改文件。";
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
                        .put("old_string", new JSONObject().put("type", "string").put("description", "要搜索的原始文本，必须唯一或明确"))
                        .put("new_string", new JSONObject().put("type", "string").put("description", "替换后的新文本")))
                .put("required", new org.json.JSONArray().put("file_path").put("old_string").put("new_string"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String path = input.optString("file_path");
            String oldString = input.optString("old_string");
            String newString = input.optString("new_string");
            if (path.trim().length() == 0) {
                return error("file_path 不能为空");
            }
            if (oldString.length() == 0) {
                return error("old_string 不能为空");
            }
            File file = FileToolPathPolicy.resolve(context, path);
            if (!file.exists()) {
                return error("文件不存在: " + FileToolPathPolicy.displayPath(context.getHomePath(), file));
            }
            if (file.isDirectory()) {
                return error("路径是一个目录，无法编辑文件: " + path + "\n如需编辑文件，请指定具体文件路径。");
            }
            String content = readUtf8(file);
            if (!content.contains(oldString)) {
                return error("未找到匹配的文本");
            }
            int count = countOccurrences(content, oldString);
            String next = content.replace(oldString, newString);
            FileOutputStream output = new FileOutputStream(file, false);
            try {
                output.write(next.getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }
            return ok("成功编辑文件 " + FileToolPathPolicy.displayPath(context.getHomePath(), file)
                    + " (" + count + " 处匹配已替换)");
        } catch (Exception e) {
            return error("编辑文件失败: " + e.getMessage());
        }
    }

    private String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }

    private int countOccurrences(String content, String value) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
