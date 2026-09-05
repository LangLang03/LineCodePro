package cn.lineai.mvp;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.content.SharedPreferences;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.ai.protocol.CodexResponsesProtocol;
import cn.lineai.ai.protocol.OpenAiResponsesCompactionProtocol;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;
import cn.lineai.context.TokenUsageTracker;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.importer.LineCodeArchiveService;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationRepository;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.FileTreeRepository;
import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.IpcFileTreeRepository;
import cn.lineai.data.repository.IpcFileTreeStore;
import cn.lineai.data.repository.IpcProviderRepository;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.data.repository.KeepAliveRepository;
import cn.lineai.data.repository.LearningContextRepository;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.service.LearningContextService;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.PhoneControlRepository;
import cn.lineai.service.AccessibilityStateProvider;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.data.repository.ProjectRepository;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SettingsRepository;
import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.data.repository.SshFileTreeRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.StorageStatsRepository;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.data.service.ContextResourceProvider;
import cn.lineai.resource.ResourceProvider;
import cn.lineai.resource.SystemConfigProvider;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.log.ErrorLogRepository;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderScanner;
import cn.lineai.data.repository.ModelRepository;
import cn.lineai.model.ModelStore;
import cn.lineai.share.ExportFormatResolver;
import cn.lineai.ssh.SshService;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.ToolCategoryResolver;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolPromptRenderer;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ui.AgentPipelineToolCallViewFactory;
import cn.lineai.tool.ui.AgentToolCallViewFactory;
import cn.lineai.tool.ui.DeleteToolCallViewFactory;
import cn.lineai.tool.ui.GenericToolCallViewFactory;
import cn.lineai.tool.ui.ImageGenerationToolCallViewFactory;
import cn.lineai.tool.ui.PhoneControlToolCallViewFactory;
import cn.lineai.tool.ui.ReadToolCallViewFactory;
import cn.lineai.tool.ui.ShellToolCallViewFactory;
import cn.lineai.tool.ui.TodoToolCallViewFactory;
import cn.lineai.tool.ui.ToolCallViewFactoryRegistry;
import cn.lineai.tool.ui.WriteToolCallViewFactory;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.WorkspacePaths;
import cn.lineai.workspace.StoragePermissionManager;

public final class MainDependencies {
    final Context context;
    final ModelStore modelRepository;
    final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    final ChatModeRepository chatModeRepository;
    final InputSettingsRepository inputSettingsRepository;
    final OutputSettingsRepository outputSettingsRepository;
    final ThemeSettingsRepository themeSettingsRepository;
    final PromptTemplateRepository promptTemplateRepository;
    final ConversationStore conversationRepository;
    final ProjectStore projectRepository;
    final LearningContextStore learningContextRepository;
    final LearningContextService learningContextService;
    final ToolSettingsStore toolSettingsRepository;
    final ExtensionStore extensionRepository;
    final IpcProviderStore ipcProviderRepository;
    final IpcProviderScanner ipcProviderScanner;
    final IpcProviderManager ipcProviderManager;
    final DiffStore diffRepository;
    final FileTreeStore fileTreeRepository;
    final IpcFileTreeStore ipcFileTreeRepository;
    final SshService sshService;
    final SshFileTreeStore sshFileTreeRepository;
    final ContextManager contextManager;
    final ContextCompactionService contextCompactionService;
    final ModelClient modelClient;
    final TokenUsageTracker tokenUsageTracker;
    final ToolRegistry toolRegistry;
    final ToolExecutor toolExecutor;
    final ToolExecutionCoordinator toolExecutionCoordinator;
    final SystemPromptProvider systemPromptProvider;
    final StoragePermissionManager storagePermissionManager;
    final SafPathResolver safPathResolver;
    final MainThreadDispatcher mainThreadDispatcher;
    final BackgroundTaskRunner backgroundTaskRunner;
    final LineCodeArchiveService lineCodeArchiveService;
    final TodoStateStore todoStateStore;
    final ToolCallViewFactoryRegistry toolCallViewFactoryRegistry;
    final PhoneControlRepository phoneControlRepository;
    final ErrorLogRepository errorLogRepository;
    final StorageStatsRepository storageStatsRepository;
    final KeepAliveRepository keepAliveRepository;
    final PhoneControlController phoneControlController;
    final ErrorLogController errorLogController;
    public final ExportFormatResolver exportFormatResolver;
    public final ShareController shareController;
    public final QuoteController quoteController;

