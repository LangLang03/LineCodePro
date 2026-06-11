package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolSettingsRepository {
    public static final String KEY_PERMISSION_MODE = "@lineai_permission_mode";
    public static final String KEY_MCP_EXECUTION_MODE = "@lineai_mcp_execution_mode";
    public static final String KEY_IMAGE_UNDERSTANDING_MODEL_ID = "@lineai_image_understanding_model_id";
    public static final String KEY_IMAGE_GENERATION_MODEL_ID = "@lineai_image_generation_model_id";
    private static final String KEY_MCP_PREFIX = "@linecode_mcp_enabled_";

    public static final String PERMISSION_READONLY = "readonly";
    public static final String PERMISSION_AUTO = "auto";
    public static final String PERMISSION_CONFIRM = "confirm";
    public static final String EXECUTION_LOCAL = "local";
    public static final String EXECUTION_SSH = "ssh";

    private static final McpToolConfig[] DEFAULT_CONFIGS = new McpToolConfig[] {
            new McpToolConfig("file_ops", "文件操作", "读取、写入、编辑和删除文件", true,
                    new String[] {"file_read", "file_write", "file_edit", "file_delete", "glob", "list_dir"}),
            new McpToolConfig("http_server", "HTTP 服务器", "启动本地 HTTP 文件服务器", true,
                    new String[] {"http_server"}),
            new McpToolConfig("agent", "Agent", "分派 Agent 处理任务", true,
                    new String[] {"agent", "agent_pipeline"}),
            new McpToolConfig("image_understanding", "图片理解", "读取本地或 SSH 工作区图片并调用已选择的视觉模型理解内容", false,
                    new String[] {"image_understanding"}),
            new McpToolConfig("image_generation", "图片生成", "调用已选择的生图模型生成图片，并以内联 Markdown 图片返回", false,
                    new String[] {"image_generation"}),
            new McpToolConfig("shell", "SSH Shell", "通过 SSH 执行 shell 命令", true,
                    new String[] {"shell_execute"}),
            new McpToolConfig("web_search", "网页搜索", "搜索互联网并查看网页内容", false,
                    new String[] {"web_search", "web_fetch"})
    };

    private final SettingsRepository settingsRepository;
    private final WebSearchConfigRepository webSearchConfigRepository;

    public ToolSettingsRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
        webSearchConfigRepository = new WebSearchConfigRepository(context);
    }

    public synchronized String getPermissionMode() {
        return normalizePermissionMode(settingsRepository.getString(KEY_PERMISSION_MODE, PERMISSION_AUTO));
    }

    public synchronized void setPermissionMode(String mode) {
        settingsRepository.setString(KEY_PERMISSION_MODE, normalizePermissionMode(mode));
    }

    public synchronized String getExecutionMode() {
        return normalizeExecutionMode(settingsRepository.getString(KEY_MCP_EXECUTION_MODE, EXECUTION_LOCAL));
    }

    public synchronized void setExecutionMode(String mode) {
        settingsRepository.setString(KEY_MCP_EXECUTION_MODE, normalizeExecutionMode(mode));
    }

    public synchronized List<McpToolConfig> getConfigs() {
        ArrayList<McpToolConfig> configs = new ArrayList<>();
        for (McpToolConfig config : DEFAULT_CONFIGS) {
            boolean enabled = settingsRepository.getBoolean(KEY_MCP_PREFIX + config.getId(), config.isEnabled());
            configs.add(new McpToolConfig(config.getId(), config.getName(), config.getDescription(), enabled, config.getTools()));
        }
        return configs;
    }

    public synchronized McpSettingsState getMcpSettingsState() {
        return new McpSettingsState(
                getExecutionMode(),
                getConfigs(),
                webSearchConfigRepository.get(),
                getImageUnderstandingModelId(),
                getImageGenerationModelId()
        );
    }

    public synchronized WebSearchConfig getWebSearchConfig() {
        return webSearchConfigRepository.get();
    }

    public synchronized void setWebSearchConfig(WebSearchConfig config) {
        webSearchConfigRepository.save(config);
    }

    public synchronized String getImageUnderstandingModelId() {
        return settingsRepository.getString(KEY_IMAGE_UNDERSTANDING_MODEL_ID, "").trim();
    }

    public synchronized void setImageUnderstandingModelId(String modelId) {
        settingsRepository.setString(KEY_IMAGE_UNDERSTANDING_MODEL_ID, modelId == null ? "" : modelId.trim());
    }

    public synchronized String getImageGenerationModelId() {
        return settingsRepository.getString(KEY_IMAGE_GENERATION_MODEL_ID, "").trim();
    }

    public synchronized void setImageGenerationModelId(String modelId) {
        settingsRepository.setString(KEY_IMAGE_GENERATION_MODEL_ID, modelId == null ? "" : modelId.trim());
    }

    public synchronized void setMcpEnabled(String id, boolean enabled) {
        if (id == null || id.length() == 0) {
            return;
        }
        settingsRepository.setBoolean(KEY_MCP_PREFIX + id, enabled);
    }

    public synchronized Set<String> getEnabledToolNames() {
        String executionMode = getExecutionMode();
        String permissionMode = getPermissionMode();
        HashSet<String> enabled = new HashSet<>();
        for (McpToolConfig config : getConfigs()) {
            if (!config.isEnabled()) {
                continue;
            }
            if (EXECUTION_LOCAL.equals(executionMode) && "shell".equals(config.getId())) {
                continue;
            }
            if (EXECUTION_SSH.equals(executionMode)
                    && !"shell".equals(config.getId())
                    && !"web_search".equals(config.getId())
                    && !"image_understanding".equals(config.getId())
                    && !"image_generation".equals(config.getId())) {
                continue;
            }
            for (String tool : config.getTools()) {
                ToolCategory category = getToolCategory(tool);
                if (PERMISSION_READONLY.equals(permissionMode) && category != ToolCategory.READ) {
                    continue;
                }
                enabled.add(tool);
            }
        }
        return enabled;
    }

    public synchronized Set<String> getEnabledToolNames(Collection<BaseTool> implementedTools) {
        HashSet<String> enabled = new HashSet<>(getEnabledToolNames());
        HashSet<String> implementedNames = new HashSet<>();
        if (implementedTools != null) {
            for (BaseTool tool : implementedTools) {
                if (tool == null || tool.getName().length() == 0) {
                    continue;
                }
                implementedNames.add(tool.getName());
                if (isEnabledExtensionTool(tool.getName(), tool.getCategory())) {
                    enabled.add(tool.getName());
                }
            }
        }
        if (!implementedNames.isEmpty()) {
            enabled.retainAll(implementedNames);
        }
        return enabled;
    }

    public synchronized PermissionResult canExecuteTool(String toolName, ToolCategory category) {
        if (toolName == null || toolName.length() == 0) {
            return PermissionResult.denied("工具名为空");
        }
        if (!getEnabledToolNames().contains(toolName)) {
            if (!isEnabledExtensionTool(toolName, category)) {
                return PermissionResult.denied("工具未启用或当前执行目标不可用: " + toolName);
            }
        }
        if (PERMISSION_READONLY.equals(getPermissionMode()) && category != ToolCategory.READ) {
            return PermissionResult.denied("只读模式下不允许执行 " + toolName + "。请在权限设置中切换到自动或确认模式。");
        }
        return PermissionResult.allowed();
    }

    public synchronized boolean needsConfirmation(String toolName) {
        if ("file_delete".equals(toolName) || "shell_execute".equals(toolName)) {
            return true;
        }
        return PERMISSION_CONFIRM.equals(getPermissionMode());
    }

    public synchronized String buildToolPrompt(Set<String> implementedToolNames) {
        return buildToolPrompt(implementedToolNames, false);
    }

    public synchronized String buildToolPrompt(Set<String> implementedToolNames, boolean nativeToolProtocol) {
        Set<String> enabled = getEnabledToolNames();
        if (implementedToolNames != null) {
            enabled.retainAll(implementedToolNames);
        }
        return renderToolPrompt(getExecutionMode(), getConfigs(), enabled, new LinkedHashMap<>(), nativeToolProtocol);
    }

    public synchronized String buildToolPrompt(Collection<BaseTool> implementedTools, boolean nativeToolProtocol) {
        Set<String> enabled = getEnabledToolNames();
        LinkedHashMap<String, BaseTool> toolByName = new LinkedHashMap<>();
        if (implementedTools != null) {
            for (BaseTool tool : implementedTools) {
                if (tool != null && tool.getName().length() > 0) {
                    toolByName.put(tool.getName(), tool);
                    if (isEnabledExtensionTool(tool.getName(), tool.getCategory())) {
                        enabled.add(tool.getName());
                    }
                }
            }
            enabled.retainAll(toolByName.keySet());
        }
        return renderToolPrompt(getExecutionMode(), getConfigs(), enabled, toolByName, nativeToolProtocol);
    }

    static String renderToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        return renderToolPrompt(EXECUTION_LOCAL, configs, enabled, toolByName, nativeToolProtocol);
    }

    static String renderToolPrompt(
            String executionMode,
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        if (enabled.isEmpty()) {
            return "## 可用工具\n当前没有可用工具。未配置模型工具、工具组被关闭或当前权限模式禁用了所有工具。";
        }
        if (EXECUTION_SSH.equals(normalizeExecutionMode(executionMode))) {
            return renderSshToolPrompt(configs, enabled, toolByName, nativeToolProtocol);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 可用工具\n以下工具列表由当前 MCP 设置、权限模式、执行目标和已注册工具动态生成。未列出的工具不可用，工具执行必须遵守当前权限模式。\n\n");
        List<McpToolConfig> promptConfigs = configs == null ? new ArrayList<>() : configs;
        HashSet<String> renderedTools = new HashSet<>();
        for (McpToolConfig config : promptConfigs) {
            ArrayList<String> tools = new ArrayList<>();
            for (String tool : config.getTools()) {
                if (enabled.contains(tool)) {
                    tools.add(tool);
                    renderedTools.add(tool);
                }
            }
            if (tools.isEmpty()) {
                continue;
            }
            builder.append("### ").append(config.getName()).append('\n');
            for (String toolName : tools) {
                BaseTool tool = toolByName == null ? null : toolByName.get(toolName);
                builder.append("  - ").append(toolName);
                if (tool != null) {
                    builder.append(" [").append(categoryLabel(tool.getCategory()));
                    if (tool.requiresConfirmation()) {
                        builder.append(", 需要确认");
                    }
                    builder.append("]：").append(tool.getDescription()).append('\n');
                    try {
                        builder.append("    参数: ").append(tool.getParameters().toString()).append('\n');
                    } catch (Exception ignored) {
                        builder.append("    参数: {}\n");
                    }
                } else {
                    builder.append('\n');
                }
            }
            builder.append('\n');
        }
        appendExtensionTools(builder, enabled, renderedTools, toolByName);
        if (nativeToolProtocol) {
            builder.append("工具调用由当前模型协议的原生 tools/function calling 机制提供。需要读取、写入、搜索或列目录时，必须使用原生工具调用，不要把工具调用 JSON、XML、<tool_calls> 或 Markdown 代码块输出到正文。")
                    .append("每次工具返回后必须继续分析结果；如果任务还没完成，继续调用合适工具执行下一步。");
        } else {
            builder.append("工具调用格式已锁定：需要调用工具时，只能输出 <tool_calls><tool_call name=\"工具名\"><argument name=\"参数名\">参数值</argument></tool_call></tool_calls>。")
                    .append("不要输出 OpenAI tool_calls JSON、Markdown 代码块或自然语言包装。每次工具返回后必须继续分析结果；如果任务还没完成，继续调用合适工具执行下一步。");
        }
        return builder.toString().trim();
    }

    private static void appendExtensionTools(
            StringBuilder builder,
            Set<String> enabled,
            Set<String> renderedTools,
            Map<String, BaseTool> toolByName
    ) {
        ArrayList<String> extensionTools = new ArrayList<>();
        for (String toolName : enabled) {
            if (!renderedTools.contains(toolName) && ToolRegistry.isExtensionToolName(toolName)) {
                extensionTools.add(toolName);
            }
        }
        if (extensionTools.isEmpty()) {
            return;
        }
        builder.append("### 扩展\n");
        for (String toolName : extensionTools) {
            BaseTool tool = toolByName == null ? null : toolByName.get(toolName);
            builder.append("  - ").append(toolName);
            if (tool != null) {
                builder.append(" [").append(categoryLabel(tool.getCategory())).append("]：")
                        .append(tool.getDescription()).append('\n');
                try {
                    builder.append("    参数: ").append(tool.getParameters().toString()).append('\n');
                } catch (Exception ignored) {
                    builder.append("    参数: {}\n");
                }
            } else {
                builder.append('\n');
            }
        }
        builder.append('\n');
    }

    private static String renderSshToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 可用工具\n")
                .append("当前执行目标是 SSH Shell。本地文件读写、文件搜索、Agent、Agent Pipeline 和 HTTP 服务器已禁用。\n")
                .append("图片理解会通过 SFTP 读取 SSH 工作区图片；网页搜索、图片生成和应用侧自定义 HTTP MCP 可用时仍会作为工具提供。\n")
                .append("不要引用应用私有 home 工作目录；如果系统提示提供了 SSH 项目目录，必须在该目录内操作。\n")
                .append("如需读取、写入、列目录或搜索文件，请通过 shell 命令在 SSH 环境内完成。\n\n");
        List<McpToolConfig> promptConfigs = configs == null ? new ArrayList<>() : configs;
        HashSet<String> renderedTools = new HashSet<>();
        for (McpToolConfig config : promptConfigs) {
            if (!"shell".equals(config.getId())
                    && !"web_search".equals(config.getId())
                    && !"image_understanding".equals(config.getId())
                    && !"image_generation".equals(config.getId())) {
                continue;
            }
            ArrayList<String> tools = new ArrayList<>();
            for (String tool : config.getTools()) {
                if (enabled.contains(tool)) {
                    tools.add(tool);
                    renderedTools.add(tool);
                }
            }
            if (tools.isEmpty()) {
                continue;
            }
            builder.append("### ").append(config.getName()).append('\n');
            for (String toolName : tools) {
                BaseTool tool = toolByName == null ? null : toolByName.get(toolName);
                builder.append("  - ").append(toolName);
                if (tool != null) {
                    builder.append(" [").append(categoryLabel(tool.getCategory()));
                    if (tool.requiresConfirmation()) {
                        builder.append(", 需要确认");
                    }
                    builder.append("]：").append(tool.getDescription()).append('\n');
                    try {
                        builder.append("    参数: ").append(tool.getParameters().toString()).append('\n');
                    } catch (Exception ignored) {
                        builder.append("    参数: {}\n");
                    }
                } else {
                    builder.append('\n');
                }
            }
            if ("shell".equals(config.getId())) {
                builder.append("shell_execute 默认在当前工作区目录执行；如需临时切换目录，再显式设置 cwd。\n");
            } else if ("web_search".equals(config.getId())) {
                builder.append("web_search 和 web_fetch 由应用侧网络配置执行，不依赖 SSH 主机环境。\n");
            } else if ("image_understanding".equals(config.getId())) {
                builder.append("image_understanding 通过 SFTP 读取 SSH 工作区图片，再由应用侧视觉模型配置执行。\n");
            } else if ("image_generation".equals(config.getId())) {
                builder.append("image_generation 由应用侧生图模型配置执行，不依赖 SSH 主机环境；结果会以内联 Markdown 图片返回。\n");
            }
            builder.append('\n');
        }
        appendExtensionTools(builder, enabled, renderedTools, toolByName);
        builder.append("每次工具返回后必须继续分析输出；如果任务还没完成，继续调用合适工具执行下一步。")
                .append("不要因为刚执行过一次或两次 shell 命令就结束；只有确认任务完成、受阻或需要用户决定时才回复用户。\n");
        if (nativeToolProtocol) {
            builder.append("工具调用由当前模型协议的原生 tools/function calling 机制提供。不要把工具调用 JSON、XML、<tool_calls> 或 Markdown 代码块输出到正文。");
        } else {
            builder.append("工具调用格式已锁定：需要调用工具时，只能输出 <tool_calls><tool_call name=\"工具名\"><argument name=\"参数名\">参数值</argument></tool_call></tool_calls>。")
                    .append("不要输出 OpenAI tool_calls JSON、Markdown 代码块或自然语言包装。");
        }
        return builder.toString().trim();
    }

    private static String categoryLabel(ToolCategory category) {
        if (category == ToolCategory.WRITE) {
            return "write";
        }
        if (category == ToolCategory.SYSTEM) {
            return "system";
        }
        return "read";
    }

    public static String normalizePermissionMode(String mode) {
        if (PERMISSION_READONLY.equals(mode) || "manual".equals(mode)) {
            return PERMISSION_READONLY;
        }
        if (PERMISSION_CONFIRM.equals(mode) || "ask".equals(mode)) {
            return PERMISSION_CONFIRM;
        }
        return PERMISSION_AUTO;
    }

    public static String normalizeExecutionMode(String mode) {
        return EXECUTION_SSH.equals(mode) ? EXECUTION_SSH : EXECUTION_LOCAL;
    }

    public static ToolCategory getToolCategory(String toolName) {
        if ("file_read".equals(toolName) || "glob".equals(toolName) || "list_dir".equals(toolName)
                || "web_search".equals(toolName) || "web_fetch".equals(toolName)
                || "image_understanding".equals(toolName)
                || "image_generation".equals(toolName)) {
            return ToolCategory.READ;
        }
        if ("file_write".equals(toolName) || "file_edit".equals(toolName) || "file_delete".equals(toolName)) {
            return ToolCategory.WRITE;
        }
        return ToolCategory.SYSTEM;
    }

    private boolean isEnabledExtensionTool(String toolName, ToolCategory category) {
        if (!ToolRegistry.isExtensionToolName(toolName)) {
            return false;
        }
        String executionMode = getExecutionMode();
        if (EXECUTION_SSH.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (!EXECUTION_LOCAL.equals(executionMode) && !EXECUTION_SSH.equals(executionMode)) {
            return false;
        }
        return !PERMISSION_READONLY.equals(getPermissionMode()) || category == ToolCategory.READ;
    }
}
