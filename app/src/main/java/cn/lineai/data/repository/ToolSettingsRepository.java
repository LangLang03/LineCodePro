package cn.lineai.data.repository;

import cn.lineai.R;
import cn.lineai.model.ChatMode;
import cn.lineai.resource.ResourceProvider;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.ToolCategoryResolver;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolNames;
import cn.lineai.tool.ToolPermissionService;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolSettingsRepository implements ToolSettingsStore {

    private static final String KEY_MCP_PREFIX = "@linecode_mcp_enabled_";

    private static final Map<String, String> PHONE_CONTROL_TOOL_PERMISSION_MAP;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(ToolNames.PHONE_SCREENSHOT, PhoneControlRepository.PERMISSION_SCREENSHOT);
        map.put(ToolNames.PHONE_CLICK, PhoneControlRepository.PERMISSION_CLICK);
        map.put(ToolNames.PHONE_SWIPE, PhoneControlRepository.PERMISSION_SWIPE);
        map.put(ToolNames.PHONE_LONG_PRESS, PhoneControlRepository.PERMISSION_LONG_PRESS);
        map.put(ToolNames.PHONE_VIEW_HIERARCHY, PhoneControlRepository.PERMISSION_VIEW_HIERARCHY);
        map.put(ToolNames.PHONE_CLICK_VIEW, PhoneControlRepository.PERMISSION_VIEW_ACTION);
        map.put(ToolNames.PHONE_GLOBAL_ACTION, PhoneControlRepository.PERMISSION_GLOBAL_ACTION);
        PHONE_CONTROL_TOOL_PERMISSION_MAP = java.util.Collections.unmodifiableMap(map);
    }

    private static final Set<String> MODE_LOCAL = java.util.Collections.singleton(EXECUTION_LOCAL);
    private static final Set<String> MODE_REMOTE = java.util.Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(EXECUTION_SSH, EXECUTION_TERMINAL_PROVIDER)));
    private static final Set<String> MODE_ALL = java.util.Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(EXECUTION_LOCAL, EXECUTION_SSH, EXECUTION_TERMINAL_PROVIDER)));

    private final ResourceProvider resourceProvider;
    private final SettingsRepository settingsRepository;
    private final CommandPermissionRepository commandPermissions;
    private final WebSearchConfigRepository webSearchConfigRepository;
    private final PhoneControlRepository phoneControlRepository;
    private ToolRegistry toolRegistry;
    private static ToolCategoryResolver toolCategoryResolver;
    private ToolPermissionService toolPermissionService;
    private cn.lineai.ai.prompt.ToolPromptService toolPromptService;

    public ToolSettingsRepository(ResourceProvider resourceProvider, SettingsRepository settingsRepository, WebSearchConfigRepository webSearchConfigRepository, PhoneControlRepository phoneControlRepository, ToolCategoryResolver categoryResolver) {
        this.resourceProvider = resourceProvider;
        this.settingsRepository = settingsRepository;
        this.commandPermissions = new CommandPermissionRepository(settingsRepository);
        this.webSearchConfigRepository = webSearchConfigRepository;
        this.phoneControlRepository = phoneControlRepository;
        toolCategoryResolver = categoryResolver;
    }

    private List<McpToolConfig> buildDefaultConfigs() {
        List<McpToolConfig> configs = new ArrayList<>();
        configs.add(new McpToolConfig("file_ops",
                resourceProvider.getString(R.string.tool_group_file_ops_name),
                resourceProvider.getString(R.string.tool_group_file_ops_desc),
                true,
                new String[] {ToolNames.FILE_READ, ToolNames.FILE_WRITE, ToolNames.FILE_EDIT, ToolNames.FILE_DELETE, ToolNames.GLOB, ToolNames.LIST_DIR},
                MODE_LOCAL, "file_ops"));
        configs.add(new McpToolConfig(ToolNames.AGENT,
                "Agent",
                resourceProvider.getString(R.string.tool_group_agent_desc),
                true,
                new String[] {ToolNames.AGENT, ToolNames.AGENT_PIPELINE},
                MODE_ALL, "agent"));
        configs.add(new McpToolConfig("phone_control",
                resourceProvider.getString(R.string.tool_group_phone_control_name),
                resourceProvider.getString(R.string.tool_group_phone_control_desc),
                true,
                new String[] {ToolNames.PHONE_SCREENSHOT, ToolNames.PHONE_CLICK, ToolNames.PHONE_SWIPE, ToolNames.PHONE_LONG_PRESS, ToolNames.PHONE_VIEW_HIERARCHY, ToolNames.PHONE_CLICK_VIEW, ToolNames.PHONE_GLOBAL_ACTION},
                MODE_ALL, "phone_control"));
        configs.add(new McpToolConfig("todo",
                resourceProvider.getString(R.string.tool_group_todo_name),
                resourceProvider.getString(R.string.tool_group_todo_desc),
                true,
                new String[] {ToolNames.TODO_UPDATE},
                MODE_ALL, "todo"));
        configs.add(new McpToolConfig(ToolNames.IMAGE_UNDERSTANDING,
                resourceProvider.getString(R.string.tool_group_image_understanding_name),
                resourceProvider.getString(R.string.tool_group_image_understanding_desc),
                false,
                new String[] {ToolNames.IMAGE_UNDERSTANDING},
                MODE_ALL, "image_understanding"));
        configs.add(new McpToolConfig(ToolNames.IMAGE_GENERATION,
                resourceProvider.getString(R.string.tool_group_image_generation_name),
                resourceProvider.getString(R.string.tool_group_image_generation_desc),
                false,
                new String[] {ToolNames.IMAGE_GENERATION},
                MODE_ALL, "image_generation"));
        configs.add(new McpToolConfig("shell",
                "SSH Shell",
                resourceProvider.getString(R.string.tool_group_shell_desc),
                true,
                new String[] {ToolNames.SHELL_EXECUTE},
                MODE_REMOTE, "shell"));
        configs.add(new McpToolConfig(ToolNames.WEB_SEARCH,
                resourceProvider.getString(R.string.tool_group_web_search_name),
                resourceProvider.getString(R.string.tool_group_web_search_desc),
                true,
                new String[] {ToolNames.WEB_SEARCH, ToolNames.WEB_FETCH},
                MODE_ALL, "web_search"));
        configs.add(new McpToolConfig("memory",
                resourceProvider.getString(R.string.tool_group_memory_name),
                resourceProvider.getString(R.string.tool_group_memory_desc),
                true,
                new String[] {ToolNames.MEMORY_UPDATE},
                MODE_ALL, "memory"));
        return configs;
    }

    @Override public boolean isCommandPermanentlyAllowed(String scope, cn.lineai.model.tool.ToolCall call) {
        return commandPermissions.isAllowed(scope, call);
    }
    @Override public void allowCommandPermanently(String scope, cn.lineai.model.tool.ToolCall call) {
        commandPermissions.allow(scope, call);
    }
    @Override public void clearPermanentCommandPermissions() { commandPermissions.clear(); }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public void setToolPermissionService(ToolPermissionService toolPermissionService) {
        this.toolPermissionService = toolPermissionService;
    }

    public void setToolPromptService(cn.lineai.ai.prompt.ToolPromptService toolPromptService) {
        this.toolPromptService = toolPromptService;
    }

    @Override
    public synchronized String getPermissionMode() {
        return normalizePermissionMode(settingsRepository.getString(KEY_PERMISSION_MODE, PERMISSION_AUTO));
    }

    @Override
    public synchronized void setPermissionMode(String mode) {
        settingsRepository.setString(KEY_PERMISSION_MODE, normalizePermissionMode(mode));
    }

    @Override
    public synchronized String getExecutionMode() {
        return normalizeExecutionMode(settingsRepository.getString(KEY_MCP_EXECUTION_MODE, EXECUTION_LOCAL));
    }

    @Override
    public synchronized void setExecutionMode(String mode) {
        settingsRepository.setString(KEY_MCP_EXECUTION_MODE, normalizeExecutionMode(mode));
    }

    @Override
    public synchronized List<McpToolConfig> getConfigs() {
        String executionMode = getExecutionMode();
        ArrayList<McpToolConfig> configs = new ArrayList<>();
        for (McpToolConfig config : buildDefaultConfigs()) {
            boolean enabled = getMcpEnabled(executionMode, config);
            configs.add(displayConfigForMode(executionMode, config, enabled, resourceProvider));
        }
        return configs;
    }

    @Override
    public synchronized McpSettingsState getMcpSettingsState() {
        return new McpSettingsState(
                getExecutionMode(),
                getConfigs(),
                webSearchConfigRepository.get(),
                getImageUnderstandingModelId(),
                getImageGenerationModelId()
        );
    }

    @Override
    public synchronized WebSearchConfig getWebSearchConfig() {
        return webSearchConfigRepository.get();
    }

    @Override
    public synchronized void setWebSearchConfig(WebSearchConfig config) {
        webSearchConfigRepository.save(config);
    }

    @Override
    public synchronized String getImageUnderstandingModelId() {
        return settingsRepository.getString(KEY_IMAGE_UNDERSTANDING_MODEL_ID, "").trim();
    }

    @Override
    public synchronized void setImageUnderstandingModelId(String modelId) {
        settingsRepository.setString(KEY_IMAGE_UNDERSTANDING_MODEL_ID, modelId == null ? "" : modelId.trim());
    }

    @Override
    public synchronized String getImageGenerationModelId() {
        return settingsRepository.getString(KEY_IMAGE_GENERATION_MODEL_ID, "").trim();
    }

    @Override
    public synchronized void setImageGenerationModelId(String modelId) {
        settingsRepository.setString(KEY_IMAGE_GENERATION_MODEL_ID, modelId == null ? "" : modelId.trim());
    }

    @Override
    public synchronized void setMcpEnabled(String id, boolean enabled) {
        if (id == null || id.length() == 0) {
            return;
        }
        settingsRepository.setBoolean(mcpEnabledKey(getExecutionMode(), id), enabled);
    }

    private boolean getMcpEnabled(String executionMode, McpToolConfig config) {
        Map<String, String> settings = settingsRepository.getLineCodeSettings();
        String key = mcpEnabledKey(executionMode, config.getId());
        if (settings.containsKey(key)) {
            return settingsRepository.getBoolean(key, config.isEnabled());
        }
        String mode = normalizeExecutionMode(executionMode);
        if (EXECUTION_SSH.equals(mode) || EXECUTION_TERMINAL_PROVIDER.equals(mode)) {
            return config.isEnabled();
        }
        return settingsRepository.getBoolean(KEY_MCP_PREFIX + config.getId(), config.isEnabled());
    }

    static String mcpEnabledKey(String executionMode, String id) {
        String mode = normalizeExecutionMode(executionMode);
        if (EXECUTION_SSH.equals(mode) || EXECUTION_TERMINAL_PROVIDER.equals(mode)) {
            return KEY_MCP_PREFIX + mode + "_" + id;
        }
        return KEY_MCP_PREFIX + id;
    }

    static McpToolConfig displayConfigForMode(String executionMode, McpToolConfig config, boolean enabled, ResourceProvider resourceProvider) {
        if ("shell".equals(config.getId()) && EXECUTION_TERMINAL_PROVIDER.equals(normalizeExecutionMode(executionMode))) {
            String ipcDesc = resourceProvider == null
                    ? "通过终端提供者 IPC 执行 shell 命令"
                    : resourceProvider.getString(R.string.tool_group_ipc_shell_desc);
            return new McpToolConfig(config.getId(), "IPC Shell", ipcDesc, enabled, config.getTools(), config.getSupportedExecutionModes(), config.getIconKey());
        }
        return new McpToolConfig(config.getId(), config.getName(), config.getDescription(), enabled, config.getTools(), config.getSupportedExecutionModes(), config.getIconKey());
    }

    @Override
    public synchronized Set<String> getEnabledToolNames() {
        String executionMode = getExecutionMode();
        String permissionMode = getPermissionMode();
        HashSet<String> enabled = new HashSet<>();
        for (McpToolConfig config : getConfigs()) {
            if (!config.isEnabled()) {
                continue;
            }
            Set<String> modes = config.getSupportedExecutionModes();
            if (modes != null && !modes.contains(executionMode)) {
                continue;
            }
            for (String tool : config.getTools()) {
                ToolCategory category = getToolCategory(tool);
                if (PERMISSION_READONLY.equals(permissionMode)
                        && !isReadonlyToolAllowedForMode(executionMode, tool, category)) {
                    continue;
                }
                enabled.add(tool);
            }
        }
        String chatMode = settingsRepository.getString("@linecode_chat_mode", ChatMode.DEFAULT);
        if (!ChatMode.CONTROL.equals(chatMode)) {
            java.util.Iterator<String> iterator = enabled.iterator();
            while (iterator.hasNext()) {
                String tool = iterator.next();
                if (tool != null && tool.startsWith("phone_")) {
                    iterator.remove();
                }
            }
        } else {
            java.util.Iterator<String> iterator = enabled.iterator();
            while (iterator.hasNext()) {
                String tool = iterator.next();
                String permissionId = PHONE_CONTROL_TOOL_PERMISSION_MAP.get(tool);
                if (permissionId != null && !phoneControlRepository.isPermissionEnabled(permissionId)) {
                    iterator.remove();
                }
            }
        }
        boolean learningMode = settingsRepository.getBoolean(AiBehaviorSettingsRepository.KEY_LEARNING_MODE, false);
        applyLearningModeGate(enabled, learningMode);
        return enabled;
    }

    static void applyLearningModeGate(Set<String> enabled, boolean learningModeEnabled) {
        if (!learningModeEnabled && enabled != null) {
            enabled.remove(ToolNames.MEMORY_UPDATE);
        }
    }

    @Override
    public synchronized Set<String> getEnabledToolNames(Collection<ToolInfo> implementedTools) {
        HashSet<String> enabled = new HashSet<>(getEnabledToolNames());
        HashSet<String> implementedNames = new HashSet<>();
        if (implementedTools != null) {
            for (ToolInfo tool : implementedTools) {
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

    @Override
    public synchronized PermissionResult canExecuteTool(String toolName, ToolCategory category) {
        return toolPermissionService.canExecuteTool(toolName, category);
    }

    @Override
    public synchronized boolean needsConfirmation(String toolName) {
        return toolPermissionService.needsConfirmation(toolName);
    }

    @Override
    public synchronized String buildToolPrompt(Set<String> implementedToolNames) {
        return buildToolPrompt(implementedToolNames, false);
    }

    @Override
    public synchronized String buildToolPrompt(Set<String> implementedToolNames, boolean nativeToolProtocol) {
        return toolPromptService.buildToolPrompt(implementedToolNames, nativeToolProtocol);
    }

    @Override
    public synchronized String buildToolPrompt(Collection<ToolInfo> implementedTools, boolean nativeToolProtocol) {
        return toolPromptService.buildToolPrompt(implementedTools, nativeToolProtocol);
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
        if (EXECUTION_SSH.equals(mode)) return EXECUTION_SSH;
        if (EXECUTION_TERMINAL_PROVIDER.equals(mode)) return EXECUTION_TERMINAL_PROVIDER;
        return EXECUTION_LOCAL;
    }

    public static ToolCategory getToolCategory(String toolName) {
        if (toolCategoryResolver != null) {
            ToolDisplayCategory display = toolCategoryResolver.resolve(toolName);
            if (display == ToolDisplayCategory.READ) return ToolCategory.READ;
            if (display == ToolDisplayCategory.WRITE) return ToolCategory.WRITE;
            if (display == ToolDisplayCategory.DELETE) return ToolCategory.WRITE;
            if (display == ToolDisplayCategory.IMAGE_GENERATION) return ToolCategory.GENERATE;
        }
        return ToolCategory.SYSTEM;
    }

    private static boolean isReadonlyAllowed(ToolCategory category) {
        return category == ToolCategory.READ || category == ToolCategory.GENERATE;
    }

    static boolean isReadonlyAlwaysAllowed(String toolName) {
        return ToolNames.TODO_UPDATE.equals(toolName);
    }

    static boolean isReadonlyToolAllowedForMode(String executionMode, String toolName, ToolCategory category) {
        if (isReadonlyAllowed(category) || isReadonlyAlwaysAllowed(toolName)) {
            return true;
        }
        if (!EXECUTION_SSH.equals(executionMode) && !EXECUTION_TERMINAL_PROVIDER.equals(executionMode)) {
            return false;
        }
        return ToolNames.SHELL_EXECUTE.equals(toolName)
                || ToolNames.AGENT.equals(toolName)
                || ToolNames.AGENT_PIPELINE.equals(toolName);
    }

    private boolean isEnabledExtensionTool(String toolName, ToolCategory category) {
        if (!ToolRegistry.isExtensionToolName(toolName)) {
            return false;
        }
        String executionMode = getExecutionMode();
        if (EXECUTION_SSH.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (EXECUTION_TERMINAL_PROVIDER.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (!EXECUTION_LOCAL.equals(executionMode) && !EXECUTION_SSH.equals(executionMode) && !EXECUTION_TERMINAL_PROVIDER.equals(executionMode)) {
            return false;
        }
        return !PERMISSION_READONLY.equals(getPermissionMode()) || isReadonlyAllowed(category);
    }
}
