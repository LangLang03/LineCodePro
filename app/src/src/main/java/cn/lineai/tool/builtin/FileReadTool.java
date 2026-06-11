package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;

public final class FileReadTool extends BaseTool {
    private static final long LARGE_FILE_THRESHOLD_BYTES = 50L * 1024L;
    private static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_DIRECTORY_ITEMS = 400;

    @Override
    public String getName() {
        return "file_read";
    }

    @Override
    public String getDescription() {
        return "读取文件内容。返回带行号的文件内容；大文件请通过 start_line/end_line 分段读取。读取目录时返回目录树。";
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
                        .put("file_path", new JSONObject().put("type", "string").put("description", "文件的绝对或相对路径"))
                        .put("start_line", new JSONObject().put("type", "number").put("description", "起始行号，从 1 开始，包含该行"))
                        .put("end_line", new JSONObject().put("type", "number").put("description", "结束行号，从 1 开始，包含该行"))
                        .put("offset", new JSONObject().put("type", "number").put("description", "兼容旧参数：起始行偏移，从 0 开始"))
                        .put("limit", new JSONObject().put("type", "number").put("description", "兼容旧参数：最大读取行数，默认 2000")))
                .put("required", new org.json.JSONArray().put("file_path"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            File file = FileToolPathPolicy.resolve(context, input.optString("file_path"));
            if (!file.exists()) {
                return error("文件不存在: " + FileToolPathPolicy.displayPath(context.getHomePath(), file));
            }
            if (file.isDirectory()) {
                StringBuilder builder = new StringBuilder();
                int[] count = new int[] {0};
                appendDirectory(builder, file, "", count);
                String list = builder.length() == 0 ? "(空目录)" : builder.toString().trim();
                return ok("目录 " + FileToolPathPolicy.displayPath(context.getHomePath(), file) + ":\n" + list
                        + "\n\n如需读取文件，请指定具体文件路径。");
            }
            boolean hasLineRange = input.has("start_line") || input.has("end_line");
            if (!hasLineRange && file.length() > LARGE_FILE_THRESHOLD_BYTES) {
                return error("文件 " + FileToolPathPolicy.displayPath(context.getHomePath(), file)
                        + " 大小为 " + file.length() + " bytes，单次读取超过 50KB。\n"
                        + "请使用 start_line 和 end_line 指定行号范围，例如："
                        + "{\"file_path\":\"" + input.optString("file_path") + "\",\"start_line\":1,\"end_line\":200}");
            }
            String content = readUtf8(file);
            String[] lines = content.split("\n", -1);
            Range range = resolveRange(input, lines.length);
            StringBuilder result = new StringBuilder();
            for (int i = range.startIndex; i < range.endIndexExclusive; i++) {
                result.append(i + 1).append('\t').append(lines[i]);
                if (i + 1 < range.endIndexExclusive) {
                    result.append('\n');
                }
            }
            if (range.startIndex > 0 || range.endIndexExclusive < lines.length) {
                result.append("\n\n... (共 ").append(lines.length).append(" 行，显示 ")
                        .append(range.startIndex + 1).append('-').append(range.endIndexExclusive).append(')');
            }
            return ok(result.toString());
        } catch (Exception e) {
            return error("读取文件失败: " + e.getMessage());
        }
    }

    private void appendDirectory(StringBuilder builder, File dir, String parentPath, int[] count) {
        if (count[0] >= MAX_DIRECTORY_ITEMS) {
            return;
        }
        File[] items = dir.listFiles();
        if (items == null) {
            return;
        }
        Arrays.sort(items, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File item : items) {
            if (count[0] >= MAX_DIRECTORY_ITEMS) {
                builder.append("... (目录项过多，已截断)\n");
                return;
            }
            String relative = parentPath.length() == 0 ? item.getName() : parentPath + "/" + item.getName();
            if (item.isDirectory()) {
                builder.append("[DIR]  ").append(relative).append("/\n");
                count[0]++;
                appendDirectory(builder, item, relative, count);
            } else {
                builder.append("[FILE] ").append(relative).append('\n');
                count[0]++;
            }
        }
    }

    private Range resolveRange(JSONObject input, int totalLines) {
        if (input.has("start_line") || input.has("end_line")) {
            int startLine = Math.max(1, input.optInt("start_line", 1));
            int endLine = Math.min(totalLines, Math.max(startLine, input.optInt("end_line", Math.min(totalLines, startLine + DEFAULT_LIMIT - 1))));
            return new Range(startLine - 1, endLine);
        }
        int offset = Math.max(0, input.optInt("offset", 0));
        int limit = Math.max(1, input.optInt("limit", DEFAULT_LIMIT));
        return new Range(Math.min(offset, totalLines), Math.min(totalLines, offset + limit));
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

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private static final class Range {
        final int startIndex;
        final int endIndexExclusive;

        Range(int startIndex, int endIndexExclusive) {
            this.startIndex = startIndex;
            this.endIndexExclusive = endIndexExclusive;
        }
    }
}
