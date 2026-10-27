package cn.lineai.tool.workspace;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.tool.api.PathPolicy;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolResult;

/** Glob search rooted at the workspace. */
public final class GlobTool implements Tool {

    private static final int LIMIT = 200;

    @Override public String name() { return "glob"; }

    @Override public String description() { return "Find workspace paths matching a glob pattern."; }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("pattern", "Glob such as **/*.java")
                .required("pattern").build();
    }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Find files", ToolIcon.SEARCH,
                arguments.optString("pattern", ""), ToolPresentation.reason(arguments));
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            String pattern = arguments.getString("pattern");
            if (pattern.startsWith("/") || pattern.contains("..")) {
                return ToolResult.failure("", "Pattern must stay inside the workspace");
            }
            Path root = context.workspaceRoot().getCanonicalFile().toPath();
            java.nio.file.PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> matches = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> matcher.matches(root.relativize(path)))
                        .sorted()
                        .forEach(path -> {
                            if (matches.size() < LIMIT) matches.add(root.relativize(path).toString());
                        });
            }
            StringBuilder out = new StringBuilder();
            for (String match : matches) {
                if (out.length() > 0) out.append('\n');
                out.append(match);
            }
            return ToolResult.success("", out.toString());
        } catch (Exception error) {
            return ToolResult.failure("", ReadFileTool.message(error));
        }
    }
}
