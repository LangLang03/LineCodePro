package cn.lineai.tool;

import cn.lineai.data.repository.ToolSettingsStore;

/**
 * 工具权限判断服务，从 ToolSettingsRepository 中提取的单一职责类。
 * 负责判断工具是否可执行、是否需要用户确认。
 */
public final class ToolPermissionService {

    private final ToolSettingsStore toolSettingsStore;
    private final ToolRegistry toolRegistry;

    public ToolPermissionService(ToolSettingsStore toolSettingsStore, ToolRegistry toolRegistry) {
        this.toolSettingsStore = toolSettingsStore;
        this.toolRegistry = toolRegistry;
    }

    public PermissionResult canExecuteTool(String toolName, ToolCategory category) {
        if (toolName == null || toolName.length() == 0) {
            return PermissionResult.denied("工具名为空");
        }
        if (!toolSettingsStore.getEnabledToolNames().contains(toolName)) {
            if (!isEnabledExtensionTool(toolName, category)) {
                return PermissionResult.denied("工具未启用或当前执行目标不可用: " + toolName);
            }
        }
        String executionMode = toolSettingsStore.getExecutionMode();
        if (ToolSettingsStore.PERMISSION_READONLY.equals(toolSettingsStore.getPermissionMode())
                && !isReadonlyToolAllowedForMode(executionMode, toolName, category)) {
            return PermissionResult.denied("只读模式下不允许执行 " + toolName + "。请在权限设置中切换到自动或确认模式。");
        }
        return PermissionResult.allowed();
    }

    public boolean needsConfirmation(String toolName) {
        if (toolRegistry != null) {
            ToolInfo tool = toolRegistry.get(toolName);
            if (tool != null && tool.needsConfirmation()) {
                return true;
            }
        } else {
            if (ToolNames.FILE_DELETE.equals(toolName) || ToolNames.SHELL_EXECUTE.equals(toolName)) {
                return true;
            }
        }
        return ToolSettingsStore.PERMISSION_CONFIRM.equals(toolSettingsStore.getPermissionMode());
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

    private static boolean isReadonlyToolAllowedForMode(String executionMode, String toolName, ToolCategory category) {
        if (isReadonlyAllowed(category) || isReadonlyAlwaysAllowed(toolName)) {
            return true;
        }
        if (!ToolSettingsStore.EXECUTION_SSH.equals(executionMode) && !ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(executionMode)) {
            return false;
        }
        return ToolNames.SHELL_EXECUTE.equals(toolName)
                || ToolNames.AGENT.equals(toolName)
                || ToolNames.AGENT_PIPELINE.equals(toolName);
    }

    private static boolean isReadonlyAlwaysAllowed(String toolName) {
        return ToolNames.TODO_UPDATE.equals(toolName);
    }
}
