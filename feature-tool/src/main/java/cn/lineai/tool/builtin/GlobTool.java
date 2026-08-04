package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolArgs;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class GlobTool extends BaseTool {
    public static final String NAME = "glob";
    private static final int MAX_RESULTS = 1000;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Search for matching files. Supports * ** ? wildcards.";
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
        return context.getString(R.string.tool_call_action_match);
    }

    @Override
    public int getActionIcon() {
        return ICON_SEARCH;
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
                        .put("pattern", new JSONObject().put("type", "string").put("description", "File match pattern, e.g. *.java, app/src/**/*.java"))
                        .put("path", new JSONObject().put("type", "string").put("description", "Search root directory, optional, defaults to the home directory")))
                .put("required", new org.json.JSONArray().put("pattern"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String pattern = input.optString("pattern");
            ToolArgs.requireNonEmpty(pattern, "pattern");
            File root = FileToolPathPolicy.resolve(context, input.optString("path"));
            if (!root.exists() || !root.isDirectory()) {
                return error(context.getString(R.string.tool_glob_root_not_found, input.optString("path", ".")));
            }
            ArrayList<String> results = new ArrayList<>();
            Pattern compiled = Pattern.compile(globToRegex(pattern));
            search(root, "", pattern, compiled, results);
            String displayRoot = FileToolPathPolicy.displayPath(context.getHomePath(), root);
            if (results.isEmpty()) {
                return ok(context.getString(R.string.tool_glob_no_match, pattern, displayRoot));
            }
            StringBuilder builder = new StringBuilder();
            builder.append(context.getString(R.string.tool_glob_found, results.size(), displayRoot));
            for (String result : results) {
                builder.append(result).append('\n');
            }
            if (results.size() >= MAX_RESULTS) {
                builder.append(context.getString(R.string.tool_glob_truncated));
            }
            return ok(builder.toString().trim());
        } catch (Exception e) {
            return error(context.getString(R.string.tool_glob_failed, e.getMessage()));
        }
    }

    private void search(File dir, String parentPath, String pattern, Pattern compiled, ArrayList<String> results) {
        if (results.size() >= MAX_RESULTS) {
            return;
        }
        File[] items = dir.listFiles();
        if (items == null) {
            return;
        }
        Arrays.sort(items, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File item : items) {
            if (results.size() >= MAX_RESULTS) {
                return;
            }
            String name = item.getName();
            String relative = parentPath.length() == 0 ? name : parentPath + "/" + name;
            if (item.isDirectory()) {
                if (!name.startsWith(".") && !"node_modules".equals(name)) {
                    search(item, relative, pattern, compiled, results);
                }
            } else if (compiled.matcher(relative).matches()
                    || (pattern.indexOf('/') < 0 && compiled.matcher(name).matches())) {
                results.add(relative);
            }
        }
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return regex.toString();
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
