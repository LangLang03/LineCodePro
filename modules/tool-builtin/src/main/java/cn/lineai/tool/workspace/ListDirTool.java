package cn.lineai.tool.workspace;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;

import cn.lineai.tool.api.PathPolicy;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolResult;

/** Lists one workspace directory, directories first. */
public final class ListDirTool implements Tool {

    @Override public String name() { return "list_dir"; }

    @Override public String description() { return "List entries of a workspace directory."; }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative directory (default .)")
                .build();
    }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("List directory", ToolIcon.LIST,
                arguments.optString("path", "."), ToolPresentation.reason(arguments));
    }

    @Override public boolean needsLocalWorkspace() { return true; }

    @Override public boolean mutating() { return false; }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            String path = arguments.optString("path", ".");
            File dir = context.resolvePath(path);
            if (!dir.isDirectory()) return ToolResult.failure("", "Not a directory: " + path);
            File[] children = dir.listFiles();
            if (children == null) return ToolResult.success("", "");
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            StringBuilder out = new StringBuilder();
            for (File child : children) {
                if (out.length() > 0) out.append('\n');
                out.append(child.isDirectory() ? child.getName() + "/" : child.getName());
            }
            return ToolResult.success("", out.toString());
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }
}
