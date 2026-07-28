package cn.lineai.data.repository;

import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * 扩展仓库接口，定义 ExtensionRepository 的公开契约。
 * 委托到 AgentExtensionRepository、McpExtensionRepository、SkillRepository 三个子仓库。
 */
public interface ExtensionStore {

    /**
     * 返回扩展总览（代理、MCP、Skills）。
     */
    ExtensionOverviewState getOverview(String homePath);

    /**
     * 列出已注册的代理扩展。
     */
    List<ExtensionAgentConfig> getAgentExtensions();

    /**
     * 保存代理扩展配置。
     */
    ExtensionAgentConfig saveAgentExtension(ExtensionAgentConfig input);

    /**
     * 设置代理扩展启用状态。
     */
    void setAgentEnabled(String id, boolean enabled);

    /**
     * 删除代理扩展。
     */
    void deleteAgent(String id);

    /**
     * 列出已注册的 MCP 扩展。
     */
    List<ExtensionMcpConfig> getMcpExtensions();

    /**
     * 保存 MCP 扩展配置。
     */
    ExtensionMcpConfig saveMcpExtension(ExtensionMcpConfig input);

    /**
     * 设置 MCP 扩展启用状态。
     */
    void setMcpEnabled(String id, boolean enabled);

    /**
     * 删除 MCP 扩展。
     */
    void deleteMcp(String id);

    /**
     * 查询 MCP 远端工具清单。
     */
    List<McpToolSummary> queryMcpTools(String url, List<McpRequestHeader> headers) throws Exception;

    /**
     * 列出当前工作区下可用的 Skills。
     */
    List<SkillRecord> getSkills(String homePath);

    /**
     * 在指定位置创建 Skill 记录。
     */
    SkillRecord createSkill(String homePath, String location, String name, String description, String content);

    /**
     * 从本地 zip 安装 Skill。
     */
    SkillRecord installSkill(String homePath, String location, String sourcePath, String name) throws Exception;

    /**
     * 从 URI 安装 Skill。
     */
    SkillRecord installSkillFromUri(String homePath, String location, String uri, String displayName) throws Exception;

    /**
     * 从 GitHub 仓库或 raw SKILL.md URL 安装 Skill。
     */
    SkillRecord installSkillFromGitHub(String homePath, String location, String githubUrl) throws Exception;

    /**
     * 设置 Skill 启用状态。
     */
    void setSkillEnabled(String id, boolean enabled);

    /**
     * 删除 Skill。
     */
    void deleteSkill(String id);

    /**
     * 批量删除 Skill。
     */
    void deleteSkills(List<String> ids);

    /**
     * 构造注入到 system prompt 的扩展文本。
     */
    String buildExtensionPrompt(String homePath);

    /**
     * 列出当前 home 路径下允许写入的 Skill 根目录。
     */
    ArrayList<String> skillWriteRoots(String homePath);
}
