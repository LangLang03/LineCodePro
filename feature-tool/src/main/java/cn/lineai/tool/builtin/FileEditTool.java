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

public final class FileEditTool extends BaseTool {
    public static final String NAME = "file_edit";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Edit file contents. Search and replace via old_string/new_string.";
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
                        .put("old_string", new JSONObject().put("type", "string").put("description", "Original text to search for; must be unique unless replace_all is true"))
                        .put("new_string", new JSONObject().put("type", "string").put("description", "Replacement text"))
                        .put("replace_all", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "If true, replace every occurrence of old_string. Default false: require a unique match and replace only once.")))
                .put("required", new org.json.JSONArray().put("file_path").put("old_string").put("new_string"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String path = input.optString("file_path");
            String oldString = input.optString("old_string");
            String newString = input.optString("new_string");
            boolean replaceAll = input.optBoolean("replace_all", false);
            ToolArgs.requireNonEmpty(path, "file_path");
            if (oldString.length() == 0) {
                return error(context.getString(R.string.tool_file_edit_old_string_empty));
            }
            File file = FileToolPathPolicy.resolve(context, path);
            if (!file.exists()) {
                return error(context.getString(R.string.tool_file_edit_not_found, FileToolPathPolicy.displayPath(context.getHomePath(), file)));
            }
            if (file.isDirectory()) {
                return error(context.getString(R.string.tool_file_edit_is_directory, path));
            }
            String content = FileIo.readUtf8(file);
            if (!content.contains(oldString)) {
                return error(context.getString(R.string.tool_file_edit_no_match));
            }
            int count = countOccurrences(content, oldString);
            if (count > 1 && !replaceAll) {
                return error(context.getString(R.string.tool_file_edit_multiple_matches, count));
            }
            String next = replaceAll
                    ? content.replace(oldString, newString)
                    : replaceFirst(content, oldString, newString);
            int replaced = replaceAll ? count : 1;
            FileOutputStream output = new FileOutputStream(file, false);
            try {
                output.write(next.getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }
            return ok(context.getString(
                    R.string.tool_file_edit_success,
                    FileToolPathPolicy.displayPath(context.getHomePath(), file),
                    replaced
            ));
        } catch (Exception e) {
            return error(context.getString(R.string.tool_file_edit_failed, e.getMessage()));
        }
    }

    private static String replaceFirst(String content, String oldString, String newString) {
        int index = content.indexOf(oldString);
        if (index < 0) {
            return content;
        }
        return content.substring(0, index) + newString + content.substring(index + oldString.length());
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

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallWriteView.class;
    }
}
