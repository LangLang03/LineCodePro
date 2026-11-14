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

/**
 * Glob search rooted at the workspace.
 *
 * <p>Patterns that start with an absolute segment or contain {@code ..} are rejected before
 * walking, so the NIO glob matcher can never address anything outside the workspace. Matches
 * are capped to keep a broad pattern from flooding the context.</p>
 */
public final class GlobTool implements Tool {

    /** Maximum number of reported matches. */
    private static final int LIMIT = 200;

    /** {@inheritDoc} */
    @Override public String name() { return "glob"; }

    /** {@inheritDoc} */
    @Override public String description() { return "Find workspace paths matching a glob pattern."; }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("pattern", "Glob such as **/*.java")
                .required("pattern").build();
    }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Find files", ToolIcon.SEARCH,
                arguments.optString("pattern", ""), ToolPresentation.reason(arguments));
    }

    /** {@inheritDoc} */
    @Override public boolean needsLocalWorkspace() { return true; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /**
     * {@inheritDoc}
     *
     * <p>Returns workspace-relative paths, sorted, regular files only, at most
     * {@link #LIMIT} entries.</p>
     */
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
