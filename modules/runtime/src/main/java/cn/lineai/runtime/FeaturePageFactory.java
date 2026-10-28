package cn.lineai.runtime;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.feature.extensions.AgentExtensionEditPage;
import cn.lineai.feature.extensions.AgentExtensionsPage;
import cn.lineai.feature.extensions.ExtensionScreens;
import cn.lineai.feature.extensions.GitHubSkillPage;
import cn.lineai.feature.extensions.LineCodeExtensionsPage;
import cn.lineai.feature.extensions.McpExtensionEditPage;
import cn.lineai.feature.extensions.McpExtensionsPage;
import cn.lineai.feature.extensions.SkillEditPage;
import cn.lineai.feature.extensions.SkillHubPage;
import cn.lineai.feature.extensions.SkillsExtensionsPage;
import cn.lineai.feature.extensions.TerminalProvidersPage;
import cn.lineai.feature.models.ModelEditPage;
import cn.lineai.feature.models.ModelListPage;
import cn.lineai.feature.models.ModelScreens;
import cn.lineai.feature.workspace.ChangesPage;
import cn.lineai.feature.workspace.WorkspaceProjectsPage;
import cn.lineai.feature.workspace.WorkspaceScreens;
import cn.lineai.ui.i18n.R;

/** Models, extensions and workspace screens constructed outside the activity. */
public final class FeaturePageFactory {

    private final PageHost host;
    private final LocalModelBridge localModels;
    private SkillsExtensionsPage skillsPage;
    private WorkspaceProjectsPage workspacePage;
    private LocalModelBridge.Page localPage;

    FeaturePageFactory(PageHost host) {
        this.host = host;
        this.localModels = LocalModelBridges.find();
    }

    View create(ScreenKey<?, ?> key) {
        if (key.id() == ExtensionScreens.AGENTS) return agentExtensionsPage();
        if (key.id() == ExtensionScreens.AGENT_EDIT) return agentEditPage((Params.Args) key.params());
        if (key.id() == ExtensionScreens.MCPS) return mcpExtensionsPage();
        if (key.id() == ExtensionScreens.MCP_EDIT) return mcpEditPage((Params.Args) key.params());
        if (key.id() == ExtensionScreens.SKILLS) return skillsExtensionsPage();
        if (key.id() == ExtensionScreens.SKILL_EDIT) return skillEditPage((Params.Args) key.params());
        if (key.id() == ExtensionScreens.SKILL_HUB) {
            return new SkillHubPage(host.activity(), host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills(), slug -> host.navigation().push(
                            ExtensionScreens.SKILL_DETAIL.key(cn.lineai.core.mvi.nav.Params.Args.of(
                                    "slug", slug))));
        }
        if (key.id() == ExtensionScreens.SKILL_DETAIL) {
            String slug = key.args().get("slug") instanceof String
                    ? (String) key.args().get("slug") : "";
            return new cn.lineai.feature.extensions.SkillHubDetailPage(host.activity(),
                    host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills(), slug);
        }
        if (key.id() == ExtensionScreens.SKILL_LOGIN) {
            return new cn.lineai.feature.extensions.SkillHubLoginPage(host.activity(),
                    host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    () -> {
                        android.widget.Toast.makeText(host.activity(),
                                R.string.skillhub_login_success, android.widget.Toast.LENGTH_SHORT)
                                .show();
                        host.navigation().back();
                    });
        }
        if (key.id() == ExtensionScreens.SKILL_PUBLISH) {
            return new cn.lineai.feature.extensions.SkillHubPublishPage(host.activity(),
                    host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills(), () -> host.navigation().back());
        }
        if (key.id() == ExtensionScreens.SKILL_GITHUB) {
            return new GitHubSkillPage(host.activity(), host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills());
        }
        if (key.id() == ExtensionScreens.LINECODE) {
            return new LineCodeExtensionsPage(host.activity(), host.tokens(),
                    host.navigation()::back);
        }
        if (key.id() == ExtensionScreens.TERMINAL_PROVIDERS) return terminalProvidersPage();
        if (key.id() == WorkspaceScreens.PROJECTS) return workspaceProjectsPage();
        if (key.id() == WorkspaceScreens.CHANGES) {
            return new ChangesPage(host.activity(), host.tokens(), host.navigation()::back,
                    new ChangesPage.Listener() {
                        public java.util.List<cn.lineai.core.model.FileDiff> load() {
                            return host.graph().diffs().list();
                        }
                        public boolean revert(String id) { return host.graph().diffs().revert(id); }
                    });
        }
        if (key.id() == ModelScreens.LIST) return modelListPage();
        if (key.id() == ModelScreens.OPTIONS) return modelOptionsPage();
        if (key.id() == ModelScreens.LOCAL) {
            cn.lineai.core.model.ModelConfig existing = key.args().asMap().containsKey("id")
                    ? host.graph().models().find(key.args().requireString("id")) : null;
            return localModelPage(existing);
        }
        if (key.id() == ModelScreens.EDIT) {
            cn.lineai.core.model.ModelConfig existing = key.args().asMap().containsKey("id")
                    ? host.graph().models().find(key.args().requireString("id")) : null;
            if (existing != null && existing.protocol
                    == cn.lineai.core.model.ModelConfig.Protocol.LOCAL_GGUF
                    && localModels != null) {
                return localModelPage(existing);
            }
            return modelEditPage(key, existing);
        }
        throw new IllegalArgumentException("Unregistered feature screen: " + key.id().route());
    }

