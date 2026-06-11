package cn.lineai.data.repository;

import cn.lineai.model.McpToolConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class ToolSettingsRepositoryTest {
    @Test
    public void toolPromptUsesRegisteredToolMetadata() {
        ToolRegistry registry = new ToolRegistry();
        Map<String, BaseTool> toolByName = new LinkedHashMap<>();
        for (BaseTool tool : registry.getAll()) {
            toolByName.put(tool.getName(), tool);
        }
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("file_read");
        enabled.add("web_search");
        enabled.add("image_understanding");
        enabled.add("image_generation");
        enabled.add("agent");
        enabled.add("agent_pipeline");
        McpToolConfig config = new McpToolConfig(
                "mixed",
                "动态工具",
                "",
                true,
                new String[] {"file_read", "web_search", "image_understanding", "image_generation", "agent", "agent_pipeline", "shell_execute"}
        );

        String prompt = ToolSettingsRepository.renderToolPrompt(
                java.util.Collections.singletonList(config),
                enabled,
                toolByName,
                false
        );

        Assert.assertTrue(prompt.contains("file_read [read]"));
        Assert.assertTrue(prompt.contains("读取文件内容"));
        Assert.assertTrue(prompt.contains("\"file_path\""));
        Assert.assertTrue(prompt.contains("web_search [read]"));
        Assert.assertTrue(prompt.contains("\"query\""));
        Assert.assertTrue(prompt.contains("image_understanding [read]"));
        Assert.assertTrue(prompt.contains("\"path\""));
        Assert.assertTrue(prompt.contains("image_generation [read]"));
        Assert.assertTrue(prompt.contains("\"prompt\""));
        Assert.assertTrue(prompt.contains("agent [system]"));
        Assert.assertTrue(prompt.contains("agent_pipeline [system]"));
        Assert.assertTrue(prompt.contains("\"depends_on\""));
        Assert.assertFalse(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("<tool_calls><tool_call name=\"工具名\">"));
    }

    @Test
    public void nativeToolPromptForbidsInlineToolMarkup() {
        ToolRegistry registry = new ToolRegistry();
        Map<String, BaseTool> toolByName = new LinkedHashMap<>();
        for (BaseTool tool : registry.getAll()) {
            toolByName.put(tool.getName(), tool);
        }
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("file_read");
        McpToolConfig config = new McpToolConfig(
                "file_ops",
                "文件操作",
                "",
                true,
                new String[] {"file_read"}
        );

        String prompt = ToolSettingsRepository.renderToolPrompt(
                java.util.Collections.singletonList(config),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("原生 tools/function calling"));
        Assert.assertTrue(prompt.contains("不要把工具调用 JSON、XML、<tool_calls>"));
    }

    @Test
    public void sshToolPromptIncludesCustomMcpTools() {
        Map<String, BaseTool> toolByName = new LinkedHashMap<>();
        ToolRegistry registry = new ToolRegistry();
        BaseTool customMcp = new DummyCustomMcpTool();
        toolByName.put("shell_execute", registry.get("shell_execute"));
        toolByName.put("image_understanding", registry.get("image_understanding"));
        toolByName.put("image_generation", registry.get("image_generation"));
        toolByName.put(customMcp.getName(), customMcp);
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("shell_execute");
        enabled.add("image_understanding");
        enabled.add("image_generation");
        enabled.add(customMcp.getName());
        McpToolConfig shell = new McpToolConfig(
                "shell",
                "SSH Shell",
                "",
                true,
                new String[] {"shell_execute"}
        );
        McpToolConfig imageUnderstanding = new McpToolConfig(
                "image_understanding",
                "图片理解",
                "",
                true,
                new String[] {"image_understanding"}
        );
        McpToolConfig imageGeneration = new McpToolConfig(
                "image_generation",
                "图片生成",
                "",
                true,
                new String[] {"image_generation"}
        );

        String prompt = ToolSettingsRepository.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_SSH,
                java.util.Arrays.asList(shell, imageUnderstanding, imageGeneration),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("image_understanding"));
        Assert.assertTrue(prompt.contains("通过 SFTP 读取 SSH 工作区图片"));
        Assert.assertTrue(prompt.contains("image_generation"));
        Assert.assertTrue(prompt.contains("以内联 Markdown 图片返回"));
        Assert.assertTrue(prompt.contains("mcpx_test_lookup"));
        Assert.assertTrue(prompt.contains("调用测试 MCP"));
        Assert.assertTrue(prompt.contains("本地文件读写、文件搜索、Agent、Agent Pipeline 和 HTTP 服务器已禁用"));
    }

    private static final class DummyCustomMcpTool extends BaseTool {
        @Override
        public String getName() {
            return "mcpx_test_lookup";
        }

        @Override
        public String getDescription() {
            return "调用测试 MCP";
        }

        @Override
        public ToolCategory getCategory() {
            return ToolCategory.SYSTEM;
        }

        @Override
        public JSONObject getParameters() throws org.json.JSONException {
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject());
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            return new ToolResult("", getName(), "", false);
        }
    }
}
