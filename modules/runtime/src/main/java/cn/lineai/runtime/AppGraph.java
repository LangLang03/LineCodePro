package cn.lineai.runtime;

import android.content.Context;
import android.content.res.Configuration;

import java.io.File;

import cn.lineai.core.kernel.di.Graph;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Project;
import cn.lineai.data.api.ConversationStore;
import cn.lineai.data.api.DiffStore;
import cn.lineai.data.api.MessageStore;
import cn.lineai.data.api.McpExtensionStore;
import cn.lineai.data.api.ProjectStore;
import cn.lineai.data.api.MemoryStore;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.ArchiveService;
import cn.lineai.data.api.AgentExtensionStore;
import cn.lineai.data.api.ErrorLogStore;
import cn.lineai.data.api.StorageStatsStore;
import cn.lineai.data.api.TerminalProviderStore;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.data.migration.LegacyFiles;
import cn.lineai.data.migration.LegacyMigration;
import cn.lineai.data.api.SkillStore;
import cn.lineai.data.sqlite.LineCodeDatabase;
import cn.lineai.data.sqlite.SqliteConversationStore;
import cn.lineai.data.sqlite.SqliteDiffStore;
import cn.lineai.data.sqlite.SqliteMessageStore;
import cn.lineai.data.sqlite.SqliteMcpExtensionStore;
import cn.lineai.data.sqlite.SqliteMemoryStore;
import cn.lineai.data.sqlite.SqliteModelStore;
import cn.lineai.data.sqlite.SqliteProjectStore;
import cn.lineai.data.sqlite.SqliteAgentExtensionStore;
import cn.lineai.data.sqlite.SqliteArchiveService;
import cn.lineai.data.sqlite.SqliteErrorLogStore;
import cn.lineai.data.sqlite.FileSkillStore;
import cn.lineai.data.sqlite.SqliteStorageStatsStore;
import cn.lineai.data.sqlite.SqliteTerminalProviderStore;
import cn.lineai.data.sqlite.SqliteSettingsStore;
import cn.lineai.engine.ai.HttpModelClient;
import cn.lineai.engine.ai.ImageGenerationClient;
import cn.lineai.engine.ai.McpClient;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.ai.VisionClient;
import cn.lineai.engine.prompt.AssetPromptCatalog;
import cn.lineai.engine.prompt.PromptCatalog;
import cn.lineai.engine.prompt.PromptStore;
import cn.lineai.engine.prompt.SystemPromptAssembler;
import cn.lineai.feature.chat.ChatController;
import cn.lineai.feature.chat.DefaultPromptExtensions;
import cn.lineai.feature.settings.McpPreferences;
import cn.lineai.platform.AndroidScheduler;
import cn.lineai.tool.api.BackgroundTasks;
import cn.lineai.tool.api.DiffSink;
import cn.lineai.tool.api.ToolCatalog;
import cn.lineai.tool.agent.AgentRunner;
import cn.lineai.tool.agent.AgentTools;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.mcp.McpToolCatalog;
import cn.lineai.tool.phone.PhoneTools;
import cn.lineai.tool.media.MediaTools;
import cn.lineai.tool.workspace.BuiltinTools;
import cn.lineai.ui.tokens.ThemeController;
import cn.lineai.core.mvi.present.Store;
import cn.lineai.core.service.SettingsAction;
import cn.lineai.shell.BackgroundShellRegistry;
import cn.lineai.feature.chat.LearningContextProvider;
import cn.lineai.shell.SshWorkspace;
import cn.lineai.shell.TerminalProviderGateway;
import cn.lineai.feature.settings.SettingsPresenter;
import cn.lineai.feature.settings.SettingsState;

/**
 * Composition root for the rewritten shell.
 *
 * <p>Application-scoped graph only: stores, scheduler, theme and the chat controller. Activity and
 * screen scopes are created as children, so nothing here needs a static mutable holder.</p>
 */
public final class AppGraph implements AutoCloseable {

    private final Graph graph;
    private final AndroidScheduler scheduler;
    private final LineCodeDatabase database;
    private final SettingsStore settings;
    private final ModelStore models;
    private final StorageStatsStore storageStats;
    private final ErrorLogStore errorLogs;
    private final ArchiveService archives;
    private final MemoryStore memories;
    private final AgentExtensionStore agentExtensions;
    private final McpExtensionStore mcpExtensions;
    private final SkillStore skills;
    private final ProjectStore projects;
    private final DiffStore diffs;
    private final ConversationStore conversationStore;
    private final cn.lineai.tool.api.TodoStore sessionTodos;
    private final TerminalProviderStore terminalProviders;
    private final McpPreferences mcpPreferences;
    private final TerminalProviderGateway terminalProviderGateway;
    private final BackgroundShellRegistry backgroundShellRegistry;
    private final Context context;
    private final ToolCatalog toolCatalog;
    private final ToolCatalog baseCatalogForAgents;
    private final ModelClient modelClient;
    private final ToolContext toolContextForAgents;
    private volatile AgentRunner agentRunner;
    private final PromptStore prompts;
    private final ThemeController theme;
    private final ChatController chat;
    private final Store<SettingsState, SettingsAction> preferences;
    private final cn.lineai.feature.settings.ThemePreferences themePreferences;

