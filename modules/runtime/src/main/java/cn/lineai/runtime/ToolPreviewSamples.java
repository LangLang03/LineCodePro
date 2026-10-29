package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.feature.chat.toolcall.ToolCallEntry;
import cn.lineai.feature.chat.toolcall.ToolRunStatus;
import cn.lineai.feature.chat.toolcall.ToolDiffLookup;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;

/**
 * Fake call/result pairs for the tool-call preview page.
 *
 * <p>Pure data with no Android dependencies beyond the category label resources. Each entry pairs
 * a real {@link ToolCallEntry} with the {@link ToolPresentation} the matching tool would supply, so
 * the preview exercises the production renderers instead of a parallel mock UI.</p>
 */
public final class ToolPreviewSamples {

    /** One preview row: a category heading plus the call and its presentation metadata. */
    public static final class Sample {
        public final int category;
        public final ToolCallEntry entry;
        public final ToolPresentation presentation;
        public final boolean expanded;

        Sample(int category, ToolCallEntry entry, ToolPresentation presentation,
               boolean expanded) {
            this.category = category;
            this.entry = entry;
            this.presentation = presentation;
            this.expanded = expanded;
        }
    }

    private static final String ACTIVITY =
            "app/src/main/java/cn/lineai/MainActivity.java";
    private static final Map<String, ToolPresentation> PRESENTATIONS = presentations();

    private ToolPreviewSamples() {
    }

    /** All categories in display order: read, write, edit, diff, delete, shell, todo... */
    public static List<Sample> all() {
        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample(R.string.toolcall_preview_category_read,
                new ToolCallEntry("preview-read", "read_file",
                        "{\"path\":\"" + ACTIVITY + "\"}",
                        "Read 128 lines (3.2 KB)", false, false),
                PRESENTATIONS.get("read_file"), false));

        samples.add(new Sample(R.string.toolcall_preview_category_read,
                new ToolCallEntry("preview-list", "list_dir",
                        "{\"path\":\"app/src/main/java/cn/lineai\"}",
                        "MainActivity.java\nChatScreenView.java\nProcessPanel.java",
                        false, false),
                PRESENTATIONS.get("list_dir"), false));

        samples.add(new Sample(R.string.toolcall_preview_category_read,
                new ToolCallEntry("preview-glob", "glob",
                        "{\"pattern\":\"**/*.java\"}",
                        "42 matches", false, false),
                PRESENTATIONS.get("glob"), false));

