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

/**
 * Deletes one workspace file (never the root or a directory).
 *
 * <p>The refusal to delete directories keeps recursive removal out of reach of a single model
 * mistake; deletion records the prior content in the diff sink so it can be restored.</p>
 */
public final class DeleteFileTool implements Tool {

    /** {@inheritDoc} */
    @Override public String name() { return "delete_file"; }

    /** {@inheritDoc} */
    @Override public String description() { return "Delete one workspace file."; }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative file path")
                .required("path").build();
    }

    /** {@inheritDoc} */
    @Override public boolean needsLocalWorkspace() { return true; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return true; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Delete file", ToolIcon.DELETE,
                paths(arguments), ToolPresentation.reason(arguments));
    }

    /**
     * Collects the target paths from the legacy {@code paths} array and the conventional
     * single-path keys, tolerating models that use either argument shape.
     *
     * @param arguments parsed call arguments
     * @return one path per line, or the empty string when none is recognizable
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Refuses the workspace root, directories and non-files before any removal happens;
     * the recorded diff keeps the deleted content recoverable.</p>
     */
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
