package cn.lineai.data.repository;

import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolInfo;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 工具设置仓库接口，定义 ToolSettingsRepository 的公开契约。
 * 静态常量（如 PERMISSION_AUTO、EXECUTION_SSH）以及工具分类辅助方法
 * （{@code normalizePermissionMode}、{@code normalizeExecutionMode}、{@code getToolCategory}）
 * 继续保留在 {@link ToolSettingsRepository}，调用方通过类直接引用。
 */
public interface ToolSettingsStore {

    String KEY_PERMISSION_MODE = "@lineai_permission_mode";
    String KEY_MCP_EXECUTION_MODE = "@lineai_mcp_execution_mode";
    String KEY_IMAGE_UNDERSTANDING_MODEL_ID = "@lineai_image_understanding_model_id";
    String KEY_IMAGE_GENERATION_MODEL_ID = "@lineai_image_generation_model_id";

    String PERMISSION_READONLY = "readonly";
    String PERMISSION_AUTO = "auto";
    String PERMISSION_CONFIRM = "confirm";
    String EXECUTION_LOCAL = "local";
    String EXECUTION_SSH = "ssh";
    String EXECUTION_TERMINAL_PROVIDER = "terminal_provider";

    /**
     * 获取当前权限模式。
     */
    String getPermissionMode();

    /**
     * 设置当前权限模式。
     */
    void setPermissionMode(String mode);

    /**
     * 获取当前 MCP 执行目标。
     */
    String getExecutionMode();

    /**
     * 设置当前 MCP 执行目标。
     */
    void setExecutionMode(String mode);

    default boolean isCommandPermanentlyAllowed(String scope, cn.lineai.model.tool.ToolCall call) { return false; }
    default void allowCommandPermanently(String scope, cn.lineai.model.tool.ToolCall call) {}
    default void clearPermanentCommandPermissions() {}

    /**
     * 获取所有 MCP 工具组配置。
     */
    List<McpToolConfig> getConfigs();

    /**
     * 获取 MCP 工具与网络搜索综合配置。
     */
    McpSettingsState getMcpSettingsState();

    /**
     * 获取网页搜索配置。
     */
    WebSearchConfig getWebSearchConfig();

    /**
     * 保存网页搜索配置。
     */
    void setWebSearchConfig(WebSearchConfig config);

    /**
     * 获取图片理解模型 id。
     */
    String getImageUnderstandingModelId();

    /**
     * 设置图片理解模型 id。
     */
    void setImageUnderstandingModelId(String modelId);

    /**
     * 获取图片生成模型 id。
     */
    String getImageGenerationModelId();

    /**
     * 设置图片生成模型 id。
     */
    void setImageGenerationModelId(String modelId);

    /**
     * 设置 MCP 工具组启用状态。
     */
    void setMcpEnabled(String id, boolean enabled);

    /**
     * 计算当前可启用的工具名集合。
     */
    Set<String> getEnabledToolNames();

    /**
     * 结合已注册工具计算当前可启用的工具名集合。
     */
    Set<String> getEnabledToolNames(Collection<ToolInfo> implementedTools);

    /**
     * 判断指定工具在当前权限/执行目标下是否可执行。
     */
    PermissionResult canExecuteTool(String toolName, ToolCategory category);

    /**
     * 判断指定工具当前是否需要用户确认。
     */
    boolean needsConfirmation(String toolName);

    /**
     * 构造工具提示（按工具名过滤）。
     */
    String buildToolPrompt(Set<String> implementedToolNames);

    /**
     * 构造工具提示。
     */
    String buildToolPrompt(Set<String> implementedToolNames, boolean nativeToolProtocol);

    /**
     * 构造工具提示（按已注册工具过滤）。
     */
    String buildToolPrompt(Collection<ToolInfo> implementedTools, boolean nativeToolProtocol);

    /**
     * 获取 Agent 禁止使用的工具名称集合。
     * 默认排除 agent 和 agent_pipeline，防止递归调用。
     */
    default Set<String> getAgentExcludedToolNames() {
        Set<String> names = new java.util.HashSet<>();
        names.add("agent");
        names.add("agent_pipeline");
        names.add("agent_output");
        return names;
    }

    /**
     * 获取指定 Agent 类型允许的工具类别集合。
     * explore 默认只允许 READ；其他类型默认允许 READ 和 WRITE。
     */
    default Set<ToolCategory> getAgentAllowedCategories(String type) {
        if ("explore".equals(type)) {
            return Collections.singleton(ToolCategory.READ);
        }
        Set<ToolCategory> categories = new java.util.HashSet<>();
        categories.add(ToolCategory.READ);
        categories.add(ToolCategory.WRITE);
        return categories;
    }
}
