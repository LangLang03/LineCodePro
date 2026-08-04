package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;

public final class FileReadTool extends BaseTool {
    public static final String NAME = "file_read";
    private static final long LARGE_FILE_THRESHOLD_BYTES = 50L * 1024L;
    private static final int MAX_DIRECTORY_ITEMS = 400;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read file contents. Returns line-numbered content; for large files, read in segments via start_kb/end_kb. Returns a directory tree when reading a directory.";
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
        return context.getString(R.string.tool_call_action_read);
    }

    @Override
    public String getDisplayLabel(Context ctx, JSONObject input, String workspacePath) {
        String filePath = input.optString("file_path");
        if (filePath.length() > 0) {
            return displayPath(workspacePath, filePath);
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
                        .put("file_path", new JSONObject().put("type", "string").put("description", "Absolute or relative file path"))
                        .put("start_kb", new JSONObject().put("type", "number").put("description", "Start position in KB, default 0"))
                        .put("end_kb", new JSONObject().put("type", "number").put("description", "End position in KB, default 50, max 50")))
                .put("required", new org.json.JSONArray().put("file_path"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            File file = FileToolPathPolicy.resolve(context, input.optString("file_path"));
            if (!file.exists()) {
                return error(context.getString(R.string.tool_file_read_not_found, FileToolPathPolicy.displayPath(context.getHomePath(), file)));
            }
            if (file.isDirectory()) {
                StringBuilder builder = new StringBuilder();
                int[] count = new int[] {0};
                appendDirectory(builder, file, "", count, context);
                String list = builder.length() == 0 ? context.getString(R.string.tool_file_read_empty_dir) : builder.toString().trim();
                return ok(context.getString(R.string.tool_file_read_dir_content, FileToolPathPolicy.displayPath(context.getHomePath(), file), list)
                        + context.getString(R.string.tool_file_read_dir_specify_file));
            }

            int startKb = Math.max(0, input.optInt("start_kb", 0));
            int endKb = Math.max(startKb + 1, Math.min(50, input.optInt("end_kb", 50)));
            boolean hasKbRange = input.has("start_kb") || input.has("end_kb");

            long fileLen = file.length();

            // No KB range specified:
            //  - small file (< 50KB): read entirely
            //  - large file (>= 50KB): refuse and suggest using KB range
            if (!hasKbRange) {
                if (fileLen > LARGE_FILE_THRESHOLD_BYTES) {
                    return error(context.getString(R.string.tool_file_read_exceed_50kb,
                            FileToolPathPolicy.displayPath(context.getHomePath(), file),
                            fileLen / 1024,
                            input.optString("file_path")));
                }
                String content = FileIo.readUtf8(file);
                String numbered = addLineNumbers(content, 1);
                return ok(ToolResult.truncateContent(numbered));
            }

            // KB range specified: read ONLY the requested byte range. This caps the
            // single-read size at (end_kb - start_kb) KB regardless of file size, so
            // files larger than 1MB can still be read range-by-range.
            long startByte = Math.min((long) startKb * 1024L, fileLen);
            long endByte = Math.min((long) endKb * 1024L, fileLen);
            if (startByte >= fileLen) {
                return error(context.getString(R.string.tool_file_read_start_out_of_range, startKb, fileLen / 1024));
            }

            byte[] chunk = readRange(file, startByte, endByte);
            String content = new String(chunk, StandardCharsets.UTF_8);

            // Snap to line boundaries so we never return a partial line.
            int startChar = 0;
            int endChar = content.length();
            if (startByte > 0) {
                int lineStart = content.lastIndexOf('\n', content.length() - 1);
                if (lineStart >= 0) {
                    startChar = lineStart + 1;
                }
            }
            if (endByte < fileLen) {
                int lineEnd = content.indexOf('\n', startChar);
                if (lineEnd >= 0) {
                    endChar = lineEnd + 1;
                }
            }

            // Count the absolute line number at the (snapped) start position.
            long startLineNumber = 1;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long scan = Math.max(0, startByte + startChar);
                long pos = 0;
                while (pos < scan) {
                    raf.seek(pos);
                    int b = raf.read();
                    if (b < 0) break;
                    if (b == '\n') startLineNumber++;
                    pos++;
                }
            }

            String extracted = content.substring(startChar, endChar);
            StringBuilder result = new StringBuilder();
            result.append(addLineNumbers(extracted, (int) startLineNumber));

            // Add range info
            long totalLines = 1;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long pos = 0;
                while (true) {
                    raf.seek(pos);
                    int b = raf.read();
                    if (b < 0) break;
                    if (b == '\n') totalLines++;
                    pos++;
                }
            }
            result.append(context.getString(R.string.tool_file_read_range_info, totalLines, startKb, endKb, fileLen / 1024));

            return ok(ToolResult.truncateContent(result.toString()));
        } catch (Exception e) {
            return error(context.getString(R.string.tool_file_read_failed, e.getMessage()));
        }
    }

    /** 只读取文件的 [start, end) 字节区间，避免一次性加载整个文件。 */
    private static byte[] readRange(File file, long start, long end) throws Exception {
        long len = end - start;
        if (len <= 0) return new byte[0];
        byte[] buffer = new byte[(int) Math.min(len, Integer.MAX_VALUE - 8)];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            int read;
            int offset = 0;
            while (offset < buffer.length && (read = raf.read(buffer, offset, buffer.length - offset)) != -1) {
                offset += read;
            }
            if (offset == buffer.length) {
                return buffer;
            }
            byte[] trimmed = new byte[offset];
            System.arraycopy(buffer, 0, trimmed, 0, offset);
            return trimmed;
        }
    }

    private String addLineNumbers(String content, int startLine) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(startLine + i).append('\t').append(lines[i]);
            if (i + 1 < lines.length) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private void appendDirectory(StringBuilder builder, File dir, String parentPath, int[] count, ToolContext context) {
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
                builder.append(context.getString(R.string.tool_file_read_dir_truncated));
                return;
            }
            String relative = parentPath.length() == 0 ? item.getName() : parentPath + "/" + item.getName();
            if (item.isDirectory()) {
                builder.append("[DIR]  ").append(relative).append("/\n");
                count[0]++;
                appendDirectory(builder, item, relative, count, context);
            } else {
                builder.append("[FILE] ").append(relative).append('\n');
                count[0]++;
            }
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