    public void applyLocalModelPath(String path) {
        if (localPage != null) localPage.setModelPath(path);
    }

    public void reloadWorkspace() {
        if (workspacePage != null) workspacePage.reload();
    }

    public void reloadSkills() {
        if (skillsPage != null) skillsPage.reload();
    }

    private AgentExtensionsPage agentExtensionsPage() {
        return new AgentExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                new AgentExtensionsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.AgentExtension> load() {
                        return host.graph().agentExtensions().list();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().agentExtensions().setEnabled(id, enabled);
                    }
                    public void delete(String id) { host.graph().agentExtensions().delete(id); }
                    public void add() {
                        host.navigation().push(ExtensionScreens.AGENT_EDIT.key(
                                ExtensionScreens.agentArgs("")));
                    }
                    public void edit(cn.lineai.core.model.AgentExtension agent) {
                        host.navigation().push(ExtensionScreens.AGENT_EDIT.key(
                                ExtensionScreens.agentArgs(agent.id)));
                    }
                });
    }

    private AgentExtensionEditPage agentEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        cn.lineai.core.model.AgentExtension editing = null;
        if (!id.isEmpty()) {
            for (cn.lineai.core.model.AgentExtension agent
                    : host.graph().agentExtensions().list()) {
                if (agent.id.equals(id)) { editing = agent; break; }
            }
        }
        final cn.lineai.core.model.AgentExtension target = editing;
        return new AgentExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                target, new java.util.ArrayList<>(host.graph().tools().registry().all()),
                new AgentExtensionEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.AgentExtension extension) {
                        host.graph().agentExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String agentId) {
                        host.graph().agentExtensions().delete(agentId);
                        host.navigation().back();
                    }
                });
    }

    private McpExtensionsPage mcpExtensionsPage() {
        return new McpExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                new McpExtensionsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.McpExtension> load() {
                        return host.graph().mcpExtensions().list();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().mcpExtensions().setEnabled(id, enabled);
                    }
                    public void delete(String id) { host.graph().mcpExtensions().delete(id); }
                    public void add() {
                        host.navigation().push(ExtensionScreens.MCP_EDIT.key(
                                ExtensionScreens.mcpArgs("")));
                    }
                    public void edit(cn.lineai.core.model.McpExtension server) {
                        host.navigation().push(ExtensionScreens.MCP_EDIT.key(
                                ExtensionScreens.mcpArgs(server.id)));
                    }
                });
    }

    private McpExtensionEditPage mcpEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        cn.lineai.core.model.McpExtension editing = null;
        if (!id.isEmpty()) {
            for (cn.lineai.core.model.McpExtension server
                    : host.graph().mcpExtensions().list()) {
                if (server.id.equals(id)) { editing = server; break; }
            }
        }
        return new McpExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                editing, host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                new McpExtensionEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.McpExtension extension) {
                        host.graph().mcpExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String serverId) {
                        host.graph().mcpExtensions().delete(serverId);
                        host.navigation().back();
                    }
                });
    }

    private SkillsExtensionsPage skillsExtensionsPage() {
        skillsPage = new SkillsExtensionsPage(host.activity(), host.tokens(),
                host.navigation()::back, new SkillsExtensionsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.Skill> load() {
                        return host.graph().skills().list();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().skills().setEnabled(id, enabled);
                    }
                    public void delete(String id) { host.graph().skills().delete(id); }
                    public void add() {
                        host.navigation().push(ExtensionScreens.SKILL_EDIT.key(
                                ExtensionScreens.skillArgs("")));
                    }
                    public void edit(cn.lineai.core.model.Skill skill) {
                        host.navigation().push(ExtensionScreens.SKILL_EDIT.key(
                                ExtensionScreens.skillArgs(skill.id)));
                    }
                    public void openHub() {
                        host.navigation().push(ExtensionScreens.SKILL_HUB.key(Params.Empty.INSTANCE));
                    }
                    public void openGithub() {
                        host.navigation().push(ExtensionScreens.SKILL_GITHUB.key(
                                Params.Empty.INSTANCE));
                    }
                    public void openLogin() {
                        host.navigation().push(ExtensionScreens.SKILL_LOGIN.key(
                                Params.Empty.INSTANCE));
                    }
                    public void openPublish() {
                        host.navigation().push(ExtensionScreens.SKILL_PUBLISH.key(
                                Params.Empty.INSTANCE));
                    }
                    public void importSkill() { host.pickSkillArchive(); }
                });
        return skillsPage;
    }

    private SkillEditPage skillEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        cn.lineai.core.model.Skill editing = null;
        if (!id.isEmpty()) {
            for (cn.lineai.core.model.Skill skill : host.graph().skills().list()) {
                if (skill.id.equals(id)) { editing = skill; break; }
            }
        }
        final cn.lineai.core.model.Skill target = editing;
        return new SkillEditPage(host.activity(), host.tokens(), host.navigation()::back, target,
                new SkillEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.Skill skill) {
                        host.graph().skills().save(skill);
                        host.navigation().back();
                    }
                    public void onDelete(String skillId) {
                        host.graph().skills().delete(skillId);
                        host.navigation().back();
                    }
                });
    }

    private TerminalProvidersPage terminalProvidersPage() {
        return new TerminalProvidersPage(host.activity(), host.tokens(), host.navigation()::back,
                new TerminalProvidersPage.Listener() {
                    public java.util.List<cn.lineai.core.model.TerminalProviderConfig> installed() {
                        return host.graph().terminalProviders().list();
                    }
                    public java.util.List<cn.lineai.core.model.ScannedTerminalProvider> scan() {
                        java.util.List<cn.lineai.core.model.ScannedTerminalProvider> result =
                                new java.util.ArrayList<>();
                        for (cn.lineai.ipc.ScannedProvider provider :
                                new cn.lineai.ipc.IpcProviderScanner().scan(host.activity(),
                                        cn.lineai.ipc.IpcProviderType.TERMINAL)) {
                            result.add(new cn.lineai.core.model.ScannedTerminalProvider(
                                    provider.getLabel(), provider.getPackageName(),
                                    provider.getServiceClass(), provider.getProviderType()));
                        }
                        return result;
                    }
                    public void add(cn.lineai.core.model.ScannedTerminalProvider provider) {
                        host.graph().terminalProviders().add(
                                new cn.lineai.core.model.TerminalProviderConfig(
                                        "", true, provider.name, provider.packageName,
                                        provider.serviceClass, provider.providerType, 0, 0));
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().terminalProviders().setEnabled(id, enabled);
                    }
                    public void delete(String id) {
                        host.graph().terminalProviders().delete(id);
                    }
                });
    }

    private WorkspaceProjectsPage workspaceProjectsPage() {
        workspacePage = new WorkspaceProjectsPage(host.activity(), host.tokens(),
                host.navigation()::back, new WorkspaceProjectsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.Project> list() {
                        return host.graph().projects().list();
                    }
                    public void select(String id) {
                        host.graph().projects().select(id);
                        host.applyProjectLabel(host.graph().projects().selected());
                    }
                    public void delete(String id) {
                        host.graph().projects().delete(id);
                        host.applyProjectLabel(host.graph().projects().selected());
                    }
                    public cn.lineai.core.model.Project create(String name) {
                        java.io.File root = new java.io.File(host.activity().getFilesDir(),
                                "workspace");
                        String safe = name.trim().replaceAll("[^a-zA-Z0-9._-]+", "-");
                        if (safe.isEmpty()) safe = "project";
                        java.io.File dir = new java.io.File(root, safe);
                        int index = 2;
                        while (dir.exists()) {
                            dir = new java.io.File(root, safe + "-" + index);
                            index++;
                        }
                        dir.mkdirs();
                        cn.lineai.core.model.Project created = host.graph().projects().create(
                                name.trim(), dir.getAbsolutePath());
                        host.applyProjectLabel(created);
                        return created;
                    }
                    public void openExternal() { host.openExternalWorkspace(); }
                    public void onChanges() {
                        host.navigation().push(WorkspaceScreens.CHANGES.key(Params.Empty.INSTANCE));
                    }
                });
        return workspacePage;
    }

    private View modelListPage() {
        ModelListPage page = new ModelListPage(host.activity(), host.tokens(),
                host.graph().models().list(), selectedModelId(),
                new ModelListPage.Listener() {
                    public void onBack() { host.navigation().back(); }
                    public void onAddModel() {
                        host.navigation().push(ModelScreens.OPTIONS.key(Params.Empty.INSTANCE));
                    }
                    public void onSelectModel(String id) {
                        host.graph().models().select(id);
                        host.graph().chat().refreshModel();
                        host.navigation().back();
                    }
                    public void onEditModel(String id) {
                        host.navigation().push(ModelScreens.EDIT.key(
                                ModelScreens.editArgs(host.graph().models().find(id))));
                    }
                    public void onDeleteModels(java.util.List<String> ids) {
                        host.graph().models().delete(ids);
                        host.graph().chat().refreshModel();
                    }
                });
        page.loader(() -> host.graph().models().list(), this::selectedModelId);
        return page;
    }

    private View modelOptionsPage() {
        return new cn.lineai.feature.models.ModelAddOptionsPage(host.activity(), host.tokens(),
                host.navigation()::back, new cn.lineai.feature.models.ModelAddOptionsPage.Listener() {
                    public void onCustom() {
                        host.navigation().push(ModelScreens.EDIT.key(ModelScreens.editArgs(null)));
                    }
                    public void onProvider(String presetId) {
                        host.navigation().push(ModelScreens.EDIT.key(
                                ModelScreens.presetArgs(presetId)));
                    }
                    public boolean localModelsAvailable() { return localModels != null; }
                    public void onLocal() {
                        if (localModels == null) {
                            Toast.makeText(host.activity(),
                                    R.string.screen_model_add_local_picker_disabled,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        host.navigation().push(ModelScreens.LOCAL.key(Params.Args.empty()));
                    }
                });
    }

    private View modelEditPage(ScreenKey<?, ?> key, cn.lineai.core.model.ModelConfig existing) {
        cn.lineai.core.model.ModelPreset preset = key.args().asMap().containsKey("preset")
                ? cn.lineai.core.model.ModelPreset.find(key.args().requireString("preset")) : null;
        ModelEditPage page = new ModelEditPage(host.activity(), host.tokens(),
                host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                host.navigation()::back, existing, preset,
                (protocol, url, apiKey) -> new cn.lineai.engine.ai.ModelCatalogClient()
                        .fetch(protocol, url, apiKey),
                new ModelEditPage.Listener() {
                    public void onBack() { host.navigation().back(); }
                    public boolean localModelsAvailable() { return localModels != null; }
                    public void onSave(cn.lineai.core.model.ModelConfig model) {
                        host.graph().models().save(model);
                        host.graph().models().select(model.id);
                        host.graph().chat().refreshModel();
                        host.navigation().popTo(ModelScreens.LIST);
                    }
                    public void onTest(cn.lineai.core.model.ModelConfig model) {
                        host.probeModel(model);
                    }
                });
        return page;
    }

    private View localModelPage(cn.lineai.core.model.ModelConfig existing) {
        if (localModels == null) {
            host.unavailable();
            return modelOptionsPage();
        }
        localPage = localModels.createPage(host.activity(), host.tokens(),
                host.navigation()::back, existing, new LocalModelBridge.Listener() {
                    public void onPickFile() { host.pickLocalModelFile(); }
                    public void onSave(cn.lineai.core.model.ModelConfig model) {
                        host.graph().models().save(model);
                        host.graph().models().select(model.id);
                        host.graph().chat().refreshModel();
                        host.navigation().popTo(ModelScreens.LIST);
                    }
                    public void onDelete(String id) {
                        host.graph().models().delete(java.util.Collections.singletonList(id));
                        host.graph().chat().refreshModel();
                        host.navigation().popTo(ModelScreens.LIST);
                    }
                });
        return localPage.view();
    }

    private String selectedModelId() {
        cn.lineai.core.model.ModelConfig selected = host.graph().models().selected();
        return selected == null ? "" : selected.id;
    }
}