    public MainDependencies(Context context) {
        this.context = context.getApplicationContext();
        Context appContext = this.context;

        // Shared infrastructure - constructed once, injected everywhere
        LineCodeDatabase database = LineCodeDatabase.getInstance(appContext);
        SettingsRepository settingsRepository = new SettingsRepository(database);
        ResourceProvider resourceProvider = new ContextResourceProvider(appContext);
        SystemConfigProvider systemConfigProvider = new ContextSystemConfigProvider(appContext);

        modelRepository = new ModelRepository(database, appContext.getSharedPreferences("linecode_models", Context.MODE_PRIVATE));
        aiBehaviorSettingsRepository = new AiBehaviorSettingsRepository(settingsRepository);
        chatModeRepository = new ChatModeRepository(settingsRepository);
        inputSettingsRepository = new InputSettingsRepository(settingsRepository);
        outputSettingsRepository = new OutputSettingsRepository(settingsRepository);
        cn.lineai.security.UrlPolicy.setRelaxedHttpEnabled(outputSettingsRepository.get().isAllowAnyHttp());
        themeSettingsRepository = new ThemeSettingsRepository(systemConfigProvider, settingsRepository);
        promptTemplateRepository = new PromptTemplateRepository(resourceProvider, settingsRepository);
        LineTheme.apply(themeSettingsRepository.resolveCurrentPalette());
        conversationRepository = new ConversationRepository(database);
        WorkspacePaths workspacePaths = new WorkspacePaths(context);
        projectRepository = new ProjectRepository(database, settingsRepository, workspacePaths);
        learningContextRepository = new LearningContextRepository(database, workspacePaths, promptTemplateRepository);
        learningContextService = new LearningContextService((LearningContextRepository) learningContextRepository, new cn.lineai.ai.prompt.MemoryPromptBuilder(workspacePaths, promptTemplateRepository));
        phoneControlRepository = new PhoneControlRepository(settingsRepository, new AccessibilityStateProvider() {
            @Override
            public boolean isAccessibilityEnabled() {
                return LineCodeAccessibilityService.isServiceEnabled(context);
            }
        });
        cn.lineai.tool.builtin.PhoneControlToolSupport.inject(
                LineCodeAccessibilityService.getReadyInstance(context),
                ctx -> LineCodeAccessibilityService.isServiceEnabled(ctx));
        ToolCategoryResolver categoryResolver =
                toolName -> cn.lineai.tool.ui.ToolCallUtils.getDisplayCategory(toolName);
        WebSearchConfigRepository webSearchConfigRepository = new WebSearchConfigRepository(settingsRepository);
        ToolSettingsRepository toolSettingsRepo = new ToolSettingsRepository(resourceProvider, settingsRepository, webSearchConfigRepository, phoneControlRepository, categoryResolver);
        toolSettingsRepository = toolSettingsRepo;
        cn.lineai.data.service.SkillFileManager skillFileManager = new cn.lineai.data.service.SkillFileManager(workspacePaths, appContext, resourceProvider);
        extensionRepository = new ExtensionRepository(database, resourceProvider, skillFileManager, new cn.lineai.ai.SkillPromptProvider() {
            @Override
            public String buildExtensionPrompt(String skillName, String skillContent, String workDirectory) {
                StringBuilder sb = new StringBuilder();
                sb.append("#### Skill: ").append(skillName)
                        .append("\nRoot: ").append(workDirectory);
                if (skillContent != null && skillContent.length() > 0) {
                    sb.append("\n\n").append(skillContent);
                }
                return sb.toString();
            }
        });
        ipcProviderRepository = new IpcProviderRepository(database);
        ipcProviderScanner = new IpcProviderScanner();
        ipcProviderManager = new IpcProviderManager(context);
        diffRepository = new DiffRepository(database);
        fileTreeRepository = new FileTreeRepository();
        sshService = new SshService(context);
        sshFileTreeRepository = new SshFileTreeRepository(sshService);
        ipcFileTreeRepository = new IpcFileTreeRepository(ipcProviderManager);
        contextManager = new ContextManager();
        modelClient = new ModelClient();
        tokenUsageTracker = new TokenUsageTracker();
        contextCompactionService = new ContextCompactionService(
                modelClient,
                new OpenAiResponsesCompactionProtocol(),
                new CodexResponsesProtocol(),
                promptTemplateRepository,
                tokenUsageTracker);
        toolRegistry = new ToolRegistry(context, ipcProviderManager);
        toolRegistry.setExtensionStore((ExtensionStore) extensionRepository);
        toolSettingsRepo.setToolRegistry(toolRegistry);
        cn.lineai.tool.ToolPermissionService toolPermissionService = new cn.lineai.tool.ToolPermissionService(toolSettingsRepository, toolRegistry);
        toolSettingsRepo.setToolPermissionService(toolPermissionService);
        ToolPromptRenderer promptRenderer =
                (enabledTools, includeConfirmationHints) -> {
                    java.util.Set<String> enabled = new java.util.HashSet<>();
                    java.util.Map<String, ToolInfo> toolByName = new java.util.LinkedHashMap<>();
                    for (ToolInfo tool : enabledTools) {
                        enabled.add(tool.getName());
                        toolByName.put(tool.getName(), tool);
                    }
                    return cn.lineai.ai.prompt.ToolPromptRenderer.renderToolPrompt(
                            toolSettingsRepository.getExecutionMode(),
                            toolSettingsRepository.getConfigs(),
                            enabled, toolByName, includeConfirmationHints);
                };
        cn.lineai.ai.prompt.ToolPromptService toolPromptService = new cn.lineai.ai.prompt.ToolPromptService(toolSettingsRepository, toolRegistry, promptRenderer);
        toolSettingsRepo.setToolPromptService(toolPromptService);
        cn.lineai.tool.ToolDisplayResolver.setDefault(new cn.lineai.tool.ToolDisplayResolver(toolRegistry));
        cn.lineai.tool.ui.ToolInfoResolverProvider.setDefault(cn.lineai.tool.ToolDisplayResolver.getDefault());
        toolCallViewFactoryRegistry = createToolCallViewFactoryRegistry();
        cn.lineai.tool.ui.ToolCallViewFactoryRegistry.setDefault(toolCallViewFactoryRegistry);
        toolExecutor = new ToolExecutor(toolRegistry, toolSettingsRepository, new cn.lineai.tool.DiffRecorder(diffRepository),
                (ModelStore) modelRepository, sshFileTreeRepository, new cn.lineai.ai.DefaultModelServiceProvider(),
                promptTemplateRepository, learningContextRepository);
        toolExecutionCoordinator = new ToolExecutionCoordinator(toolRegistry);
        systemPromptProvider = new SystemPromptProvider(context, promptTemplateRepository);
        storagePermissionManager = new StoragePermissionManager(context);
        safPathResolver = new SafPathResolver();
        mainThreadDispatcher = new MainThreadDispatcher();
        backgroundTaskRunner = new BackgroundTaskRunner();
        lineCodeArchiveService = new LineCodeArchiveService(context);
        todoStateStore = new TodoStateStore();
        chatModeRepository.initialize();
        chatModeRepository.applyPermissionForMode(chatModeRepository.getMode(), toolSettingsRepository);
        errorLogRepository = new ErrorLogRepository(appContext.getFilesDir().getAbsolutePath());
        storageStatsRepository = new StorageStatsRepository(systemConfigProvider, database);
        keepAliveRepository = new KeepAliveRepository(appContext.getSharedPreferences("linecode_keep_alive", Context.MODE_PRIVATE));
        phoneControlController = new PhoneControlController(phoneControlRepository);
        errorLogController = new ErrorLogController(errorLogRepository);
        this.exportFormatResolver = new ExportFormatResolver();
        this.shareController = new ShareController(exportFormatResolver);
        this.quoteController = new QuoteController();
    }

