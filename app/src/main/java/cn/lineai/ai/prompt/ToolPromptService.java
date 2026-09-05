package cn.lineai.ai.prompt;

import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolPromptRenderer;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 工具提示词渲染服务，从 ToolSettingsRepository 中提取的单一职责类。
 * 负责构建发送给模型的工具列表提示词。
 */
public final class ToolPromptService {

    private final ToolSettingsStore toolSettingsStore;
    private final ToolRegistry toolRegistry;
    private final ToolPromptRenderer toolPromptRenderer;

    public ToolPromptService(ToolSettingsStore toolSettingsStore, ToolRegistry toolRegistry, ToolPromptRenderer toolPromptRenderer) {
        this.toolSettingsStore = toolSettingsStore;
        this.toolRegistry = toolRegistry;
        this.toolPromptRenderer = toolPromptRenderer;
    }

    public String buildToolPrompt(Set<String> implementedToolNames) {
        return buildToolPrompt(implementedToolNames, false);
    }

    public String buildToolPrompt(Set<String> implementedToolNames, boolean nativeToolProtocol) {
        Set<String> enabled = toolSettingsStore.getEnabledToolNames();
        if (implementedToolNames != null) {
            enabled.retainAll(implementedToolNames);
        }
        return withPermissionMode(toolPromptRenderer.renderToolPrompt(collectEnabledTools(enabled), nativeToolProtocol));
    }

    public String buildToolPrompt(Collection<ToolInfo> implementedTools, boolean nativeToolProtocol) {
        Set<String> enabled = toolSettingsStore.getEnabledToolNames();
        List<ToolInfo> enabledTools = new ArrayList<>();
        if (implementedTools != null) {
            for (ToolInfo tool : implementedTools) {
                if (tool != null && tool.getName().length() > 0) {
                    if (enabled.contains(tool.getName()) || isEnabledExtensionTool(tool.getName(), tool.getCategory())) {
                        enabledTools.add(tool);
                    }
                }
            }
        }
        enabledTools.sort(java.util.Comparator.comparing(ToolInfo::getName));
        return withPermissionMode(toolPromptRenderer.renderToolPrompt(enabledTools, nativeToolProtocol));
    }

    private String withPermissionMode(String prompt) {
        String mode = toolSettingsStore.getPermissionMode();
        if (ToolSettingsStore.PERMISSION_AUTO.equals(mode)) {
            return "Permission mode: automatic. Enabled tools execute without per-call confirmation. "
                    + "Submit tool calls directly instead of asking the user for execution approval.\n\n" + prompt;
        }
        if (ToolSettingsStore.PERMISSION_CONFIRM.equals(mode)) {
            return "Permission mode: confirmation. Submit tool calls directly; the app will request approval "
                    + "for tools that require it before execution.\n\n" + prompt;
        }
        return prompt;
    }

    private boolean isEnabledExtensionTool(String toolName, ToolCategory category) {
        if (!ToolRegistry.isExtensionToolName(toolName)) {
            return false;
        }
        String executionMode = toolSettingsStore.getExecutionMode();
        if (ToolSettingsStore.EXECUTION_SSH.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (!ToolSettingsStore.EXECUTION_LOCAL.equals(executionMode) && !ToolSettingsStore.EXECUTION_SSH.equals(executionMode) && !ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(executionMode)) {
            return false;
        }
        return !ToolSettingsStore.PERMISSION_READONLY.equals(toolSettingsStore.getPermissionMode()) || isReadonlyAllowed(category);
    }

    private static boolean isReadonlyAllowed(ToolCategory category) {
        return category == ToolCategory.READ || category == ToolCategory.GENERATE;
    }

    private List<ToolInfo> collectEnabledTools(Set<String> enabledNames) {
        List<ToolInfo> tools = new ArrayList<>();
        if (toolRegistry != null) {
            for (String name : enabledNames) {
                ToolInfo tool = toolRegistry.get(name);
                if (tool != null) {
                    tools.add(tool);
                }
            }
        }
        tools.sort(java.util.Comparator.comparing(ToolInfo::getName));
        return tools;
    }
}
