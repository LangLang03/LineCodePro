package cn.lineai.data.repository;

import cn.lineai.ai.prompt.ToolPromptRenderer;
import cn.lineai.model.McpToolConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolCategoryResolver;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolDisplayResolver;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public final class ToolSettingsRepositoryTest {

    private static ToolCategoryResolver previousResolver;

    @BeforeClass
    public static void setUpToolCategoryResolver() throws Exception {
        Field field = ToolSettingsRepository.class.getDeclaredField("toolCategoryResolver");
        field.setAccessible(true);
        previousResolver = (ToolCategoryResolver) field.get(null);
        ToolRegistry registry = new ToolRegistry();
        ToolDisplayResolver displayResolver = new ToolDisplayResolver(registry);
        field.set(null, (ToolCategoryResolver) name -> displayResolver.getDisplayCategory(name));
    }

    @AfterClass
    public static void tearDownToolCategoryResolver() throws Exception {
        Field field = ToolSettingsRepository.class.getDeclaredField("toolCategoryResolver");
        field.setAccessible(true);
        field.set(null, previousResolver);
    }

    @Test
    public void toolPromptUsesRegisteredToolMetadata() {
        ToolRegistry registry = new ToolRegistry();
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
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

        String prompt = ToolPromptRenderer.renderToolPrompt(
                java.util.Collections.singletonList(config),
                enabled,
                toolByName,
                false
        );

        Assert.assertTrue(prompt.contains("file_read [read]"));
        Assert.assertTrue(prompt.contains("Read file contents"));
        Assert.assertTrue(prompt.contains("\"file_path\""));
        Assert.assertTrue(prompt.contains("web_search [read]"));
        Assert.assertTrue(prompt.contains("\"query\""));
        Assert.assertTrue(prompt.contains("image_understanding [read]"));
        Assert.assertTrue(prompt.contains("\"path\""));
        Assert.assertTrue(prompt.contains("image_generation [generate]"));
        Assert.assertTrue(prompt.contains("\"prompt\""));
        Assert.assertTrue(prompt.contains("agent [system]"));
        Assert.assertTrue(prompt.contains("agent_pipeline [system]"));
        Assert.assertTrue(prompt.contains("\"depends_on\""));
        Assert.assertFalse(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("<tool_calls><tool_call name=\"tool_name\">"));
    }

    @Test
    public void imageGenerationHasGenerateCategory() {
        Assert.assertEquals(ToolCategory.GENERATE, ToolSettingsRepository.getToolCategory("image_generation"));
    }

    @Test
    public void sshReadonlyKeepsRemoteShellAgentAndReadTools() {
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "shell_execute", ToolSettingsRepository.getToolCategory("shell_execute")));
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "agent", ToolSettingsRepository.getToolCategory("agent")));
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "agent_pipeline", ToolSettingsRepository.getToolCategory("agent_pipeline")));
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "todo_update", ToolSettingsRepository.getToolCategory("todo_update")));
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "image_understanding", ToolSettingsRepository.getToolCategory("image_understanding")));
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "web_search", ToolSettingsRepository.getToolCategory("web_search")));
        Assert.assertTrue(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_SSH,
                "web_fetch", ToolSettingsRepository.getToolCategory("web_fetch")));
        Assert.assertFalse(ToolSettingsRepository.isReadonlyToolAllowedForMode(ToolSettingsRepository.EXECUTION_LOCAL,
                "shell_execute", ToolSettingsRepository.getToolCategory("shell_execute")));
    }

    @Test
    public void nativeToolPromptForbidsInlineToolMarkup() {
        ToolRegistry registry = new ToolRegistry();
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
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

        String prompt = ToolPromptRenderer.renderToolPrompt(
                java.util.Collections.singletonList(config),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("native tools/function calling"));
        Assert.assertTrue(prompt.contains("do not output tool call JSON, XML, <tool_calls>"));
    }

    @Test
    public void sshToolPromptIncludesCustomMcpTools() {
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
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

        String prompt = ToolPromptRenderer.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_SSH,
                java.util.Arrays.asList(shell, imageUnderstanding, imageGeneration),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("image_understanding"));
        Assert.assertTrue(prompt.contains("via SFTP"));
        Assert.assertTrue(prompt.contains("image_generation"));
        Assert.assertTrue(prompt.contains("inline Markdown image"));
        Assert.assertTrue(prompt.contains("mcpx_test_lookup"));
        Assert.assertTrue(prompt.contains("调用测试 MCP"));
        Assert.assertTrue(prompt.contains("Local file read/write and file search are disabled"));
        Assert.assertTrue(prompt.contains("Agent, Agent Pipeline, and todo list remain available"));
    }

    @Test
    public void normalizeExecutionModeAcceptsTerminalProvider() {
        Assert.assertEquals(ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                ToolSettingsRepository.normalizeExecutionMode(ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER));
    }

    @Test
    public void normalizeExecutionModeDefaultsToLocalForUnknown() {
        Assert.assertEquals(ToolSettingsRepository.EXECUTION_LOCAL,
                ToolSettingsRepository.normalizeExecutionMode("unknown_mode"));
    }

    @Test
    public void normalizeExecutionModeDefaultsToLocalForNull() {
        Assert.assertEquals(ToolSettingsRepository.EXECUTION_LOCAL,
                ToolSettingsRepository.normalizeExecutionMode(null));
    }

    @Test
    public void remoteExecutionModesUseSeparateToolSettingKeys() {
        Assert.assertEquals("@linecode_mcp_enabled_ssh_image_understanding",
                ToolSettingsRepository.mcpEnabledKey(ToolSettingsRepository.EXECUTION_SSH, "image_understanding"));
        Assert.assertEquals("@linecode_mcp_enabled_terminal_provider_image_understanding",
                ToolSettingsRepository.mcpEnabledKey(ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER, "image_understanding"));
        Assert.assertEquals("@linecode_mcp_enabled_image_understanding",
                ToolSettingsRepository.mcpEnabledKey(ToolSettingsRepository.EXECUTION_LOCAL, "image_understanding"));
    }

    @Test
    public void terminalProviderShellConfigUsesIpcName() {
        McpToolConfig config = ToolSettingsRepository.displayConfigForMode(
                ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                new McpToolConfig("shell", "SSH Shell", "通过 SSH 执行 shell 命令", true, new String[] {"shell_execute"}),
                true,
                null
        );

        Assert.assertEquals("IPC Shell", config.getName());
        Assert.assertTrue(config.getDescription().contains("IPC"));
        Assert.assertFalse(config.getName().contains("SSH"));
    }

    @Test
    public void terminalProviderToolPromptIncludesShellAndImageTools() {
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
        ToolRegistry registry = new ToolRegistry();
        toolByName.put("shell_execute", registry.get("shell_execute"));
        toolByName.put("image_understanding", registry.get("image_understanding"));
        toolByName.put("image_generation", registry.get("image_generation"));
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("shell_execute");
        enabled.add("image_understanding");
        enabled.add("image_generation");
        McpToolConfig shell = new McpToolConfig(
                "shell",
                "Terminal Shell",
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

        String prompt = ToolPromptRenderer.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                java.util.Arrays.asList(shell, imageUnderstanding, imageGeneration),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("image_understanding"));
        Assert.assertTrue(prompt.contains("via IPC"));
        Assert.assertTrue(prompt.contains("image_generation"));
        Assert.assertTrue(prompt.contains("inline Markdown image"));
        Assert.assertTrue(prompt.contains("Terminal Provider"));
        Assert.assertTrue(prompt.contains("terminal provider environment"));
        Assert.assertTrue(prompt.contains("Local file read/write and file search are disabled"));
        Assert.assertTrue(prompt.contains("Agent, Agent Pipeline, and todo list remain available"));
    }

    @Test
    public void terminalProviderToolPromptIncludesCustomMcpTools() {
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
        ToolRegistry registry = new ToolRegistry();
        BaseTool customMcp = new DummyCustomMcpTool();
        toolByName.put("shell_execute", registry.get("shell_execute"));
        toolByName.put(customMcp.getName(), customMcp);
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("shell_execute");
        enabled.add(customMcp.getName());
        McpToolConfig shell = new McpToolConfig(
                "shell",
                "Terminal Shell",
                "",
                true,
                new String[] {"shell_execute"}
        );

        String prompt = ToolPromptRenderer.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                java.util.Collections.singletonList(shell),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("mcpx_test_lookup"));
        Assert.assertTrue(prompt.contains("调用测试 MCP"));
    }

    @Test
    public void terminalProviderToolPromptUsesNativeToolProtocolNotice() {
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
        ToolRegistry registry = new ToolRegistry();
        toolByName.put("shell_execute", registry.get("shell_execute"));
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("shell_execute");
        McpToolConfig shell = new McpToolConfig(
                "shell",
                "Terminal Shell",
                "",
                true,
                new String[] {"shell_execute"}
        );

        String prompt = ToolPromptRenderer.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                java.util.Collections.singletonList(shell),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("native tools/function calling"));
    }

    @Test
    public void terminalProviderToolPromptUsesToolCallsMarkupWhenNotNative() {
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
        ToolRegistry registry = new ToolRegistry();
        toolByName.put("shell_execute", registry.get("shell_execute"));
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("shell_execute");
        McpToolConfig shell = new McpToolConfig(
                "shell",
                "Terminal Shell",
                "",
                true,
                new String[] {"shell_execute"}
        );

        String prompt = ToolPromptRenderer.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                java.util.Collections.singletonList(shell),
                enabled,
                toolByName,
                false
        );

        Assert.assertTrue(prompt.contains("<tool_calls><tool_call name=\"tool_name\">"));
    }

    @Test
    public void extensionToolsAreRenderedInStableNameOrder() {
        Map<String, ToolInfo> toolByName = new LinkedHashMap<>();
        BaseTool beta = new NamedDummyCustomMcpTool("mcpx_beta_lookup");
        BaseTool alpha = new NamedDummyCustomMcpTool("mcpx_alpha_lookup");
        toolByName.put(beta.getName(), beta);
        toolByName.put(alpha.getName(), alpha);
        Set<String> enabled = new java.util.HashSet<>();
        enabled.add(beta.getName());
        enabled.add(alpha.getName());

        String prompt = ToolPromptRenderer.renderToolPrompt(
                java.util.Collections.<McpToolConfig>emptyList(),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.indexOf("mcpx_alpha_lookup") < prompt.indexOf("mcpx_beta_lookup"));
    }

    @Test
    public void terminalProviderToolPromptEmptyEnabledReturnsNoToolsMessage() {
        String prompt = ToolPromptRenderer.renderToolPrompt(
                ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER,
                java.util.Collections.<McpToolConfig>emptyList(),
                new LinkedHashSet<>(),
                new LinkedHashMap<>(),
                false
        );

        Assert.assertTrue(prompt.contains("No tools are available"));
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
            return ToolResult.of("", getName(), "", false);
        }
    }

    private static final class NamedDummyCustomMcpTool extends BaseTool {
        private final String name;

        NamedDummyCustomMcpTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
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
            return ToolResult.of("", getName(), "", false);
        }
    }
}