    private AppGraph(Context context) {
        this.context = context.getApplicationContext();
        scheduler = new AndroidScheduler();
        File dbFile = new File(context.getDatabasePath("linecode-v2.db").getAbsolutePath());
        database = new LineCodeDatabase(dbFile);
        LegacyMigration.migrate(context.getDatabasePath("linecode.db"), database);
        Clock clock = Clock.system();

        final SettingsStore settingsStore = new SqliteSettingsStore(database, clock);
        final ConversationStore conversations = new SqliteConversationStore(database, clock);
        final MessageStore messages = new SqliteMessageStore(database);
        final ModelStore modelStore = new SqliteModelStore(database);
        modelStore.seedFromSettings(settingsStore);
        final StorageStatsStore statsStore = new SqliteStorageStatsStore(database,
                context.getFilesDir());
        final ErrorLogStore errorLogStore = new SqliteErrorLogStore(database, clock);
        final ArchiveService archiveService = new SqliteArchiveService(database, clock, context.getFilesDir());
        final MemoryStore memoryStore = new SqliteMemoryStore(database, clock);
        final AgentExtensionStore agentExtensionStore = new SqliteAgentExtensionStore(database, clock);
        final McpExtensionStore mcpExtensionStore = new SqliteMcpExtensionStore(database, clock);
        final File skillsRoot = new File(context.getFilesDir(), "skills");
        LegacyFiles.copySkills(new File(context.getFilesDir(), ".linecode/skills"),
                skillsRoot);
        final SkillStore skillStore = new FileSkillStore(
                skillsRoot, settingsStore, clock);
        final TerminalProviderStore providerStore = new SqliteTerminalProviderStore(database, clock);
        final ModelClient httpClient = new HttpModelClient(scheduler);
        final LocalModelBridge localBridge = LocalModelBridges.find();
        final ModelClient client = localBridge == null
                ? httpClient : localBridge.wrap(httpClient, scheduler);
        final PromptStore promptStore = promptStore(context, settingsStore);

        this.settings = settingsStore;
        this.models = modelStore;
        this.storageStats = statsStore;
        this.errorLogs = errorLogStore;
        this.archives = archiveService;
        this.memories = memoryStore;
        this.agentExtensions = agentExtensionStore;
        this.mcpExtensions = mcpExtensionStore;
        this.skills = skillStore;
        this.terminalProviders = providerStore;
        this.mcpPreferences = new McpPreferences(settingsStore);
        this.terminalProviderGateway = new TerminalProviderGateway(this.context, settingsStore,
                providerStore, () -> cn.lineai.feature.settings.McpPreferences
                        .MODE_TERMINAL_PROVIDER.equals(mcpPreferences.mode()));
        this.prompts = promptStore;
        final File workspace = new File(context.getFilesDir(), "workspace");
        workspace.mkdirs();
        final ProjectStore projectStore = new SqliteProjectStore(database, clock);
        final DiffStore diffStore = new SqliteDiffStore(database, clock);
        final Project defaultProject = defaultProject(projectStore, workspace, settingsStore);
        this.projects = projectStore;
        this.diffs = diffStore;
        this.conversationStore = conversations;
        // Terminal provider mode owns remote background tasks via AIDL v2 handles; SSH
        // workspaces keep the temp-log backend (unless an SSH project is selected).
        this.backgroundShellRegistry = new BackgroundShellRegistry(this::runRemote,
                terminalProviderGateway.providerBackground(),
                () -> !SshWorkspace.isSsh(projectStore.selected())
                        && terminalProviderGateway.terminalMode());
        final cn.lineai.feature.settings.ToolPreferences toolPreferences =
                new cn.lineai.feature.settings.ToolPreferences(settingsStore);
        final VisionClient visionClient = new VisionClient();
        final ImageGenerationClient imageClient = new ImageGenerationClient();
        final AccessibilityPhoneControl phoneControl = new AccessibilityPhoneControl(context);
        final ToolCatalog baseTools = () -> PhoneTools.augment(
                MediaTools.augment(
                        BuiltinTools.registry(toolPreferences.webSearch()),
                        () -> modelStore.find(toolPreferences.imageUnderstandingModelId()),
                        () -> modelStore.find(toolPreferences.imageGenerationModelId()),
                        visionClient::describe, imageClient::generate),
                () -> phoneControl);
        final MemoryTodoStore todoStore = new MemoryTodoStore();
        this.sessionTodos = todoStore;
        final ToolContext toolContext = toolContext(context, clock, settingsStore, projectStore,
                diffStore, memoryStore, modelStore, todoStore);
        final ToolCatalog baseCatalog = new McpToolCatalog(baseTools,
                mcpExtensionStore::list, new McpClient()::callTool);
        final ToolCatalog toolCatalog = toolCatalog(baseCatalog, agentExtensionStore, projectStore);
        this.toolCatalog = toolCatalog;
        this.baseCatalogForAgents = () -> applyWorkspacePolicy(baseCatalog.registry());
        this.modelClient = client;
        this.toolContextForAgents = toolContext;
        this.chat = new ChatController(conversations, messages, modelStore, settingsStore,
                client, scheduler, clock, new SystemPromptAssembler(promptStore),
                toolCatalog, toolContext,
                new DefaultPromptExtensions(agentExtensionStore::list, skillStore::enabled,
                        todoStore::items,
                        new LearningContextProvider(settingsStore, memoryStore, conversations,
                                skillStore, projectStore, promptStore, skillsRoot)::build));
        this.chat.setErrorLogStore(errorLogStore);
        this.themePreferences = new cn.lineai.feature.settings.ThemePreferences(settingsStore, scheduler);
        this.theme = new ThemeController(themePreferences.initial(isNight(context)));
        this.preferences = new Store<>(new SettingsPresenter(settingsStore), scheduler);
        preferences.dispatch(new SettingsAction.Load());

        this.graph = buildGraph(clock, messages);
        terminalProviderGateway.start();
    }

