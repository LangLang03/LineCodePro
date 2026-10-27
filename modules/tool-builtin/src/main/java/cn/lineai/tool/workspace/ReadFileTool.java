package cn.lineai.tool.workspace;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.lineai.tool.api.PathPolicy;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolResult;

/** Reads a UTF-8 text file inside the workspace. */
public final class ReadFileTool implements Tool {

    private static final int MAX_CHARS = 32_000;

    @Override public String name() { return "read_file"; }

    @Override public String description() { return "Read a UTF-8 text file from the workspace."; }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative path")
                .required("path").build();
    }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Read file", ToolIcon.READ,
                arguments.optString("path", ""), ToolPresentation.reason(arguments));
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            File file = context.resolvePath(arguments.getString("path"));
            if (!file.isFile()) return ToolResult.failure("", "Not a file: " + arguments.getString("path"));
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (content.length() > MAX_CHARS) {
                return ToolResult.truncated("", content.substring(0, MAX_CHARS)
                        + "\n… truncated " + (content.length() - MAX_CHARS) + " chars");
            }
            return ToolResult.success("", content);
        } catch (Exception error) {
            return ToolResult.failure("", message(error));
        }
    }

    static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
