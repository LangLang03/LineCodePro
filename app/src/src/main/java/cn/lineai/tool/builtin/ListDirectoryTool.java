package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

public final class ListDirectoryTool extends BaseTool {
    @Override
    public String getName() {
        return "list_dir";
    }

    @Override
    public String getDescription() {
        return "列出目录下的直接文件和文件夹。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "目录的绝对或相对路径，可选，默认为 home 目录")));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            File dir = FileToolPathPolicy.resolve(context, input.optString("path"));
            if (!dir.exists()) {
                return error("目录不存在: " + input.optString("path", "."));
            }
            if (!dir.isDirectory()) {
                return error("路径不是目录: " + input.optString("path", "."));
            }
            File[] items = dir.listFiles();
            if (items == null || items.length == 0) {
                return ok("目录 " + FileToolPathPolicy.displayPath(context.getHomePath(), dir) + ":\n(空目录)");
            }
            Arrays.sort(items, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            StringBuilder builder = new StringBuilder();
            builder.append("目录 ").append(FileToolPathPolicy.displayPath(context.getHomePath(), dir)).append(":\n");
            for (File item : items) {
                builder.append(item.isDirectory() ? "[DIR]  " : "[FILE] ")
                        .append(item.getName())
                        .append(item.isDirectory() ? "/" : "")
                        .append('\n');
            }
            return ok(builder.toString().trim());
        } catch (Exception e) {
            return error("列目录失败: " + e.getMessage());
        }
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