    private Graph buildGraph(Clock clock, MessageStore messages) {
        Graph.Builder builder = new Graph.Builder();
        builder.instance(Scheduler.class, scheduler);
        builder.instance(Clock.class, clock);
        builder.instance(LineCodeDatabase.class, database);
        builder.instance(SettingsStore.class, settings);
        builder.instance(ConversationStore.class, conversationStore);
        builder.instance(MessageStore.class, messages);
        builder.instance(ModelStore.class, models);
        builder.instance(ModelClient.class, modelClient);
        builder.instance(ChatController.class, chat);
        builder.instance(ThemeController.class, theme);
        return builder.build();
    }

    private static Project defaultProject(ProjectStore projects, File workspace,
                                          SettingsStore settingsStore) {
        Project project = projects.ensureDefault(workspace);
        settingsStore.put(cn.lineai.feature.chat.ChatController.KEY_PROJECT_LABEL,
                project.name);
        return project;
    }

    private static PromptStore promptStore(Context context, SettingsStore settingsStore) {
        PromptCatalog catalog = new AssetPromptCatalog(
                path -> context.getAssets().open(path));
        return new PromptStore(catalog, new SettingsPromptOverrides(settingsStore));
    }

    private ToolCatalog toolCatalog(ToolCatalog baseCatalog, AgentExtensionStore agents,
                                    ProjectStore projects) {
        return () -> applyWorkspacePolicy(AgentTools.augment(baseCatalog.registry(),
                agents::list, agentRunner));
    }

    /**
     * Remote workspaces (SSH host or terminal provider guest) keep only tools that do not require
     * the local filesystem; local workspaces obey the local-shell preference.
     */
    private cn.lineai.tool.api.ToolRegistry applyWorkspacePolicy(
            cn.lineai.tool.api.ToolRegistry registry) {
        if (remoteWorkspaceActive()) {
            return cn.lineai.tool.workspace.LocalToolPolicy.apply(SshWorkspace.remoteOnly(registry),
                    mcpPreferences.remoteShell());
        }
        return cn.lineai.tool.workspace.LocalToolPolicy.apply(registry, mcpPreferences.localShell());
    }

    private boolean remoteWorkspaceActive() {
        return SshWorkspace.isSsh(projects.selected()) || terminalProviderGateway.terminalMode();
    }

