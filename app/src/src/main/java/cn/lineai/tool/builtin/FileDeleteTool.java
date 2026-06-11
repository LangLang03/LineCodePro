package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class FileDeleteTool extends BaseTool {
    @Override
    public String getName() {
        return "file_delete";
    }

    @Override
    public String getDescription() {
        return "删除文件或目录。必须提供删除原因 reason；执行前会请求用户确认。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.WRITE;
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
                        .put("reason", new JSONObject().put("type", "string").put("description", "删除原因，会展示给用户确认"))
                        .put("paths", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "要删除的文件或目录路径列表")))
                .put("required", new org.json.JSONArray().put("reason").put("paths"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        ArrayList<String> paths = paths(input);
        String reason = input.optString("reason").trim();
        if (reason.length() == 0) {
            return error("删除原因 reason 不能为空");
        }
        if (paths.isEmpty()) {
            return error("paths 不能为空");
        }

        ArrayList<String> deleted = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        for (String path : paths) {
            try {
                File target = FileToolPathPolicy.resolve(context, path);
                if (!target.exists()) {
                    errors.add("路径不存在: " + path);
                    continue;
                }
                deleteRecursive(target);
                deleted.add(FileToolPathPolicy.displayPath(context.getHomePath(), target));
            } catch (Exception e) {
                errors.add("删除 " + path + " 失败: " + e.getMessage());
            }
        }

        StringBuilder builder = new StringBuilder();
        if (!deleted.isEmpty()) {
            builder.append("成功删除 ").append(deleted.size()).append(" 项:\n");
            for (String path : deleted) {
                builder.append("- ").append(path).append('\n');
            }
        }
        if (!errors.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("失败 ").append(errors.size()).append(" 项:\n");
            for (String error : errors) {
                builder.append("- ").append(error).append('\n');
            }
        }
        return new ToolResult("", getName(), builder.length() == 0 ? "没有删除任何文件" : builder.toString().trim(),
                errors.size() > 0 && deleted.isEmpty());
    }

    private ArrayList<String> paths(JSONObject input) {
        ArrayList<String> values = new ArrayList<>();
        JSONArray array = input.optJSONArray("paths");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (value.length() > 0) {
                    values.add(value);
                }
            }
        }
        String filePath = input.optString("file_path").trim();
        if (filePath.length() > 0) {
            values.add(filePath);
        }
        String path = input.optString("path").trim();
        if (path.length() > 0) {
            values.add(path);
        }
        return values;
    }

    private void deleteRecursive(File file) throws java.io.IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            throw new java.io.IOException("无法删除 " + file.getPath());
        }
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
