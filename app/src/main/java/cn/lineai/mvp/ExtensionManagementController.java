package cn.lineai.mvp;

import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import cn.lineai.tool.ToolRegistry;
import java.util.List;

final class ExtensionManagementController {
    interface Host {
        String projectPath();

        void returnToScreen(String screenId);

        void refreshVisibleScreen(String screenId);

        void render();
    }

    private final ExtensionStore extensionRepository;
    private final IpcProviderStore ipcProviderRepository;
    private final ToolRegistry toolRegistry;
    private final Host host;

    ExtensionManagementController(
            ExtensionStore extensionRepository,
            IpcProviderStore ipcProviderRepository,
            ToolRegistry toolRegistry,
            Host host
    ) {
        this.extensionRepository = extensionRepository;
        this.ipcProviderRepository = ipcProviderRepository;
        this.toolRegistry = toolRegistry;
        this.host = host;
    }

    ExtensionOverviewState getOverview() {
        ExtensionOverviewState base = extensionRepository.getOverview(host.projectPath());
        return new ExtensionOverviewState(
                base.getAgents(),
                base.getMcps(),
                base.getSkills(),
                ipcProviderRepository.getProvidersByType(IpcProviderType.TERMINAL)
        );
    }

    void saveAgentExtension(ExtensionAgentConfig config) {
        extensionRepository.saveAgentExtension(config);
        reloadExtensions();
        host.returnToScreen("extension:agent");
        host.render();
    }

    void saveMcpExtension(ExtensionMcpConfig config) {
        extensionRepository.saveMcpExtension(config);
        reloadExtensions();
        host.returnToScreen("extension:mcp");
        host.render();
    }

    List<McpToolSummary> queryMcpTools(String url, List<McpRequestHeader> headers) throws Exception {
        return extensionRepository.queryMcpTools(url, headers);
    }

    SkillRecord createSkill(String location, String name, String description, String content) {
        SkillRecord skill = extensionRepository.createSkill(host.projectPath(), location, name, description, content);
        host.returnToScreen("extension:skills");
        host.render();
        return skill;
    }

    SkillRecord installSkill(String location, String sourcePath, String name) throws Exception {
        SkillRecord skill = extensionRepository.installSkill(host.projectPath(), location, sourcePath, name);
        host.returnToScreen("extension:skills");
        host.render();
        return skill;
    }

    SkillRecord installSkillFromUri(String location, String uri, String displayName) throws Exception {
        SkillRecord skill = extensionRepository.installSkillFromUri(host.projectPath(), location, uri, displayName);
        host.returnToScreen("extension:skills");
        host.render();
        return skill;
    }

    SkillRecord installSkillFromGitHub(String location, String githubUrl) throws Exception {
        SkillRecord skill = extensionRepository.installSkillFromGitHub(host.projectPath(), location, githubUrl);
        host.returnToScreen("extension:skills");
        host.render();
        return skill;
    }

    void deleteExtensions(String kind, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if ("skills".equals(kind)) {
            extensionRepository.deleteSkills(ids);
        } else {
            for (String id : ids) {
                deleteExtension(kind, id);
            }
            return;
        }
        reloadExtensions();
        host.refreshVisibleScreen("extension:" + kind);
        host.render();
    }

    void setExtensionEnabled(String kind, String id, boolean enabled) {
        ExtensionKindDescriptor descriptor = ExtensionKindRegistry.getInstance().get(kind);
        if (descriptor != null) {
            descriptor.setEnabled(extensionRepository, id, enabled);
        }
        reloadExtensions();
        host.refreshVisibleScreen("extension:" + kind);
        host.render();
    }

    void deleteExtension(String kind, String id) {
        ExtensionKindDescriptor descriptor = ExtensionKindRegistry.getInstance().get(kind);
        if (descriptor != null) {
            descriptor.delete(extensionRepository, id);
        }
        reloadExtensions();
        host.refreshVisibleScreen("extension:" + kind);
        host.render();
    }

    private void reloadExtensions() {
        toolRegistry.reloadExtensions();
    }
}
