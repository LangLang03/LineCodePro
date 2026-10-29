package cn.lineai.tool.workspace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.lineai.tool.api.PathPolicy;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** Deletes one workspace file (never the root or a directory). */
public final class DeleteFileTool implements Tool {

    @Override public String name() { return "delete_file"; }

    @Override public String description() { return "Delete one workspace file."; }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative file path")
                .required("path").build();
    }

    @Override public boolean needsLocalWorkspace() { return true; }

    @Override public boolean mutating() { return true; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Delete file", ToolIcon.DELETE,
                paths(arguments), ToolPresentation.reason(arguments));
    }

    private static String paths(JSONObject arguments) {
        StringBuilder out = new StringBuilder();
        JSONArray list = arguments.optJSONArray("paths");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                if (out.length() > 0) out.append('\n');
                out.append(list.optString(i));
            }
        }
        for (String field : new String[] {"file_path", "path"}) {
            String value = arguments.optString(field, "");
            if (!value.isEmpty()) {
                if (out.length() > 0) out.append('\n');
                out.append(value);
            }
        }
        return out.toString();
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            if (!context.mutationAllowed()) return ToolResult.failure("", "Mutation not allowed");
            String path = arguments.getString("path");
            File file = context.resolvePath(path);
            if (file.equals(context.workspaceRoot().getCanonicalFile())) {
                return ToolResult.failure("", "Refusing to delete the workspace root");
            }
            if (file.isDirectory()) return ToolResult.failure("", "Refusing to delete a directory");
            if (!file.isFile()) return ToolResult.failure("", "Not a file: " + path);
            String before = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (!file.delete()) return ToolResult.failure("", "Delete failed: " + path);
            if (context.diffs() != null) {
                context.diffs().record(path, before, "", true);
            }
            return ToolResult.success("", "Deleted " + path);
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }
}
