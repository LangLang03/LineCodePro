package cn.lineai.mvp;

import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.ScannedProvider;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.data.model.ExtensionOverviewState;
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

    void onSkillCreated(String location, String name, String description, String content);

    void onSkillInstalled(String location, String sourcePath, String name) throws Exception;

    void onSkillInstalledFromUri(String location, String uri, String displayName) throws Exception;

    void onSkillInstalledFromGitHub(String location, String githubUrl) throws Exception;

    void onExtensionEnabledChanged(String kind, String id, boolean enabled);

    void onExtensionDeleted(String kind, String id);

    void onExtensionsDeleted(String kind, List<String> ids);

    List<ScannedProvider> onTerminalProviderScan();

    List<ScannedProvider> getTerminalProviderScanResults();

    boolean hasTerminalProviderScanned();

    void onTerminalProviderSaved(IpcProviderConfig config);

    void onTerminalProviderEnabledChanged(String id, boolean enabled);

    void onTerminalProviderDeleted(String id);
}
