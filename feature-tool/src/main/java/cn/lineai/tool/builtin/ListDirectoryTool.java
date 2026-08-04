package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

public final class ListDirectoryTool extends BaseTool {
    public static final String NAME = "list_dir";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List the immediate files and folders under a directory.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_action_list_dir);
    }

    @Override
    public int getActionIcon() {
        return ICON_FOLDER_OPEN;
    }

    @Override
    public String getDisplayLabel(Context ctx, JSONObject input, String workspacePath) {
        String path = input.optString("path");
        if (path.length() > 0) {
            return displayPath(workspacePath, path);
        }
        return null;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute or relative directory path, optional, defaults to the home directory")));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            File dir = FileToolPathPolicy.resolve(context, input.optString("path"));
            if (!dir.exists()) {
                return error(context.getString(R.string.tool_list_dir_not_found, input.optString("path", ".")));
            }
            if (!dir.isDirectory()) {
                return error(context.getString(R.string.tool_list_dir_not_directory, input.optString("path", ".")));
            }
            File[] items = dir.listFiles();
            if (items == null || items.length == 0) {
                return ok(context.getString(R.string.tool_list_dir_empty, FileToolPathPolicy.displayPath(context.getHomePath(), dir)));
            }
            Arrays.sort(items, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            StringBuilder builder = new StringBuilder();
            builder.append(context.getString(R.string.tool_list_dir_content, FileToolPathPolicy.displayPath(context.getHomePath(), dir)));
            for (File item : items) {
                builder.append(item.isDirectory() ? "[DIR]  " : "[FILE] ")
                        .append(item.getName())
                        .append(item.isDirectory() ? "/" : "")
                        .append('\n');
            }
            return ok(builder.toString().trim());
        } catch (Exception e) {
            return error(context.getString(R.string.tool_list_dir_failed, e.getMessage()));
        }
    }

    /** 将绝对路径转换为相对于工作区的展示路径。 */
    private static String displayPath(String workspacePath, String path) {
        if (path == null || path.trim().length() == 0) return "";
        String value = path.trim().replace('\\', '/');
        if (value.startsWith("file://")) {
            value = value.substring("file://".length());
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.length() == 0) return "";
        if (!value.startsWith("/")) {
            while (value.startsWith("./")) {
                value = value.substring(2);
            }
            return value;
        }
        String root = workspacePath == null ? "" : workspacePath.trim().replace('\\', '/');
        while (root.length() > 1 && root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.length() == 0 || !root.startsWith("/")) return value;
        if (value.equals(root)) return ".";
        String prefix = root + "/";
        if (value.startsWith(prefix)) {
            String relative = value.substring(prefix.length());
            while (relative.startsWith("./")) {
                relative = relative.substring(2);
            }
            return relative;
        }
        return value;
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
