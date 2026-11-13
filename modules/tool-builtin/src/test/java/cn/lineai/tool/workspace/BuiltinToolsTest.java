package cn.lineai.tool.workspace;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.TodoStore;
import cn.lineai.tool.api.ToolResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltinToolsTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private File root;
    private ToolRegistry registry;
    private ToolContext context;

    @Before public void setUp() throws Exception {
        root = folder.newFolder("workspace");
        registry = BuiltinTools.registry();
        context = context(true);
    }

    private ToolContext context(final boolean mutationAllowed) {
        return new ToolContext() {
            @Override public File workspaceRoot() { return root; }
            @Override public String modelId() { return null; }
            @Override public boolean mutationAllowed() { return mutationAllowed; }
        };
    }

    private ToolResult run(String name, String json) throws Exception {
        Tool tool = registry.find(name);
        assertNotNull(name, tool);
        return tool.execute(context, new JSONObject(json));
    }

    @Test public void registryContainsEveryTool() {
        assertEquals(13, registry.all().size());
        assertNull(registry.find("missing"));
    }

    @Test public void remoteShellRunsThroughTheSshContext() throws Exception {
        final java.util.List<String> commands = new java.util.ArrayList<>();
        ToolContext ssh = new ToolContext() {
            @Override public File workspaceRoot() { return root; }
            @Override public String modelId() { return null; }
            @Override public boolean mutationAllowed() { return true; }
            @Override public boolean remoteWorkspace() { return true; }
            @Override public String remoteDirectory() { return "/remote/dir"; }
            @Override public String remoteExecute(String command, int timeoutMs) {
                commands.add(command);
                return "remote-output";
            }
        };
        ToolResult result = registry.find("shell")
                .execute(ssh, new JSONObject("{\"command\":\"pwd\"}"));
        assertFalse(result.error);
        assertEquals("remote-output", result.content.trim());
        assertEquals("cd '/remote/dir' && pwd", commands.get(0));
    }

    @Test public void remoteShellHonoursCwd() throws Exception {
        final java.util.List<String> commands = new java.util.ArrayList<>();
        ToolContext ssh = new ToolContext() {
            @Override public File workspaceRoot() { return root; }
            @Override public String modelId() { return null; }
            @Override public boolean mutationAllowed() { return true; }
            @Override public boolean remoteWorkspace() { return true; }
            @Override public String remoteDirectory() { return "/remote/dir"; }
            @Override public String remoteExecute(String command, int timeoutMs) {
                commands.add(command);
                return "";
            }
        };
        registry.find("shell")
                .execute(ssh, new JSONObject("{\"command\":\"ls\",\"cwd\":\"/tmp\"}"));
        assertEquals("cd '/tmp' && ls", commands.get(0));
    }

    @Test public void localFileToolsDeclareALocalWorkspace() {
        for (String name : new String[] {"read_file", "write_file", "edit_file", "delete_file",
                "glob", "list_dir"}) {
            assertTrue(name, registry.find(name).needsLocalWorkspace());
        }
        assertFalse(registry.find("shell").needsLocalWorkspace());
    }

    @Test public void presentationWithoutArgumentsNeverCrashes() {
        for (Tool tool : registry.all()) {
            assertFalse(tool.name(), registry.presentation(tool.name()).title.isEmpty());
        }
    }

    @Test public void shellRunsInsideTheWorkspace() throws Exception {
        Files.write(new File(root, "note.txt").toPath(), "content".getBytes(StandardCharsets.UTF_8));
        ToolResult result = run("shell", "{\"command\":\"cat note.txt\"}");
        assertFalse(result.content, result.error);
        assertEquals("content", result.content.trim());
    }

    @Test public void shellReportsNonZeroExit() throws Exception {
        ToolResult result = run("shell", "{\"command\":\"exit 3\"}");
        assertTrue(result.error);
        assertTrue(result.content.contains("exit 3"));
    }

    @Test public void shellRejectsCwdOutsideWorkspace() throws Exception {
        assertTrue(run("shell",
                "{\"command\":\"pwd\",\"cwd\":\"../\"}").error);
    }

    @Test public void shellHonoursTimeoutMsOnLocalRuns() throws Exception {
        long start = System.currentTimeMillis();
        ToolResult result = run("shell",
                "{\"command\":\"sleep 5\",\"timeout_ms\":1000}");
        assertTrue(result.error);
        assertTrue(result.content.contains("timed out"));
        assertTrue(System.currentTimeMillis() - start < 4000);
    }

    @Test public void backgroundStartReturnsATaskIdWithoutWaiting() throws Exception {
        final java.util.List<String> started = new java.util.ArrayList<>();
        ToolContext withBackground = new ToolContext() {
            @Override public File workspaceRoot() { return root; }
            @Override public String modelId() { return null; }
            @Override public boolean mutationAllowed() { return true; }
            @Override public cn.lineai.tool.api.BackgroundTasks backgrounds() {
                return new cn.lineai.tool.api.BackgroundTasks() {
                    @Override public String start(String command, String cwd, boolean remote) {
                        started.add(command);
                        return "bg-1";
                    }
                    @Override public String output(String id, boolean block, int timeoutMs) {
                        return "";
                    }
                    @Override public String kill(String id) { return ""; }
                    @Override public java.util.List<String> drainCompleted() {
                        return new java.util.ArrayList<>();
                    }
                    @Override public void shutdown() { }
                };
            }
        };
        long start = System.currentTimeMillis();
        ToolResult result = registry.find("shell").execute(withBackground, new JSONObject(
                "{\"command\":\"sleep 30\",\"background\":true}"));
        assertFalse(result.content, result.error);
        assertTrue(result.content.contains("bg-1"));
        assertTrue(result.content.contains("shell_output"));
        assertTrue(System.currentTimeMillis() - start < 2000);
        assertEquals(java.util.Collections.singletonList("sleep 30"), started);
    }

    @Test public void backgroundFailsCleanlyWithoutARegistry() throws Exception {
        ToolResult result = run("shell", "{\"command\":\"ls\",\"background\":true}");
        assertTrue(result.error);
        assertTrue(result.content.contains("Background tasks are unavailable"));
    }

    @Test public void shellOutputReadsFromTheRegistry() throws Exception {
        final java.util.List<Boolean> blocking = new java.util.ArrayList<>();
        ToolContext withBackground = new ToolContext() {
            @Override public File workspaceRoot() { return root; }
            @Override public String modelId() { return null; }
            @Override public boolean mutationAllowed() { return true; }
            @Override public cn.lineai.tool.api.BackgroundTasks backgrounds() {
                return new cn.lineai.tool.api.BackgroundTasks() {
                    @Override public String start(String command, String cwd, boolean remote) {
                        return "bg-2";
                    }
                    @Override public String output(String id, boolean block, int timeoutMs) {
                        blocking.add(block);
                        return "Task " + id + " finished (exit 0)\nhello";
                    }
                    @Override public String kill(String id) { return ""; }
                    @Override public java.util.List<String> drainCompleted() {
                        return new java.util.ArrayList<>();
                    }
                    @Override public void shutdown() { }
                };
            }
        };
        ToolResult read = registry.find("shell_output").execute(withBackground, new JSONObject(
                "{\"id\":\"bg-2\",\"block\":true,\"timeout_ms\":5000}"));
        assertFalse(read.error);
        assertTrue(read.content.contains("exit 0"));
        assertEquals(java.util.Collections.singletonList(true), blocking);
    }

    @Test public void shellOutputIsReadOnlyWhileKillMutates() {
        assertFalse(registry.find("shell_output").mutating());
        assertTrue(registry.find("shell_kill").mutating());
    }

    @Test public void shellKillRoutesThroughTheRegistry() throws Exception {
        final java.util.List<String> killed = new java.util.ArrayList<>();
        ToolContext withBackground = new ToolContext() {
            @Override public File workspaceRoot() { return root; }
            @Override public String modelId() { return null; }
            @Override public boolean mutationAllowed() { return true; }
            @Override public cn.lineai.tool.api.BackgroundTasks backgrounds() {
                return new cn.lineai.tool.api.BackgroundTasks() {
                    @Override public String start(String command, String cwd, boolean remote) {
                        return "";
                    }
                    @Override public String output(String id, boolean block, int timeoutMs) {
                        return "";
                    }
                    @Override public String kill(String id) {
                        killed.add(id);
                        return "Task " + id + " killed";
                    }
                    @Override public java.util.List<String> drainCompleted() {
                        return new java.util.ArrayList<>();
                    }
                    @Override public void shutdown() { }
                };
            }
        };
        ToolResult result = registry.find("shell_kill").execute(withBackground, new JSONObject(
                "{\"id\":\"bg-7\"}"));
        assertFalse(result.error);
        assertEquals(java.util.Collections.singletonList("bg-7"), killed);
    }

    @Test public void todoToolReplacesSessionState() throws Exception {
        final java.util.List<cn.lineai.core.model.TodoItem> items = new java.util.ArrayList<>();
        TodoStore store = new TodoStore() {
            public void replace(java.util.List<cn.lineai.core.model.TodoItem> next) {
                items.clear();
                items.addAll(next);
            }
            public java.util.List<cn.lineai.core.model.TodoItem> items() {
                return new java.util.ArrayList<>(items);
            }
        };
        ToolContext withTodos = new ToolContext() {
            public File workspaceRoot() { return root; }
            public String modelId() { return null; }
            public boolean mutationAllowed() { return true; }
            public TodoStore todos() { return store; }
        };
        ToolResult result = registry.find("todo_update").execute(withTodos, new JSONObject(
                "{\"items\":[{\"content\":\"read code\",\"status\":\"in_progress\"},"
                        + "{\"content\":\"write test\",\"status\":\"pending\"}]}"));
        assertFalse(result.content, result.error);
        assertEquals(2, items.size());
        assertEquals(cn.lineai.core.model.TodoItem.Status.IN_PROGRESS, items.get(0).status);
        assertTrue(registry.find("todo_update").execute(withTodos, new JSONObject(
                "{\"items\":[{\"content\":\"a\",\"status\":\"in_progress\"},"
                        + "{\"content\":\"b\",\"status\":\"in_progress\"}]}")).error);
    }

    @Test public void toolsCarryTheirOwnPresentation() {
        ToolPresentation read = registry.presentation("read_file", "{\"path\":\"a.txt\"}");
        assertEquals("Read file", read.title);
        assertEquals(ToolIcon.READ, read.icon);
        assertEquals("a.txt", read.action);

        ToolPresentation delete = registry.presentation("delete_file",
                "{\"paths\":[\"a\",\"b\"],\"reason\":\"cleanup\"}");
        assertEquals(ToolIcon.DELETE, delete.icon);
        assertEquals("a\nb", delete.action);
        assertEquals("cleanup", delete.reason);

        ToolPresentation unknown = registry.presentation("mcp_server_search",
                "{\"url\":\"https://example.com\"}");
        assertEquals("mcp_server_search", unknown.title);
        assertEquals(ToolIcon.GENERIC, unknown.icon);
        assertEquals("https://example.com", unknown.action);
    }

    @Test public void readsFile() throws Exception {
        File file = new File(root, "notes.txt");
        Files.write(file.toPath(), "hello tool".getBytes(StandardCharsets.UTF_8));
        ToolResult result = run("read_file", "{\"path\":\"notes.txt\"}");
        assertFalse(result.error);
        assertEquals("hello tool", result.content);
    }

    @Test public void readRejectsMissingAndEscapingPaths() throws Exception {
        assertTrue(run("read_file", "{\"path\":\"nope.txt\"}").error);
        assertTrue(run("read_file", "{\"path\":\"../outside.txt\"}").error);
        assertTrue(run("read_file", "{\"path\":\"/etc/passwd\"}").error);
    }

    @Test public void readTruncatesLargeFiles() throws Exception {
        File file = new File(root, "big.txt");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40_000; i++) text.append('x');
        Files.write(file.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
        ToolResult result = run("read_file", "{\"path\":\"big.txt\"}");
        assertFalse(result.error);
        assertTrue(result.truncated);
        assertTrue(result.content.startsWith("xxxx"));
        assertTrue(result.content.contains("truncated"));
    }

    @Test public void listsDirectoriesFirst() throws Exception {
        new File(root, "b.txt").createNewFile();
        new File(root, "a.txt").createNewFile();
        new File(root, "sub").mkdirs();
        ToolResult result = run("list_dir", "{}");
        assertFalse(result.error);
        assertEquals("sub/\na.txt\nb.txt", result.content);
    }

    @Test public void globFindsNestedMatchesAndRejectsEscapes() throws Exception {
        new File(root, "src/main").mkdirs();
        Files.write(new File(root, "src/main/A.java").toPath(), "a".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(root, "src/main/B.kt").toPath(), "b".getBytes(StandardCharsets.UTF_8));
        ToolResult result = run("glob", "{\"pattern\":\"**/*.java\"}");
        assertFalse(result.error);
        assertEquals("src/main/A.java", result.content);
        assertTrue(run("glob", "{\"pattern\":\"../**/*.java\"}").error);
    }

    @Test public void writesFileAndCreatesParents() throws Exception {
        ToolResult result = run("write_file", "{\"path\":\"deep/dir/out.txt\",\"content\":\"data\"}");
        assertFalse(result.error);
        assertEquals("data", new String(Files.readAllBytes(
                new File(root, "deep/dir/out.txt").toPath()), StandardCharsets.UTF_8));
    }

    @Test public void writeRejectedWithoutMutationPermission() throws Exception {
        ToolContext readOnly = context(false);
        ToolResult result = registry.find("write_file")
                .execute(readOnly, new JSONObject("{\"path\":\"a.txt\",\"content\":\"x\"}"));
        assertTrue(result.error);
        assertFalse(new File(root, "a.txt").exists());
    }

    @Test public void editReplacesFirstOccurrenceOnly() throws Exception {
        File file = new File(root, "code.txt");
        Files.write(file.toPath(), "one one one".getBytes(StandardCharsets.UTF_8));
        ToolResult result = run("edit_file",
                "{\"path\":\"code.txt\",\"old_string\":\"one\",\"new_string\":\"two\"}");
        assertFalse(result.error);
        assertEquals("two one one", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertTrue(run("edit_file",
                "{\"path\":\"code.txt\",\"old_string\":\"absent\",\"new_string\":\"x\"}").error);
    }

    @Test public void deletesOnlyRegularFiles() throws Exception {
        File file = new File(root, "gone.txt");
        Files.write(file.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertFalse(run("delete_file", "{\"path\":\"gone.txt\"}").error);
        assertFalse(file.exists());

        new File(root, "dir").mkdirs();
        assertTrue(run("delete_file", "{\"path\":\"dir\"}").error);
        assertTrue(run("delete_file", "{\"path\":\".\"}").error);
        assertTrue(run("delete_file", "{\"path\":\"missing.txt\"}").error);
    }
}
