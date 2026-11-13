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

    /** Creates the factory; the local bridge is resolved once, reflecting the build flavor. */
    FeaturePageFactory(PageHost host) {
        this.host = host;
        this.localModels = LocalModelBridges.find();
    }

    /** Builds the view for one feature screen key; throws on unregistered routes. */
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
                    host.graph().skills(),
                    slug -> host.navigation().push(
                            ExtensionScreens.SKILL_DETAIL.key(cn.lineai.core.mvi.nav.Params.Args.of(
                                    "slug", slug))),
                    () -> host.navigation().push(ExtensionScreens.SKILL_LOGIN.key(
                            Params.Empty.INSTANCE)),
                    () -> host.navigation().push(ExtensionScreens.SKILL_PUBLISH.key(
                            Params.Empty.INSTANCE)));
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

    /** Forwards an imported GGUF path to the open local model page, if any. */
    public void applyLocalModelPath(String path) {
        if (localPage != null) localPage.setModelPath(path);
    }

    /** Refreshes the cached workspace projects page after SAF-driven changes. */
    public void reloadWorkspace() {
        if (workspacePage != null) workspacePage.reload();
    }

    /** Refreshes the cached skills page after a SAF skill import. */
    public void reloadSkills() {
        if (skillsPage != null) skillsPage.reload();
    }

    /** Extension agent list page wired to the agent extension store. */
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

    /** Agent editor page; loads the edited agent and the phone-tool-free catalog. */
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
        java.util.List<cn.lineai.tool.api.Tool> agentTools = new java.util.ArrayList<>();
        for (cn.lineai.tool.api.Tool tool : host.graph().tools().registry().all()) {
            if (tool.kind() != cn.lineai.tool.api.ToolKind.PHONE) {
                agentTools.add(tool);
            }
        }
        return new AgentExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                host.graph().scheduler(), target, agentTools,
                new AgentExtensionEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.AgentExtension extension) {
                        host.graph().agentExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String agentId) {
                        host.graph().agentExtensions().delete(agentId);
                        host.navigation().back();
                    }
                    public cn.lineai.core.model.AgentExtension generateDraft(String description)
                            throws Exception {
                        return agentDraft(description);
                    }
                });
    }

    /** Wires the selected model, the tool catalog and the prompt template into the use case. */
    private cn.lineai.core.model.AgentExtension agentDraft(String description) throws Exception {
        cn.lineai.core.model.ModelConfig model = host.graph().models().selected();
        if (model == null) {
            throw new IllegalStateException("No model selected");
        }
        return AgentDrafts.generate(model,
                host.graph().prompts().current(cn.lineai.engine.prompt.PromptId.AGENT_DRAFT),
                host.graph().tools().registry().all(), description,
                new cn.lineai.engine.ai.ModelProbe());
    }

    /** MCP server list page wired to the MCP extension store. */
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

    /** MCP server editor page; loads the edited server when the args carry an id. */
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

    /** Skills list page; cached so SAF imports can trigger a reload. */
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

    /** Skill editor page; loads the edited skill when the args carry an id. */
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

    /** Terminal provider management page; refreshes discovery before rendering. */
    private TerminalProvidersPage terminalProvidersPage() {
        host.graph().refreshTerminalProviders();
        return new TerminalProvidersPage(host.activity(), host.tokens(), host.navigation()::back,
                new TerminalProvidersPage.Listener() {
                    public java.util.List<cn.lineai.core.model.TerminalProviderConfig> installed() {
                        return host.graph().terminalProviders().list();
                    }
                    public java.util.List<cn.lineai.core.model.ScannedTerminalProvider> scan() {
                        return cn.lineai.shell.TerminalProviderDiscovery.scan(host.activity());
                    }
                    public void add(cn.lineai.core.model.ScannedTerminalProvider provider) {
                        if (!host.graph().trustsProviderSignature(provider.packageName)) {
                            Toast.makeText(host.activity(),
                                    R.string.toast_terminal_provider_signature_untrusted,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        host.graph().terminalProviders().add(
                                new cn.lineai.core.model.TerminalProviderConfig(
                                        "", true, provider.name, provider.packageName,
                                        provider.serviceClass, provider.providerType, 0, 0));
                        host.graph().refreshTerminalProviders();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().terminalProviders().setEnabled(id, enabled);
                        host.graph().refreshTerminalProviders();
                    }
                    public void delete(String id) {
                        host.graph().terminalProviders().delete(id);
                        host.graph().refreshTerminalProviders();
                    }
                });
    }

    /** Workspace listener handling selection, creation, SSH status and change routing. */
    private WorkspaceProjectsPage.Listener workspaceListener() {
        return new WorkspaceProjectsPage.Listener() {
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
                        String safe = cn.lineai.feature.workspace.WorkspaceNames.folder(name);
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
                    public void shareWorkspace() { host.shareWorkspace(); }
                    public String sshAddress() {
                        cn.lineai.core.model.SshConfig config =
                                new cn.lineai.shell.SshPreferences(
                                        host.graph().settings()).config();
                        if (config.host == null || config.host.isEmpty()) return "";
                        return (config.username.isEmpty() ? "" : config.username + "@")
                                + config.host + ":" + config.port;
                    }
                    public boolean conversationActive() {
                        return host.graph().chat().hasMessages();
                    }
            public void onChanges() {
                host.navigation().push(WorkspaceScreens.CHANGES.key(Params.Empty.INSTANCE));
            }
        };
    }

    /** Workspace projects page; cached so SAF results can trigger a reload. */
    private View workspaceProjectsPage() {
        workspacePage = new WorkspaceProjectsPage(host.activity(), host.tokens(),
                host.navigation()::back, workspaceListener());
        return workspacePage;
    }

    /** Model list page with a late loader that re-reads the store on each visit. */
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
                }, host.graph().scheduler());
        page.loader(() -> host.graph().models().list(), this::selectedModelId);
        return page;
    }

    /** Model add-options page; the local entry is hidden in the slim build. */
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

    /** Custom or preset model editor page; saves select the model and pop back. */
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

    /** Local GGUF model page from the optional bridge; falls back when absent. */
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

    /** Id of the currently selected model; empty when none is selected. */
    private String selectedModelId() {
        cn.lineai.core.model.ModelConfig selected = host.graph().models().selected();
        return selected == null ? "" : selected.id;
    }
}
