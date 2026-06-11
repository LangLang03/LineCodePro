package cn.lineai.mvp;

import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import cn.lineai.tool.BaseTool;
import java.util.List;

public interface ExtensionController {
    ExtensionOverviewState getExtensionOverview();

    void onAgentExtensionSaved(ExtensionAgentConfig config);

    ExtensionAgentConfig onAgentDraftGenerated(String description) throws Exception;

    List<BaseTool> getExtensionAvailableTools();

    void onMcpExtensionSaved(ExtensionMcpConfig config);

    List<McpToolSummary> onMcpToolsQuery(String url, List<McpRequestHeader> headers) throws Exception;

    SkillRecord onSkillCreated(String location, String name, String description, String content);

    SkillRecord onSkillInstalled(String location, String sourcePath, String name) throws Exception;

    SkillRecord onSkillInstalledFromUri(String location, String uri, String displayName) throws Exception;

    void onExtensionEnabledChanged(String kind, String id, boolean enabled);

    void onExtensionDeleted(String kind, String id);
}
