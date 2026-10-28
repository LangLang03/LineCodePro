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

/** Creates or overwrites a workspace file. */
public final class WriteFileTool implements Tool {

    @Override public String name() { return "write_file"; }

    @Override public String description() { return "Create or replace a workspace file."; }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative path")
                .string("content", "Full file content")
                .required("path").required("content").build();
    }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Write file", ToolIcon.WRITE,
                arguments.optString("path", ""), ToolPresentation.reason(arguments));
    }

    @Override public boolean mutating() { return true; }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            if (!context.mutationAllowed()) return ToolResult.failure("", "Mutation not allowed");
            File file = context.resolvePath(arguments.getString("path"));
            boolean existed = file.isFile();
            String before = existed
                    ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return ToolResult.failure("", "Cannot create parent directory");
            }
            String content = arguments.optString("content", "");
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            if (context.diffs() != null) {
                context.diffs().record(arguments.getString("path"), before, content, existed);
            }
            return ToolResult.success("", "Wrote " + arguments.getString("path"));
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }
}