    private ToolCallViewFactoryRegistry createToolCallViewFactoryRegistry() {
        cn.lineai.tool.ui.DiffLoader diffLoader =
                diffId -> {
                    cn.lineai.data.repository.DiffRecord r = diffRepository.getDiff(diffId);
                    if (r == null) return null;
                    return new cn.lineai.model.DiffUiModel(r.getId(), r.getFilePath(), r.getOldContent(), r.getNewContent(), r.isReverted());
                };
        ToolCallViewFactoryRegistry registry = new ToolCallViewFactoryRegistry();
        registry.register(new ShellToolCallViewFactory());
        registry.register(new TodoToolCallViewFactory());
        registry.register(new AgentToolCallViewFactory());
        registry.register(new AgentPipelineToolCallViewFactory());
        registry.register(new ReadToolCallViewFactory());
        registry.register(new ImageGenerationToolCallViewFactory());
        registry.register(new PhoneControlToolCallViewFactory());
        registry.register(new WriteToolCallViewFactory(diffLoader));
        registry.register(new DeleteToolCallViewFactory());
        registry.register(new GenericToolCallViewFactory());
        // 按工具声明的视图类绑定（BaseTool.getToolCallViewClass()）；未声明视图类的工具走分类回退。
        registry.register(cn.lineai.tool.ui.ToolCallReadView.class, new ReadToolCallViewFactory());
        registry.register(cn.lineai.tool.ui.ToolCallWriteView.class, new WriteToolCallViewFactory(diffLoader));
        registry.register(cn.lineai.tool.ui.ToolCallShellView.class, new ShellToolCallViewFactory());
        registry.register(cn.lineai.tool.ui.ToolCallDeleteView.class, new DeleteToolCallViewFactory());
        registry.register(cn.lineai.tool.ui.ToolCallTodoView.class, new TodoToolCallViewFactory());
        registry.register(cn.lineai.tool.ui.ToolCallAgentView.class, new AgentToolCallViewFactory());
        registry.register(cn.lineai.tool.ui.ToolCallAgentPipelineView.class, new AgentPipelineToolCallViewFactory());
        registry.register(cn.lineai.tool.ui.ToolCallGenericView.class, new GenericToolCallViewFactory());
        return registry;
    }

    private static final class ContextSystemConfigProvider implements SystemConfigProvider {
        private final Context context;

        ContextSystemConfigProvider(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public boolean isDarkModeEnabled() {
            return (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        @Override
        public int getSdkInt() {
            return android.os.Build.VERSION.SDK_INT;
        }

        @Override
        public String getFilesDirPath() {
            return context.getFilesDir().getAbsolutePath();
        }

        @Override
        public String getDatabasePath(String name) {
            java.io.File file = context.getDatabasePath(name);
            return file != null ? file.getAbsolutePath() : null;
        }

        @Override
        public String getExternalFilesDirPath() {
            java.io.File dir = context.getExternalFilesDir(null);
            return dir != null ? dir.getAbsolutePath() : null;
        }
    }
}
