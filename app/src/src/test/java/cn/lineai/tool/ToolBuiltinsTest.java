package cn.lineai.tool;

import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.WebSearchTool;
import cn.lineai.tool.builtin.WebFetchTool;
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
    public void fileReadReturnsNumberedLineRange() throws Exception {
        File file = folder.newFile("demo.txt");
        Files.write(file.toPath(), "one\ntwo\nthree\n".getBytes(StandardCharsets.UTF_8));

        ToolResult result = new FileReadTool().execute(new JSONObject()
                .put("file_path", "demo.txt")
                .put("start_line", 2)
                .put("end_line", 3), context());

        Assert.assertFalse(result.isError());
        Assert.assertTrue(result.getContent().contains("2\ttwo"));
        Assert.assertTrue(result.getContent().contains("3\tthree"));
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
        Assert.assertTrue(result.getContent().contains("超出当前工作区"));
    }

    @Test
    public void fileDeleteRequiresReason() throws Exception {
        File file = folder.newFile("delete-me.txt");

        ToolResult result = new FileDeleteTool().execute(new JSONObject()
                .put("paths", new org.json.JSONArray().put("delete-me.txt")), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("reason"));
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
        Assert.assertTrue(result.getContent().contains("成功删除"));
    }

    @Test
    public void webSearchFailsClearlyWhenNotConfigured() throws Exception {
        ToolResult result = new WebSearchTool().execute(new JSONObject().put("query", "LineAI"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("网页搜索未配置"));
    }

    @Test
    public void webFetchRejectsRemoteCleartextHttp() throws Exception {
        ToolResult result = new WebFetchTool().execute(new JSONObject().put("url", "http://example.com"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("HTTP 明文"));
    }

    @Test
    public void agentFailsClearlyWhenRunnerMissing() throws Exception {
        ToolResult result = new AgentTool().execute(new JSONObject()
                .put("type", "explore")
                .put("description", "检查代码")
                .put("prompt", "读取入口文件"), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("Agent 执行器未接入"));
    }

    @Test
    public void agentExploreRejectsWriteScope() throws Exception {
        ToolResult result = new AgentTool().execute(new JSONObject()
                .put("type", "explore")
                .put("description", "检查代码")
                .put("prompt", "只读检查入口")
                .put("write_scope", new org.json.JSONArray().put("app/src/main/java")), context());

        Assert.assertTrue(result.isError());
        Assert.assertTrue(result.getContent().contains("explore Agent 不能声明 write_scope"));
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
        Assert.assertTrue(result.getContent().contains("Agent 不能依赖自身: scan"));
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
        Assert.assertTrue(result.getContent().contains("sub-coding Agent 必须声明 write_scope"));
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
        Assert.assertTrue(result.getContent().contains("多个 Agent 不能写同一文件或重叠目录"));
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
        Assert.assertTrue(result.getContent().contains("多个 Agent 不能写同一文件或重叠目录"));
    }

    private ToolContext context() {
        return new ToolContext(folder.getRoot().getAbsolutePath());
    }
}
