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

/**
 * Creates or overwrites a workspace file.
 *
 * <p>Missing parent directories are created implicitly; the before/after snapshots are handed
 * to the {@code DiffSink} so history stays complete for both creation and overwrite.</p>
 */
public final class WriteFileTool implements Tool {

    /** {@inheritDoc} */
    @Override public String name() { return "write_file"; }

    /** {@inheritDoc} */
    @Override public String description() { return "Create or replace a workspace file."; }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative path")
                .string("content", "Full file content")
                .required("path").required("content").build();
    }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Write file", ToolIcon.WRITE,
                arguments.optString("path", ""), ToolPresentation.reason(arguments));
    }

    /** {@inheritDoc} */
    @Override public boolean needsLocalWorkspace() { return true; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return true; }

    /**
     * {@inheritDoc}
     *
     * <p>Refuses to run when mutations are disallowed or the parent directory cannot be
     * created; the recorded diff covers the whole file, not an incremental edit.</p>
     */
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
