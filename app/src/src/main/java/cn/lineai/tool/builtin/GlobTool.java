package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class GlobTool extends BaseTool {
    private static final int MAX_RESULTS = 1000;

    @Override
    public String getName() {
        return "glob";
    }

    @Override
    public String getDescription() {
        return "搜索匹配的文件。支持 * ** ? 通配符。";
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
                        .put("pattern", new JSONObject().put("type", "string").put("description", "文件匹配模式，如 *.java, app/src/**/*.java"))
                        .put("path", new JSONObject().put("type", "string").put("description", "搜索根目录，可选，默认为 home 目录")))
                .put("required", new org.json.JSONArray().put("pattern"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String pattern = input.optString("pattern");
            if (pattern.trim().length() == 0) {
                return error("pattern 不能为空");
            }
            File root = FileToolPathPolicy.resolve(context, input.optString("path"));
            if (!root.exists() || !root.isDirectory()) {
                return error("搜索根目录不存在或不是目录: " + input.optString("path", "."));
            }
            ArrayList<String> results = new ArrayList<>();
            Pattern compiled = Pattern.compile(globToRegex(pattern));
            search(root, "", pattern, compiled, results);
            String displayRoot = FileToolPathPolicy.displayPath(context.getHomePath(), root);
            if (results.isEmpty()) {
                return ok("在 " + displayRoot + " 目录下未找到匹配 \"" + pattern + "\" 的文件。");
            }
            StringBuilder builder = new StringBuilder();
            builder.append("在 ").append(displayRoot).append(" 目录下找到 ").append(results.size()).append(" 个匹配文件:\n");
            for (String result : results) {
                builder.append(result).append('\n');
            }
            if (results.size() >= MAX_RESULTS) {
                builder.append("... (结果过多，已截断)\n");
            }
            return ok(builder.toString().trim());
        } catch (Exception e) {
            return error("搜索失败: " + e.getMessage());
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

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
