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

/** Replaces the first exact occurrence of a snippet. */
public final class EditFileTool implements Tool {

    @Override public String name() { return "edit_file"; }

    @Override public String description() { return "Replace the first exact match of old_string."; }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative path")
                .string("old_string", "Exact text to replace")
                .string("new_string", "Replacement text")
                .required("path").required("old_string").required("new_string").build();
    }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Edit file", ToolIcon.EDIT,
                arguments.optString("path", ""), ToolPresentation.reason(arguments));
    }

    @Override public boolean mutating() { return true; }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            if (!context.mutationAllowed()) return ToolResult.failure("", "Mutation not allowed");
            String path = arguments.getString("path");
            File file = context.resolvePath(path);
            if (!file.isFile()) return ToolResult.failure("", "Not a file: " + path);
            String oldText = arguments.getString("old_string");
            if (oldText.isEmpty()) return ToolResult.failure("", "old_string must not be empty");
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            int index = content.indexOf(oldText);
            if (index < 0) return ToolResult.failure("", "old_string not found in " + path);
            String updated = content.substring(0, index) + arguments.optString("new_string", "")
                    + content.substring(index + oldText.length());
            Files.write(file.toPath(), updated.getBytes(StandardCharsets.UTF_8));
            if (context.diffs() != null) {
                context.diffs().record(path, content, updated, true);
            }
            return ToolResult.success("", "Edited " + path);
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }
}
