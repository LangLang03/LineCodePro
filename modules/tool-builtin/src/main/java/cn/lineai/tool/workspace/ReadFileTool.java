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
 * Reads a UTF-8 text file inside the workspace.
 *
 * <p>Output is capped so a large file cannot flood the model context; the truncated result
 * reports how many characters were cut.</p>
 */
public final class ReadFileTool implements Tool {

    /** Maximum characters returned in one call. */
    private static final int MAX_CHARS = 32_000;

    /** {@inheritDoc} */
    @Override public String name() { return "read_file"; }

    /** {@inheritDoc} */
    @Override public String description() { return "Read a UTF-8 text file from the workspace."; }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative path")
                .required("path").build();
    }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Read file", ToolIcon.READ,
                arguments.optString("path", ""), ToolPresentation.reason(arguments));
    }

    /** {@inheritDoc} */
    @Override public boolean needsLocalWorkspace() { return true; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /**
     * {@inheritDoc}
     *
     * <p>Missing files are reported as failure results; oversized content comes back marked
     * {@link ToolResult#truncated}.</p>
     */
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

    /**
     * Best available message for an exception, falling back to the class name when the
     * exception carries none; shared by the file tools for uniform failure text.
     *
     * @param error exception caught by a tool
     * @return non-empty human-readable failure text
     */
    static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