        samples.add(new Sample(R.string.toolcall_preview_category_write,
                new ToolCallEntry("preview-write", "write_file",
                        "{\"path\":\"" + ACTIVITY + "\",\"content\":\""
                                + "package cn.lineai;\\n\\npublic final class MainActivity {\\n"
                                + "    private static final int COUNT = 3;\\n"
                                + "    private static final String TITLE = \\\"LineCode\\\";\\n}\\n\"}",
                        "Wrote " + ACTIVITY, false, false, "preview-diff", ""),
                PRESENTATIONS.get("write_file"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_edit,
                new ToolCallEntry("preview-edit", "edit_file",
                        "{\"path\":\"" + ACTIVITY + "\",\"old_string\":\"return 0;\","
                                + "\"new_string\":\"return COUNT;\"}",
                        "Edited " + ACTIVITY, false, false, "", ""),
                PRESENTATIONS.get("edit_file"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_diff,
                new ToolCallEntry("preview-diff", "file_diff",
                        "{\"path\":\"" + ACTIVITY + "\"}",
                        "3 files changed", false, false, "preview-diff-edit",
                        ToolRunStatus.ACCEPTED.wire),
                PRESENTATIONS.get("file_diff"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_delete,
                new ToolCallEntry("preview-delete", "delete_file",
                        "{\"paths\":[\"app/src/main/java/cn/lineai/TodoActivity.java\"],"
                                + "\"reason\":\"Screen removed in the rewrite\"}",
                        "Deleted 1 file", false, false),
                PRESENTATIONS.get("delete_file"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_shell,
                new ToolCallEntry("preview-shell", "shell_execute",
                        "{\"command\":\"gradlew :app:assembleDebug\"}",
                        "BUILD SUCCESSFUL in 12s", false, false),
                PRESENTATIONS.get("shell_execute"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_shell,
                new ToolCallEntry("preview-shell-running", "shell_execute",
                        "{\"command\":\"gradlew :app:assembleDebug\"}",
                        "", false, true),
                PRESENTATIONS.get("shell_execute"), false));

        samples.add(new Sample(R.string.toolcall_preview_category_todo,
                new ToolCallEntry("preview-todo", "todo_update",
                        "{\"items\":["
                                + "{\"content\":\"Add unit tests for the parser\",\"status\":\"completed\"},"
                                + "{\"content\":\"Port the tool cards\",\"status\":\"in_progress\"},"
                                + "{\"content\":\"Ship the preview page\",\"status\":\"pending\"}]}",
                        "TODO list updated: 3 tasks, 1 done", false, false),
                PRESENTATIONS.get("todo_update"), false));

        samples.add(new Sample(R.string.toolcall_preview_category_memory,
                new ToolCallEntry("preview-memory", "memory_update",
                        "{\"title\":\"Build command\",\"content\":\"Always run the offline "
                                + "Gradle compile before claiming a UI change compiles.\","
                                + "\"scope\":\"project\"}",
                        "Memory saved", false, false),
                PRESENTATIONS.get("memory_update"), false));

        samples.add(new Sample(R.string.toolcall_preview_category_image,
                new ToolCallEntry("preview-image", "image_generation",
                        "{\"prompt\":\"A terminal-themed wallpaper\"}",
                        "Image saved to generated/image-1042.png", false, false),
                PRESENTATIONS.get("image_generation"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_mcp,
                new ToolCallEntry("preview-mcp", "mcpx_github",
                        "{\"repo\":\"linecode\",\"labels\":[\"bug\"]}",
                        "Fetched 3 issues", false, false),
                PRESENTATIONS.get("mcpx_github"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_agent,
                new ToolCallEntry("preview-agent", "agent_explore",
                        "{\"task\":\"Explore the codebase structure\"}",
                        agentResult(), false, false),
                PRESENTATIONS.get("agent_explore"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_pipeline,
                new ToolCallEntry("preview-pipeline", "agent_pipeline",
                        pipelineArguments(), pipelineResult(), false, false),
                PRESENTATIONS.get("agent_pipeline"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_generic,
                new ToolCallEntry("preview-generic", "unknown_tool",
                        "{\"query\":\"linecode\"}", "Completed", false, false),
                PRESENTATIONS.get("unknown_tool"), true));

        samples.add(new Sample(R.string.toolcall_preview_category_phone,
                new ToolCallEntry("preview-phone", "phone_click",
                        "{\"x\":100,\"y\":200}", "Clicked (100, 200)", false, false),
                PRESENTATIONS.get("phone_click"), false));
        return samples;
    }

    /** Presentation for one sample/nested tool name; falls back to generic metadata. */
    public static ToolPresentation presentation(String name) {
        ToolPresentation value = PRESENTATIONS.get(name);
        return value == null ? ToolPresentation.generic(name) : value;
    }

    /** Fake snapshots so the write/edit/diff cards show a real line diff. */
    public static ToolDiffLookup diffLookup() {
        return diffId -> {
            if ("preview-diff".equals(diffId)) {
                return new ToolDiffLookup.Snapshot(ACTIVITY,
                        "package cn.lineai;\n\npublic final class MainActivity {\n"
                                + "    private static final String TITLE = \"LineCode\";\n}\n",
                        "package cn.lineai;\n\npublic final class MainActivity {\n"
                                + "    private static final int COUNT = 3;\n"
                                + "    private static final String TITLE = \"LineCode\";\n}\n");
            }
            if ("preview-diff-edit".equals(diffId)) {
                return new ToolDiffLookup.Snapshot(ACTIVITY,
                        "public int count() {\n    return 0;\n}\n",
                        "public int count() {\n    return COUNT;\n}\n");
            }
            return null;
        };
    }

    private static String agentResult() {
        return "{\"linecode_agent_progress\":true,\"status\":\"done\","
                + "\"description\":\"Explore the codebase structure\",\"type\":\"explore\","
                + "\"agent_id\":\"agent-1\",\"output\":\"Found the chat timeline, the settings "
                + "family and the tool renderers.\",\"tool_call_count\":2,\"tool_calls\":["
                + "{\"id\":\"nested-1\",\"name\":\"read_file\",\"arguments\":"
                + "{\"path\":\"" + ACTIVITY + "\"},\"result\":{\"content\":"
                + "\"Read 128 lines\",\"is_error\":false}},"
                + "{\"id\":\"nested-2\",\"name\":\"shell_execute\",\"arguments\":"
                + "{\"command\":\"find . -name '*.java' | wc -l\"},\"result\":"
                + "{\"content\":\"42\",\"is_error\":false}}]}";
    }

    private static String pipelineArguments() {
        return "{\"agents\":["
                + "{\"id\":\"scan\",\"description\":\"Scan the layout code\","
                + "\"type\":\"explore\"},"
                + "{\"id\":\"summarize\",\"description\":\"Summarize the findings\","
                + "\"type\":\"sub-coding\",\"depends_on\":[\"scan\"]},"
                + "{\"id\":\"verify\",\"description\":\"Verify the build\","
                + "\"type\":\"sub-coding\",\"depends_on\":[\"summarize\"]}]}";
    }

    private static String pipelineResult() {
        return "{\"linecode_agent_pipeline_progress\":true,\"status\":\"done\","
                + "\"summary\":\"Scanned 3 areas and verified the build.\",\"agents\":["
                + "{\"id\":\"scan\",\"status\":\"done\",\"output\":\"Found 3 suspicious layouts.\"},"
                + "{\"id\":\"summarize\",\"status\":\"done\",\"output\":\"2 fixes required.\"},"
                + "{\"id\":\"verify\",\"status\":\"error\",\"error\":true,"
                + "\"output\":\"Build failed: missing string resource.\"}]}";
    }

    private static Map<String, ToolPresentation> presentations() {
        Map<String, ToolPresentation> values = new LinkedHashMap<>();
        values.put("read_file", new ToolPresentation("Read file", ToolIcon.READ, ACTIVITY, ""));
        values.put("list_dir", new ToolPresentation("List directory", ToolIcon.LIST,
                "app/src/main/java/cn/lineai", ""));
        values.put("glob", new ToolPresentation("Find files", ToolIcon.SEARCH, "**/*.java", ""));
        values.put("write_file", new ToolPresentation("Write file", ToolIcon.WRITE, ACTIVITY, ""));
        values.put("edit_file", new ToolPresentation("Edit file", ToolIcon.EDIT, ACTIVITY, ""));
        values.put("file_diff", new ToolPresentation("File diff", ToolIcon.DIFF, ACTIVITY, ""));
        values.put("delete_file", new ToolPresentation("Delete file", ToolIcon.DELETE,
                "app/src/main/java/cn/lineai/TodoActivity.java", ""));
        values.put("shell_execute", new ToolPresentation("Terminal", ToolIcon.TERMINAL,
                "gradlew :app:assembleDebug", ""));
        values.put("todo_update", new ToolPresentation("TODO update", ToolIcon.TODO,
                "3 items", ""));
        values.put("memory_update", new ToolPresentation("Save memory", ToolIcon.MEMORY,
                "Build command", ""));
        values.put("image_generation", new ToolPresentation("Image generation", ToolIcon.SPARKLES,
                "A terminal-themed wallpaper", ""));
        values.put("mcpx_github", new ToolPresentation("GitHub · search_issues", ToolIcon.MCP,
                "linecode", ""));
        values.put("agent_explore", new ToolPresentation("Explore agent", ToolIcon.AGENT,
                "Explore the codebase structure", ""));
        values.put("agent_pipeline", new ToolPresentation("Agent Pipeline", ToolIcon.PIPELINE,
                "3 stages", ""));
        values.put("unknown_tool", new ToolPresentation("Unknown tool", ToolIcon.GENERIC,
                "linecode", ""));
        values.put("phone_click", new ToolPresentation("Phone click", ToolIcon.PHONE,
                "100, 200", ""));
        return Collections.unmodifiableMap(values);
    }
}
