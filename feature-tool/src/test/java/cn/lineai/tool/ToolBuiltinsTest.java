package cn.lineai.tool;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.WebSearchTool;
import cn.lineai.tool.builtin.WebFetchTool;
import cn.lineai.tool.builtin.TodoUpdateTool;
import cn.lineai.tool.builtin.ListDirectoryTool;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ToolBuiltinsTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void fileReadReturnsNumberedLines() throws Exception {
        File file = folder.newFile("demo.txt");
        Files.write(file.toPath(), "one\ntwo\nthree\n".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "demo.txt"), context());

        Assert.assertFalse(result.isError());
        // No phantom empty trailing line after the final newline.
        Assert.assertEquals("1\tone\n2\ttwo\n3\tthree", result.getContent());
    }

    @Test
    public void fileReadWithKbRange() throws Exception {
        File file = folder.newFile("demo.txt");
        Files.write(file.toPath(), "one\ntwo\nthree\n".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "demo.txt")
                .put("start_kb", 0)
                .put("end_kb", 50), context());

        Assert.assertFalse(result.isError());
        Assert.assertTrue(result.getContent().contains("1\tone"));
        Assert.assertTrue(result.getContent().contains("2\ttwo"));
        Assert.assertTrue(result.getContent().contains("3\tthree"));
    }

    @Test
    public void fileReadLargeFileWithKbRangeDoesNotError() throws Exception {
        // A file larger than 1MB must be readable via start_kb/end_kb without
        // being rejected outright.
        File file = folder.newFile("big.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200000; i++) {
            sb.append("line ").append(i).append("\n");
        }
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(file.length() > 1024 * 1024);

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "big.txt")
                .put("start_kb", 0)
                .put("end_kb", 50), context());

        Assert.assertFalse(result.isError());
        Assert.assertTrue(result.getContent().contains("1\tline 0"));
        // The whole 50KB page must be returned, not just the first line.
        Assert.assertTrue(result.getContent().contains("2\tline 1"));
        Assert.assertTrue(result.getContent().contains("100\tline 99"));
    }

    @Test
    public void fileReadKbMidFileChunkReturnsCompleteLines() throws Exception {
        File file = folder.newFile("big.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200000; i++) {
            sb.append("line ").append(i).append("\n");
        }
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "big.txt")
                .put("start_kb", 10)
                .put("end_kb", 11), context());

        Assert.assertFalse(result.isError());
        String content = result.getContent();
        String[] lines = content.split("\n");
        Assert.assertTrue("expected several complete lines, got: " + content, lines.length >= 3);
        int first = Integer.parseInt(lines[0].substring(0, lines[0].indexOf('\t')));
        int second = Integer.parseInt(lines[1].substring(0, lines[1].indexOf('\t')));
        Assert.assertEquals(first + 1, second);
        Assert.assertTrue("mid-file chunk must not start at line 1", first > 1);
    }

    @Test
    public void fileReadEndKbBeyond50ReadsPastFirst50Kb() throws Exception {
        File file = folder.newFile("big.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200000; i++) {
            sb.append("line ").append(i).append("\n");
        }
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(file.length() > 1024 * 1024);

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "big.txt")
                .put("start_kb", 0)
                .put("end_kb", 100), context());

        Assert.assertFalse(result.isError());
        Assert.assertTrue(result.getContent().contains("1\tline 0"));
        // end_kb above 50 must be honored: the range info reports KB 0-100
        // instead of silently clamping to 50.
        Assert.assertTrue(result.getContent().contains("showing KB 0-100"));
    }

    @Test
    public void fileReadRangeWithoutKbOnLargeFileErrors() throws Exception {
        // Without a KB range, a >= 50KB file must still be rejected.
        File file = folder.newFile("medium.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append("line ").append(i).append("\n");
        }
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(file.length() > 50 * 1024);

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "medium.txt"), context());

        Assert.assertTrue(result.isError());
    }

    @Test
    public void fileWriteCreatesParentDirectories() throws Exception {
        ToolResult result = new FileWriteTool().execute(new JSONObject()
                .put("file_path", "src/main.txt")
                .put("content", "hello"), context());

        Assert.assertFalse(result.isError());
        Assert.assertEquals("hello", new String(Files.readAllBytes(new File(folder.getRoot(), "src/main.txt").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void fileEditReplacesUniqueMatchOnce() throws Exception {
        File file = folder.newFile("edit.txt");
        Files.write(file.toPath(), "alpha\nbeta\nalpha\n".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileEditTool().execute(new JSONObject()
                .put("file_path", "edit.txt")
                .put("old_string", "beta")
                .put("new_string", "gamma"), context());

        Assert.assertFalse(result.isError());
        Assert.assertEquals(
                "alpha\ngamma\nalpha\n",
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        );
        Assert.assertTrue(result.getContent().contains("1 match"));
    }

    @Test
    public void fileEditRejectsMultipleMatchesWithoutReplaceAll() throws Exception {
        File file = folder.newFile("edit-multi.txt");
        Files.write(file.toPath(), "x\nx\n".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileEditTool().execute(new JSONObject()
                .put("file_path", "edit-multi.txt")
                .put("old_string", "x")
                .put("new_string", "y"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("matched 2 places")
                || result.getContent().contains("replace_all"));
        Assert.assertEquals("x\nx\n", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void fileEditReplaceAllReplacesEveryOccurrence() throws Exception {
        File file = folder.newFile("edit-all.txt");
        Files.write(file.toPath(), "x\nx\n".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileEditTool().execute(new JSONObject()
                .put("file_path", "edit-all.txt")
                .put("old_string", "x")
                .put("new_string", "y")
                .put("replace_all", true), context());

        Assert.assertFalse(result.isError());
        Assert.assertEquals("y\ny\n", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        Assert.assertTrue(result.getContent().contains("2 match"));
    }

    @Test
    public void globFindsFilesAndSkipsNodeModules() throws Exception {
        File src = folder.newFolder("src");
        Files.write(new File(src, "Main.java").toPath(), "class Main {}".getBytes(StandardCharsets.UTF_8));
        File nodeModules = folder.newFolder("node_modules");
        Files.write(new File(nodeModules, "Skip.java").toPath(), "class Skip {}".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new GlobTool().execute(new JSONObject().put("pattern", "**/*.java"), context());

        Assert.assertFalse(result.isError());
        Assert.assertTrue(result.getContent().contains("src/Main.java"));
        Assert.assertFalse(result.getContent().contains("node_modules/Skip.java"));
    }

    @Test
    public void fileWriteRejectsPathTraversalOutsideWorkspace() throws Exception {
        ToolResult result = new FileWriteTool().execute(new JSONObject()
                .put("file_path", "../outside.txt")
                .put("content", "bad"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Path is outside the current workspace"));
    }

    @Test
    public void fileDeleteRequiresReason() throws Exception {
        File file = folder.newFile("delete-me.txt");

        ToolResult result = new FileDeleteTool().execute(new JSONObject()
                .put("paths", new org.json.JSONArray().put("delete-me.txt")), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Deletion reason cannot be empty"));
        Assert.assertTrue(file.exists());
    }

    @Test
    public void fileDeleteDeletesRequestedPaths() throws Exception {
        File dir = folder.newFolder("target");
        File file = new File(dir, "nested.txt");
        Files.write(file.toPath(), "remove".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileDeleteTool().execute(new JSONObject()
                .put("reason", "清理测试文件")
                .put("paths", new org.json.JSONArray().put("target")), context());

        Assert.assertFalse(result.isError());
        Assert.assertFalse(dir.exists());
        Assert.assertTrue(result.getContent().contains("Successfully deleted"));
    }

    @Test
    public void webSearchFailsClearlyWhenNotConfigured() throws Exception {
        WebSearchConfigRepository tavilyRepo = new WebSearchConfigRepository() {
            @Override
            public WebSearchConfig get() {
                return WebSearchConfig.defaultConfig(WebSearchConfig.PROVIDER_TAVILY);
            }
        };
        ToolResult result = new WebSearchTool(tavilyRepo).execute(new JSONObject().put("query", "LineAI"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Web search not configured"));
    }

    @Test
    public void webFetchRejectsRemoteCleartextHttp() throws Exception {
        ToolResult result = new WebFetchTool().execute(new JSONObject().put("url", "http://example.com"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("cleartext"));
    }

    @Test
    public void agentFailsClearlyWhenRunnerMissing() throws Exception {
        ToolResult result = new AgentTool().execute(new JSONObject()
                .put("type", "explore")
                .put("description", "检查代码")
                .put("prompt", "读取入口文件"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Agent runner not available"));
    }

    @Test
    public void agentExploreRejectsWriteScope() throws Exception {
        ToolResult result = new AgentTool().execute(new JSONObject()
                .put("type", "explore")
                .put("description", "检查代码")
                .put("prompt", "只读检查入口")
                .put("write_scope", new org.json.JSONArray().put("app/src/main/java")), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("explore Agent cannot declare write_scope"));
    }

    @Test
    public void agentPipelineRejectsSelfDependency() throws Exception {
        ToolResult result = new AgentPipelineTool().execute(new JSONObject()
                .put("agents", new org.json.JSONArray()
                        .put(new JSONObject()
                                .put("id", "scan")
                                .put("type", "explore")
                                .put("description", "扫描代码")
                                .put("prompt", "读取入口")
                                .put("depends_on", new org.json.JSONArray().put("scan")))), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Agent cannot depend on itself: scan"));
    }

    @Test
    public void agentPipelineRequiresSubCodingWriteScope() throws Exception {
        ToolResult result = new AgentPipelineTool().execute(new JSONObject()
                .put("agents", new org.json.JSONArray()
                        .put(new JSONObject()
                                .put("id", "ui")
                                .put("type", "sub-coding")
                                .put("description", "修改 UI")
                                .put("prompt", "实现按钮样式"))), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("sub-coding Agent must declare write_scope"));
    }

    @Test
    public void agentPipelineRejectsDuplicateWriteScope() throws Exception {
        org.json.JSONArray agents = new org.json.JSONArray()
                .put(new JSONObject()
                        .put("id", "button")
                        .put("type", "sub-coding")
                        .put("description", "修改按钮")
                        .put("prompt", "实现按钮样式")
                        .put("write_scope", new org.json.JSONArray().put("app/src/main/java/cn/lineai/ui/ButtonView.java")))
                .put(new JSONObject()
                        .put("id", "theme")
                        .put("type", "sub-coding")
                        .put("description", "修改主题")
                        .put("prompt", "实现主题样式")
                        .put("write_scope", new org.json.JSONArray().put("app/src/main/java/cn/lineai/ui/ButtonView.java")));

        ToolResult result = new AgentPipelineTool().execute(new JSONObject().put("agents", agents), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Multiple Agents cannot write the same file"));
    }

    @Test
    public void agentPipelineRejectsNestedWriteScope() throws Exception {
        org.json.JSONArray agents = new org.json.JSONArray()
                .put(new JSONObject()
                        .put("id", "ui")
                        .put("type", "sub-coding")
                        .put("description", "修改 UI")
                        .put("prompt", "实现 UI")
                        .put("write_scope", new org.json.JSONArray().put("app/src/main/java/cn/lineai/ui")))
                .put(new JSONObject()
                        .put("id", "button")
                        .put("type", "sub-coding")
                        .put("description", "修改按钮")
                        .put("prompt", "实现按钮")
                        .put("write_scope", new org.json.JSONArray().put("app/src/main/java/cn/lineai/ui/ButtonView.java")));

        ToolResult result = new AgentPipelineTool().execute(new JSONObject().put("agents", agents), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Multiple Agents cannot write the same file"));
    }

    private ToolContext context() {
        return ToolContext.builder()
            .homePath(folder.getRoot().getAbsolutePath())
            .stringResolver(new FakeResourceContext())
            .build();
    }

    @Test
    public void builtinToolsDeclareViewClasses() {
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallReadView.class, new FileReadTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallWriteView.class, new FileWriteTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallWriteView.class, new FileEditTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallDeleteView.class, new FileDeleteTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallReadView.class, new GlobTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallReadView.class, new ListDirectoryTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallAgentView.class, new AgentTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallAgentPipelineView.class, new AgentPipelineTool().getToolCallViewClass());
        Assert.assertEquals(cn.lineai.tool.ui.ToolCallTodoView.class, new TodoUpdateTool().getToolCallViewClass());
        Assert.assertNull(new WebFetchTool().getToolCallViewClass());
        Assert.assertNull(new WebSearchTool(new cn.lineai.data.repository.WebSearchConfigRepository(null)).getToolCallViewClass());
    }
}