    private ToolContext toolContext(Context context, Clock clock, SettingsStore settings,
                                    ProjectStore projects, DiffStore diffs,
                                    MemoryStore memories, ModelStore models,
                                    MemoryTodoStore todoStore) {
        File workspace = new File(context.getFilesDir(), "workspace");
        return new ToolContext() {
            @Override public cn.lineai.tool.api.TodoStore todos() { return todoStore; }
            @Override public cn.lineai.tool.api.MemorySink memory() {
                return (scope, title, content) -> {
                    Project selected = projects.selected();
                    String projectId = selected == null ? "" : selected.path;
                    long now = clock.nowMs();
                    memories.save(new cn.lineai.core.model.MemoryItem("", scope, projectId,
                            title, content, "manual", 1.0, now, now, 0, 0));
                };
            }
            @Override public DiffSink diffs() {
                return (path, before, after, existedBefore) -> {
                    Project selected = projects.selected();
                    diffs.record(selected == null ? workspace.getAbsolutePath() : selected.path,
                            path, before, after, existedBefore);
                };
            }
            @Override public File workspaceRoot() {
                Project selected = projects.selected();
                return selected == null ? workspace : new File(selected.path);
            }
            @Override public String modelId() {
                ModelConfig selected = models.selected();
                return selected == null ? "" : selected.modelId;
            }
            @Override public boolean mutationAllowed() { return true; }
            @Override public boolean pathProtectionBypassed() {
                return Boolean.parseBoolean(settings.get(
                        cn.lineai.core.model.UiPreferences.Field.BYPASS_PATH_PROTECTION.key,
                        "false"));
            }
            @Override public boolean remoteWorkspace() {
                return remoteWorkspaceActive();
            }
            @Override public String remoteDirectory() {
                if (SshWorkspace.isSsh(projects.selected())) {
                    return SshWorkspace.directory(projects.selected());
                }
                return terminalProviderGateway.terminalMode() ? terminalProviderGateway.home() : "";
            }
            @Override public String remoteExecute(String command, int timeoutMs) {
                return runRemote(command, timeoutMs);
            }
            @Override public BackgroundTasks backgrounds() {
                return backgroundShellRegistry;
            }
        };
    }

    /** One blocking remote execution on the active workspace; shared by tools and background SSH tasks. */
    private String runRemote(String command, int timeoutMs) {
        if (SshWorkspace.isSsh(projects.selected())) {
            return SshWorkspace.execute(context, settings, command, timeoutMs);
        }
        if (terminalProviderGateway.terminalMode()) {
            return terminalProviderGateway.execute(command, timeoutMs);
        }
        throw new DomainException(ErrorCode.INVALID_INPUT,
                "remote execution is unavailable in the local workspace");
    }

    public static AppGraph create(Context context) {
        return new AppGraph(context.getApplicationContext());
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public Graph graph() {
        return graph;
    }

    public ChatController chat() {
        return chat;
    }

    public ThemeController theme() {
        return theme;
    }

    public SettingsStore settings() {
        return settings;
    }

    public Store<SettingsState, SettingsAction> preferences() { return preferences; }

    public cn.lineai.feature.settings.ThemePreferences themePreferences() { return themePreferences; }

    public ModelStore models() { return models; }

    public StorageStatsStore storageStats() { return storageStats; }

    public ErrorLogStore errorLogs() { return errorLogs; }

    public ArchiveService archives() { return archives; }

    public MemoryStore memories() { return memories; }

    public AgentExtensionStore agentExtensions() { return agentExtensions; }

    public McpExtensionStore mcpExtensions() { return mcpExtensions; }

    public SkillStore skills() { return skills; }

    public ProjectStore projects() { return projects; }

    public DiffStore diffs() { return diffs; }

    public ConversationStore conversations() { return conversationStore; }

    public cn.lineai.tool.api.TodoStore todos() { return sessionTodos; }

    public TerminalProviderStore terminalProviders() { return terminalProviders; }

    public void refreshTerminalProviders() { terminalProviderGateway.refresh(); }

    public int terminalProviderState() { return terminalProviderGateway.state(); }

    public String terminalProviderName() { return terminalProviderGateway.providerName(); }

    public String probeTerminalProvider() { return terminalProviderGateway.probe(); }

    public boolean trustsProviderSignature(String packageName) {
        return terminalProviderGateway.trusted(packageName);
    }

    public ToolCatalog tools() { return toolCatalog; }

    public void setAgentRunner(AgentRunner runner) {
        this.agentRunner = runner;
    }

    public ToolCatalog agentBaseTools() { return baseCatalogForAgents; }

    public ModelClient modelClient() { return modelClient; }

    public ToolContext agentToolContext() { return toolContextForAgents; }

    public Scheduler scheduler() { return scheduler; }

    public PromptStore prompts() { return prompts; }

    @Override
    public void close() {
        backgroundShellRegistry.shutdown();
        terminalProviderGateway.shutdown();
        preferences.close();
        graph.close();
        scheduler.close();
        database.close();
    }
}
