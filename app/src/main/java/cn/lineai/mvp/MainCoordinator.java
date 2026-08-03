package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;

import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.IpcFileTreeStore;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.data.repository.KeepAliveRepository;
import cn.lineai.data.repository.StorageStatsRepository;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.ScannedProvider;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ConversationUiModel;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.data.model.ExtensionOverviewState;
import cn.lineai.model.InputSettings;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.model.SkillRecord;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.security.UrlPolicy;
import cn.lineai.tool.BaseTool;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.KeepAliveSettings;
import cn.lineai.model.StorageStatsUiModel;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import cn.lineai.model.SshConfig;
import cn.lineai.ssh.SshService;
import cn.lineai.ssh.TermuxHelper;
import cn.lineai.service.KeepAliveService;
import cn.lineai.log.ErrorLogEntry;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MainCoordinator implements MainUiController {
    String agentTerminatedMessage() {
        return context.getString(R.string.message_agent_terminated);
    }

    private final Context context;

    private final ChatSessionStore chatSessionStore = new ChatSessionStore();
    private final ArrayList<ChatMessage> messages = chatSessionStore.mutableMessages();
    private final ScreenNavigationController screenNavigationController = new ScreenNavigationController();
    private final MainThreadDispatcher mainThread;
    private final BackgroundTaskRunner backgroundTasks;
    ChatUiStateAssembler chatUiStateAssembler;
    ToolMessageController toolMessageController;
    ToolReviewController toolReviewController;
    ConversationPersistenceController conversationPersistenceController;
    ExtensionDraftController extensionDraftController;
    ExtensionManagementController extensionManagementController;
    ModelPromptController modelPromptController;
    DirectoryPickerController directoryPickerController;
    StorageMaintenanceController storageMaintenanceController;
    private final PhoneControlController phoneControlController;
    private final ErrorLogController errorLogController;
    private final cn.lineai.data.repository.StorageStatsRepository storageStatsRepository;
    private final cn.lineai.data.repository.KeepAliveRepository keepAliveRepository;
    ContextCompactionController contextCompactionController;
    IpcProviderController ipcProviderController;
    final GenerationController generationController = new GenerationController();
    GenerationLifecycleController generationLifecycleController;
    GenerationFlowController generationFlowController;
    ChatInteractionController chatInteractionController;
    ModelInteractionController modelInteractionController;
    OverlayActionController overlayActionController;
    ModelManagementController modelManagementController;
    SettingsManagementController settingsManagementController;
    SshFileTreeController sshFileTreeController;
    IpcFileTreeController ipcFileTreeController;
    FileTreeInteractionController fileTreeInteractionController;
    FileOperationController fileOperationController;
    PermissionModeController permissionModeController;
    ProjectWorkspaceController projectWorkspaceController;
    private final ModelStore modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ChatModeRepository chatModeRepository;
    private final InputSettingsRepository inputSettingsRepository;
    private final OutputSettingsRepository outputSettingsRepository;
    private final ThemeSettingsRepository themeSettingsRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ConversationStore conversationRepository;
    private final ProjectStore projectRepository;
    private final LearningContextStore learningContextRepository;
    private final ToolSettingsStore toolSettingsRepository;
    private final ExtensionStore extensionRepository;
    private final IpcProviderStore ipcProviderRepository;
    private final cn.lineai.ipc.IpcProviderManager ipcProviderManager;
    private final DiffStore diffRepository;
    private final FileTreeStore fileTreeRepository;
    private final IpcFileTreeStore ipcFileTreeRepository;
    private final SshService sshService;
    private final SshFileTreeStore sshFileTreeRepository;
    private final ContextManager contextManager;
    private final ContextCompactionService contextCompactionService;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolExecutionCoordinator toolExecutionCoordinator;
    private final SystemPromptProvider systemPromptProvider;
    private final StoragePermissionManager storagePermissionManager;
    private final SafPathResolver safPathResolver;
    LineCodeArchiveController lineCodeArchiveController;
    AgentExecutionController agentExecutionController;
    private final cn.lineai.state.TodoStateStore todoStateStore;
    private final ViewProxy viewProxy = new ViewProxy();
    private final ScreenNavigationController.Host navigationHost = new ScreenNavigationController.Host() {
        @Override
        public void hideOverlays() {
            viewProxy.hideOverlays();
        }

        @Override
        public void showScreen(String screenId) {
            viewProxy.showScreen(screenId);
        }

        @Override
        public void showScreen(String screenId, boolean forward) {
            viewProxy.showScreen(screenId, forward);
        }

        @Override
        public void showScreen(String screenId, boolean forward, boolean animate) {
            viewProxy.showScreen(screenId, forward, animate);
        }

        @Override
        public void showChatScreen() {
            viewProxy.showChatScreen();
        }
    };
    private final ProjectRuntimeState projectState = new ProjectRuntimeState();
    AttachmentPickerCoordinator attachmentPickerController;

    public MainCoordinator(Context context) {
        this(new MainDependencies(context));
    }

    public MainCoordinator(MainDependencies dependencies) {
        // === assignDependencies ===
        this.context = dependencies.context;
        modelRepository = dependencies.modelRepository;
        aiBehaviorSettingsRepository = dependencies.aiBehaviorSettingsRepository;
        chatModeRepository = dependencies.chatModeRepository;
        inputSettingsRepository = dependencies.inputSettingsRepository;
        outputSettingsRepository = dependencies.outputSettingsRepository;
        themeSettingsRepository = dependencies.themeSettingsRepository;
        promptTemplateRepository = dependencies.promptTemplateRepository;
        conversationRepository = dependencies.conversationRepository;
        projectRepository = dependencies.projectRepository;
        learningContextRepository = dependencies.learningContextRepository;
        toolSettingsRepository = dependencies.toolSettingsRepository;
        extensionRepository = dependencies.extensionRepository;
        ipcProviderRepository = dependencies.ipcProviderRepository;
        ipcProviderManager = dependencies.ipcProviderManager;
        diffRepository = dependencies.diffRepository;
        fileTreeRepository = dependencies.fileTreeRepository;
        ipcFileTreeRepository = dependencies.ipcFileTreeRepository;
        sshService = dependencies.sshService;
        sshFileTreeRepository = dependencies.sshFileTreeRepository;
        contextManager = dependencies.contextManager;
        contextCompactionService = dependencies.contextCompactionService;
        modelClient = dependencies.modelClient;
        toolRegistry = dependencies.toolRegistry;
        toolExecutor = dependencies.toolExecutor;
        toolExecutionCoordinator = dependencies.toolExecutionCoordinator;
        systemPromptProvider = dependencies.systemPromptProvider;
        storagePermissionManager = dependencies.storagePermissionManager;
        safPathResolver = dependencies.safPathResolver;
        mainThread = dependencies.mainThreadDispatcher;
        backgroundTasks = dependencies.backgroundTaskRunner;
        todoStateStore = dependencies.todoStateStore;
        storageStatsRepository = dependencies.storageStatsRepository;
        keepAliveRepository = dependencies.keepAliveRepository;
        phoneControlController = dependencies.phoneControlController;
        errorLogController = dependencies.errorLogController;

        // === initControllers ===
        MainControllerInitializer.init(this, dependencies);

        // === initStartup ===
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        fileTreeInteractionController.addExpandedPath(projectState.path());
        loadCurrentConversation();
    }

    @Override
    public void attachView(MainContract.View view) {
        viewProxy.attach(view);
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        fileTreeInteractionController.addExpandedPath(projectState.path());
        render();
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        projectWorkspaceController.validateSelectedProjectAvailabilityOnStartup();
    }

    @Override
    public void detachView() {
        viewProxy.detach();
    }

    @Override
    public void destroy() {
        ipcProviderManager.removeStateListener(ipcProviderController);
        detachView();
        generationLifecycleController.cancelActiveGeneration();
        generationLifecycleController.stopKeepAlive();
        backgroundTasks.shutdownNow();
    }

    /**
     * Full generation teardown: cancel network, reject pending tool reviews, stop keep-alive.
     * <p>Call sites (only explicit stop / permanent exit — never mere background visibility):</p>
     * <ul>
     *   <li>User taps Stop ({@link #onStopGeneration()})</li>
     *   <li>Activity finishing via {@link cn.lineai.mvp.ActivityGenerationLifecyclePolicy}</li>
     *   <li>{@link #destroy()} path (uses cancelActiveGeneration + stopKeepAlive directly)</li>
     * </ul>
     * <p>幂等：即使当前不在生成，调用也是安全的。</p>
     */
    public void resetGenerationState() {
        if (chatInteractionController != null) {
            chatInteractionController.stopGeneration();
            return;
        }
        // 兜底：直接走最小化清理，避免引用未初始化的依赖
        generationLifecycleController.cancelActiveGeneration();
        chatSessionStore.setStreaming(false);
        chatSessionStore.invalidateActiveGeneration();
        generationLifecycleController.stopKeepAlive();
        if (viewProxy.isAttached()) {
            render();
        }
    }

    @Override
    public void onMenuClick() {
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        viewProxy.showDrawer();
    }

    @Override
    public void onScreenBack() {
        navigateScreenBack("");
    }

    @Override
    public void onScreenBackFrom(String screenId) {
        navigateScreenBack(screenId);
    }

    private void navigateScreenBack(String visibleScreenId) {
        screenNavigationController.backFrom(visibleScreenId, navigationHost);
    }

    // ===== Merged from MainCoordinatorDelegates =====

    @Override
    public void onSettingsItemSelected(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        showScreen(id);
    }

    @Override
    public void onOpenUrl(String url) {
        String safeUrl = UrlPolicy.normalizeHttpOrHttpsUrl(url);
        if (safeUrl.length() == 0) {
            return;
        }
        if (OutputSettings.BROWSER_EXTERNAL.equals(settingsManagementController.getOutputSettings().getBrowserMode())) {
            viewProxy.openExternalUrl(safeUrl);
            return;
        }
        showScreen("browser:" + safeUrl);
    }

    @Override
    public void showModelManagement() {
        showScreen("models");
    }

    @Override
    public void onProjectClick() {
        projectWorkspaceController.showProjectSheet();
    }

    @Override
    public void onPermissionClick() {
        permissionModeController.showPermissionSheet();
    }

    @Override
    public void onNewConversation() {
        chatInteractionController.newConversation();
    }

    @Override
    public void onConversationSelected(String id) {
        chatInteractionController.selectConversation(id);
    }

    @Override
    public void onConversationDeleted(String id) {
        chatInteractionController.deleteConversation(id);
    }

    @Override
    public void onCurrentProjectRemoveRequested() {
        projectWorkspaceController.removeCurrentProject();
    }

    @Override
    public void onFileNodeSelected(String path, boolean directory) {
        fileTreeInteractionController.handleNodeSelected(path, directory);
    }

    @Override
    public void onFileNodeLongPressed(String path, String name, boolean directory, boolean root) {
        fileOperationController.showFileNodeActions(path, name, directory, root);
    }

    @Override
    public void onFileTreeActivated() {
        fileTreeInteractionController.activate();
    }

    @Override
    public void onFileTreeRefresh() {
        fileTreeInteractionController.refresh();
    }

    @Override
    public void onDirectoryPickerNodeSelected(String path) {
        directoryPickerController.onNodeSelected(path);
    }

    @Override
    public void onDirectoryPickerConfirmed() {
        directoryPickerController.onConfirmed();
    }

    @Override
    public void onDirectoryPickerCancelled() {
        directoryPickerController.onCancelled();
    }

    @Override
    public void onDialogInputSubmitted(String actionId, String value) {
        overlayActionController.handleDialogInput(actionId, value);
    }

    @Override
    public void onDialogConfirmed(String actionId) {
        overlayActionController.handleDialogConfirmed(actionId);
    }

    @Override
    public void onMoreClick() {
        overlayActionController.showMoreActions();
    }

    @Override
    public void onSendMessage(String text) {
        chatInteractionController.sendMessage(text);
    }

    @Override
    public void onSendMessage(String text, List<InputAttachment> attachments) {
        chatInteractionController.sendMessage(text, attachments);
    }

    @Override
    public void onSendMessageWithImage(String text, List<InputAttachment> attachments,
                                       String imageBase64, String imageMimeType, String imageName) {
        chatInteractionController.sendMessageWithImage(text, attachments,
                imageBase64, imageMimeType, imageName);
    }

    @Override
    public void onRecallMessage(String messageId) {
        chatInteractionController.recallMessage(messageId);
    }

    @Override
    public void onAttachmentPickerRequested() {
        attachmentPickerController.onAttachmentPickerRequested();
    }

    @Override
    public void onImagePickerRequested() {
        viewProxy.openImagePicker();
    }

    @Override
    public void onAttachmentPickerNodeSelected(String path, boolean directory) {
        attachmentPickerController.onAttachmentPickerNodeSelected(path, directory);
    }

    @Override
    public void onAttachmentPickerCancelled() {
        attachmentPickerController.onAttachmentPickerCancelled();
    }

    @Override
    public void onChatModeChanged(String mode) {
        chatInteractionController.changeChatMode(mode);
    }

    @Override
    public void onStopGeneration() {
        chatInteractionController.stopGeneration();
    }

    @Override
    public void onToolReview(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        if (generationFlowController.isPendingToolReview(toolCallId)) {
            generationFlowController.handleToolReview(state);
            return;
        }
        if (generationFlowController.isPendingAgentToolReview(toolCallId)) {
            generationFlowController.acceptAgentToolReview(toolCallId, state);
            return;
        }
        if (generationFlowController.handleAgentToolReview(toolCallId, state)) {
            return;
        }
        toolReviewController.review(toolCallId, state, diffId);
    }

    @Override
    public void onSheetOptionSelected(String id) {
        overlayActionController.handleSheetOption(id);
    }

    @Override
    public List<ConversationUiModel> getConversationMetas() {
        List<ConversationRecord> records = conversationRepository.getConversationMetas();
        ArrayList<ConversationUiModel> models = new ArrayList<>();
        if (records != null) {
            for (ConversationRecord r : records) {
                models.add(new ConversationUiModel(r.getId(), r.getTitle(), r.getUpdatedAt()));
            }
        }
        return models;
    }

    @Override
    public String getCurrentConversationId() {
        return chatSessionStore.getCurrentConversationId();
    }

    @Override
    public FileTreeNode getFileTree() {
        return fileTreeInteractionController.getFileTree();
    }

    @Override
    public boolean canRemoveCurrentProject() {
        return projectWorkspaceController.canRemoveCurrentProject();
    }

    @Override
    public void onModelQuickSwitch(String modelId) {
        modelInteractionController.quickSwitch(modelId);
    }

    @Override
    public void onModelTest(ModelConfig model) {
        modelInteractionController.testModel(model);
    }

    @Override
    public void onExternalProjectTreePicked(String treeUri) {
        projectWorkspaceController.onExternalProjectTreePicked(treeUri);
    }

    @Override
    public void onExternalProjectPickerCancelled() {
        projectWorkspaceController.onExternalProjectPickerCancelled();
    }

    @Override
    public void onStoragePermissionResult() {
        projectWorkspaceController.onStoragePermissionResult();
    }

    @Override
    public AiBehaviorSettings getAiBehaviorSettings() {
        return settingsManagementController.getAiBehaviorSettings();
    }

    @Override
    public void onAiToneModeChanged(String toneMode) {
        settingsManagementController.setAiToneMode(toneMode);
    }

    @Override
    public void onAiReasoningEffortChanged(String effort) {
        settingsManagementController.setAiReasoningEffort(effort);
    }

    @Override
    public void onAiThinkingScrollChanged(boolean enabled) {
        settingsManagementController.setAiThinkingScrollEnabled(enabled);
    }

    @Override
    public void onAiThinkingAutoExpandChanged(boolean enabled) {
        settingsManagementController.setAiThinkingAutoExpandEnabled(enabled);
    }

    @Override
    public void onAiPreserveReasoningChanged(boolean enabled) {
        settingsManagementController.setAiPreserveReasoningEnabled(enabled);
    }

    @Override
    public void onAiLearningModeChanged(boolean enabled) {
        settingsManagementController.setAiLearningModeEnabled(enabled);
    }

    @Override
    public InputSettings getInputSettings() {
        return settingsManagementController.getInputSettings();
    }

    @Override
    public void onEnterKeyBehaviorChanged(String behavior) {
        settingsManagementController.setEnterKeyBehavior(behavior);
    }

    @Override
    public List<PromptTemplateItem> getPromptTemplates() {
        return settingsManagementController.getPromptTemplates();
    }

    @Override
    public void onPromptTemplateSaved(String id, String value) {
        settingsManagementController.savePromptTemplate(id, value);
    }

    @Override
    public void onPromptTemplateReset(String id) {
        settingsManagementController.resetPromptTemplate(id);
    }

    @Override
    public MemoryOverviewState getMemoryOverview() {
        return settingsManagementController.getMemoryOverview();
    }

    @Override
    public void onMemorySaved(String id, String scope, String content) {
        settingsManagementController.saveMemory(id, scope, content);
    }

    @Override
    public void onMemoryDeleted(String id) {
        settingsManagementController.deleteMemory(id);
    }

    @Override
    public void onMemoriesDeleted(java.util.List<String> ids) {
        settingsManagementController.deleteMemories(ids);
    }

    @Override
    public OutputSettings getOutputSettings() {
        return settingsManagementController.getOutputSettings();
    }

    @Override
    public void onCodeWrapChanged(boolean enabled) {
        settingsManagementController.setCodeWrapEnabled(enabled);
    }

    @Override
    public void onBrowserModeChanged(String mode) {
        settingsManagementController.setBrowserMode(mode);
    }

    @Override
    public void onBrowserJavaScriptChanged(boolean enabled) {
        settingsManagementController.setBrowserJavaScriptEnabled(enabled);
    }

    @Override
    public void onAllowAnyHttpChanged(boolean enabled) {
        settingsManagementController.setAllowAnyHttp(enabled);
    }

    @Override
    public void onBypassPathProtectionChanged(boolean enabled) {
        settingsManagementController.setBypassPathProtection(enabled);
    }

    @Override
    public ThemeSettingsState getThemeSettings() {
        return settingsManagementController.getThemeSettings();
    }

    @Override
    public void onThemeModeChanged(String mode) {
        settingsManagementController.setThemeMode(mode);
    }

    @Override
    public void onCustomThemeColorsSaved(Map<String, String> colors) {
        settingsManagementController.saveCustomThemeColors(colors);
    }

    @Override
    public McpSettingsState getMcpSettingsState() {
        return settingsManagementController.getMcpSettingsState();
    }

    @Override
    public void onMcpExecutionModeChanged(String mode) {
        settingsManagementController.setMcpExecutionMode(mode);
    }

    @Override
    public void onMcpToolGroupChanged(String id, boolean enabled) {
        settingsManagementController.setMcpToolGroupEnabled(id, enabled);
    }

    @Override
    public void onMcpWebSearchConfigChanged(WebSearchConfig config) {
        settingsManagementController.setMcpWebSearchConfig(config);
    }

    @Override
    public String getImageUnderstandingModelId() {
        return settingsManagementController.getImageUnderstandingModelId();
    }

    @Override
    public void onImageUnderstandingModelSelected(String id) {
        settingsManagementController.setImageUnderstandingModelId(id);
    }

    @Override
    public String getImageGenerationModelId() {
        return settingsManagementController.getImageGenerationModelId();
    }

    @Override
    public void onImageGenerationModelSelected(String id) {
        settingsManagementController.setImageGenerationModelId(id);
    }

    @Override
    public void onLineCodeExportRequested() {
        lineCodeArchiveController.requestExport();
    }

    @Override
    public void onLineCodeExportTargetPicked(String uri, String displayName) {
        lineCodeArchiveController.exportTargetPicked(uri);
    }

    @Override
    public void onLineCodeExportCancelled() {
        lineCodeArchiveController.exportCancelled();
    }

    @Override
    public void onLineCodeImportRequested() {
        lineCodeArchiveController.requestImport();
    }

    @Override
    public void onLineCodeImportPicked(String uri, String displayName) {
        lineCodeArchiveController.importPicked(uri, displayName);
    }

    @Override
    public void onLineCodeImportCancelled() {
        lineCodeArchiveController.importCancelled();
    }

    @Override
    public ExtensionOverviewState getExtensionOverview() {
        return extensionManagementController.getOverview();
    }

    @Override
    public void onAgentExtensionSaved(ExtensionAgentConfig config) {
        extensionManagementController.saveAgentExtension(config);
    }

    @Override
    public ExtensionAgentConfig onAgentDraftGenerated(String description) throws Exception {
        return extensionDraftController.generateAgentDraft(description);
    }

    @Override
    public List<BaseTool> getExtensionAvailableTools() {
        return extensionDraftController.getAvailableTools();
    }

    @Override
    public void onMcpExtensionSaved(ExtensionMcpConfig config) {
        extensionManagementController.saveMcpExtension(config);
    }

    @Override
    public List<McpToolSummary> onMcpToolsQuery(String url, List<McpRequestHeader> headers) throws Exception {
        return extensionManagementController.queryMcpTools(url, headers);
    }

    @Override
    public SkillRecord onSkillCreated(String location, String name, String description, String content) {
        return extensionManagementController.createSkill(location, name, description, content);
    }

    @Override
    public SkillRecord onSkillInstalled(String location, String sourcePath, String name) throws Exception {
        return extensionManagementController.installSkill(location, sourcePath, name);
    }

    @Override
    public SkillRecord onSkillInstalledFromUri(String location, String uri, String displayName) throws Exception {
        return extensionManagementController.installSkillFromUri(location, uri, displayName);
    }

    @Override
    public SkillRecord onSkillInstalledFromGitHub(String location, String githubUrl) throws Exception {
        return extensionManagementController.installSkillFromGitHub(location, githubUrl);
    }

    @Override
    public void onExtensionEnabledChanged(String kind, String id, boolean enabled) {
        extensionManagementController.setExtensionEnabled(kind, id, enabled);
    }

    @Override
    public void onExtensionDeleted(String kind, String id) {
        extensionManagementController.deleteExtension(kind, id);
    }

    @Override
    public void onExtensionsDeleted(String kind, List<String> ids) {
        extensionManagementController.deleteExtensions(kind, ids);
    }

    @Override
    public List<ScannedProvider> onTerminalProviderScan() {
        return ipcProviderController.onTerminalProviderScan();
    }

    @Override
    public List<ScannedProvider> getTerminalProviderScanResults() {
        return ipcProviderController.getTerminalProviderScanResults();
    }

    @Override
    public boolean hasTerminalProviderScanned() {
        return ipcProviderController.hasTerminalProviderScanned();
    }

    @Override
    public void onTerminalProviderSaved(IpcProviderConfig config) {
        ipcProviderController.onTerminalProviderSaved(config);
    }

    @Override
    public void onTerminalProviderEnabledChanged(String id, boolean enabled) {
        ipcProviderController.onTerminalProviderEnabledChanged(id, enabled);
    }

    @Override
    public void onTerminalProviderDeleted(String id) {
        ipcProviderController.onTerminalProviderDeleted(id);
    }

    @Override
    public List<ModelConfig> getModels() {
        return modelManagementController.getModels();
    }

    @Override
    public ModelConfig getModel(String id) {
        return modelManagementController.getModel(id);
    }

    @Override
    public String getSelectedModelId() {
        return modelManagementController.getSelectedModelId();
    }

    @Override
    public void onModelSelected(String id) {
        modelManagementController.selectModel(id);
    }

    @Override
    public void onModelSaved(ModelConfig model) {
        modelManagementController.saveModel(model);
    }

    @Override
    public void onModelsDeleted(List<String> ids) {
        modelManagementController.deleteModels(ids);
    }

    @Override
    public void onPhoneControlPermissionEnabledChanged(String permissionId, boolean enabled) {
        if (permissionId == null || permissionId.length() == 0) {
            return;
        }
        toolRegistry.reloadExtensions();
        refreshVisibleScreen("phoneControl");
        render();
    }

    @Override
    public void onResume(String currentScreenId) {
        if ("phoneControl".equals(currentScreenId)) {
            refreshVisibleScreen("phoneControl");
            render();
        }
    }

    @Override
    public void onEnterBackground() {
        viewProxy.hideOverlays();
        persistCurrentConversation();
    }

    void reloadAfterLineCodeImport() {
        toolRegistry.reloadExtensions();
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        sshFileTreeController.invalidateFileTree();
        ipcFileTreeController.invalidateFileTree();
        requestSshFileTreeLoad(true);
        requestIpcFileTreeLoad(true);
        ConversationRecord current = conversationRepository.getCurrentConversation();
        if (current == null) {
            chatSessionStore.clearCurrentConversation();
        } else {
            applyConversation(current);
        }
        refreshVisibleScreen("data");
        render();
    }

    void showScreen(String screenId) {
        screenNavigationController.showScreen(screenId, navigationHost);
    }

    String projectPath() {
        return projectState.path();
    }

    String projectLabel() {
        return projectState.label();
    }

    boolean isViewAttached() {
        return viewProxy.isAttached();
    }

    void viewHideOverlays() {
        viewProxy.hideOverlays();
    }

    void viewShowScreen(String screenId) {
        viewProxy.showScreen(screenId);
    }

    void viewShowChatScreen() {
        viewProxy.showChatScreen();
    }

    void refreshVisibleScreen(String screenId) {
        viewProxy.evictScreen(screenId);
        screenNavigationController.refreshVisibleScreen(screenId, navigationHost);
    }

    void returnToScreen(String screenId) {
        viewProxy.evictScreen(screenId);
        screenNavigationController.returnToScreen(screenId, navigationHost);
    }

    void applyProject(ProjectRecord project) {
        if (project == null) {
            return;
        }
        projectState.apply(project, projectRepository);
        fileTreeInteractionController.resetToProjectRoot();
        sshFileTreeController.invalidateFileTree();
    }

    void requestSshFileTreeLoad(boolean force) {
        sshFileTreeController.requestFileTreeLoad(force);
    }

    void requestIpcFileTreeLoad(boolean force) {
        ipcFileTreeController.requestFileTreeLoad(force);
    }

    boolean isSshExecutionMode() {
        return projectState.isSshExecutionMode(toolSettingsRepository);
    }

    boolean isTerminalProviderExecutionMode() {
        return projectState.isTerminalProviderExecutionMode(toolSettingsRepository);
    }

    boolean isTermuxSshHost() {
        SshConfig config = sshService.getConfig();
        String host = config == null ? "" : config.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    String basename(String path) {
        return projectState.basename(path);
    }

    String parentPath(String path) {
        return projectState.parentPath(path);
    }

    void showNotice(String text) {
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
        render();
    }

    String syncModePermission() {
        String mode = chatModeRepository.getMode();
        chatModeRepository.applyMode(mode);
        chatModeRepository.applyPermissionForMode(mode, toolSettingsRepository);
        return chatModeRepository.getMode();
    }

    GenerationLifecycleController generationLifecycleController() {
        return generationLifecycleController;
    }

    ViewProxy viewProxy() {
        return viewProxy;
    }

    Context context() {
        return context;
    }

    ChatSessionStore chatSessionStore() {
        return chatSessionStore;
    }

    ProjectStore projectRepository() {
        return projectRepository;
    }

    ProjectRuntimeState projectState() {
        return projectState;
    }

    StoragePermissionManager storagePermissionManager() {
        return storagePermissionManager;
    }

    void render() {
        if (!viewProxy.isAttached()) {
            return;
        }
        String activeChatMode = syncModePermission();
        viewProxy.render(chatUiStateAssembler.assemble(
                projectState.label(),
                projectState.source(),
                projectState.path(),
                chatSessionStore.getCurrentConversationId(),
                activeChatMode,
                chatSessionStore.isStreaming(),
                messages
        ));
    }

    void resetTodoState() {
        if (todoStateStore != null) {
            todoStateStore.clear();
        }
    }

    void refreshFileTreeAfterRevert(String filePath) {
        fileTreeInteractionController.refreshAfterRevert(filePath);
    }

    private void loadCurrentConversation() {
        conversationPersistenceController.loadCurrentConversation();
    }

    void loadConversation(String id) {
        conversationPersistenceController.loadConversation(id);
        chatInteractionController.resetModelTracking();
    }

    private void applyConversation(ConversationRecord conversation) {
        conversationPersistenceController.applyConversation(conversation);
    }

    void ensureCurrentConversation() {
        conversationPersistenceController.ensureCurrentConversation();
    }

    void persistCurrentConversation() {
        conversationPersistenceController.persistCurrentConversation();
    }

    String nextId() {
        return chatSessionStore.nextMessageId();
    }

    @Override
    public void onClearDiffCache() {
        storageMaintenanceController.clearDiffCache();
    }

    @Override
    public void onClearChatHistory() {
        storageMaintenanceController.clearChatHistory();
    }

    @Override
    public void onKeepAliveSettingsChanged() {
        storageMaintenanceController.applyKeepAliveSettings();
    }

    // ===== Phone control =====

    @Override
    public boolean isPhoneControlAccessibilityEnabled() {
        return phoneControlController.isAccessibilityEnabled();
    }

    @Override
    public boolean isPhoneControlDisclaimerAccepted() {
        return phoneControlController.isDisclaimerAccepted();
    }

    @Override
    public boolean isPhoneControlPermissionEnabled(String permissionId) {
        return phoneControlController.isPermissionEnabled(permissionId);
    }

    @Override
    public void onPhoneControlSetPermissionEnabled(String permissionId, boolean enabled) {
        phoneControlController.setPermissionEnabled(permissionId, enabled);
    }

    @Override
    public void onPhoneControlAcceptDisclaimer() {
        phoneControlController.setDisclaimerAccepted(true);
    }

    // ===== Error logs =====

    @Override
    public List<ErrorLogEntry> getErrorLogs() {
        return errorLogController.list();
    }

    @Override
    public void clearErrorLogs() {
        errorLogController.clear();
    }

    // ===== Storage stats =====

    @Override
    public StorageStatsUiModel getStorageStats() {
        StorageStatsRepository.StorageStats stats = storageStatsRepository.getStats();
        StorageStatsUiModel ui = new StorageStatsUiModel();
        ui.totalSize = stats.totalSize;
        ui.totalCount = stats.totalCount;
        ui.diffCacheSize = stats.diffCacheSize;
        ui.diffCacheCount = stats.diffCacheCount;
        ui.chatSize = stats.chatSize;
        ui.chatCount = stats.chatCount;
        ui.configSize = stats.configSize;
        ui.configCount = stats.configCount;
        ui.homeSize = stats.homeSize;
        ui.homeCount = stats.homeCount;
        return ui;
    }

    // ===== Keep alive =====

    @Override
    public KeepAliveSettings getKeepAliveSettings() {
        KeepAliveRepository.KeepAliveSettings s = keepAliveRepository.getSettings();
        return new KeepAliveSettings(s.wakeLockEnabled, s.foregroundEnabled, s.fakeAudioEnabled);
    }

    @Override
    public void setKeepAliveWakeLockEnabled(boolean enabled) {
        keepAliveRepository.setWakeLockEnabled(enabled);
    }

    @Override
    public void setKeepAliveForegroundEnabled(boolean enabled) {
        keepAliveRepository.setForegroundEnabled(enabled);
    }

    @Override
    public void setKeepAliveFakeAudioEnabled(boolean enabled) {
        keepAliveRepository.setFakeAudioEnabled(enabled);
    }

    @Override
    public void updateKeepAliveService() {
        KeepAliveRepository.KeepAliveSettings settings = keepAliveRepository.getSettings();
        if (settings.wakeLockEnabled || settings.foregroundEnabled || settings.fakeAudioEnabled) {
            KeepAliveService.start(context, settings.wakeLockEnabled, settings.foregroundEnabled, settings.fakeAudioEnabled);
        } else {
            KeepAliveService.stop(context);
        }
    }

    @Override
    public void updateKeepAliveServiceStatus(String status) {
        KeepAliveService.updateStatus(context, status);
    }

    // ===== SSH =====

    @Override
    public SshConfig getSshConfig() {
        return sshService.getConfig();
    }

    @Override
    public void saveSshConfig(SshConfig config) {
        sshService.saveConfig(config);
    }

    @Override
    public String testSshConnection(SshConfig config) throws Exception {
        return sshService.testConnection(config);
    }

    // ===== Termux =====

    @Override
    public void openTermux() throws Exception {
        sshService.openTermux();
    }

    @Override
    public TermuxHelper.TermuxSetupResult setupTermuxSsh(int timeoutMs) throws Exception {
        return sshService.setupTermuxOpenSsh(timeoutMs);
    }

    @Override
    public int queryModelCount(String baseUrl) throws Exception {
        String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "models";
        String body = cn.lineai.security.SimpleHttpClient.get(url, 8000, 8000);
        if (body == null || body.length() == 0) {
            return 0;
        }
        org.json.JSONArray data = new org.json.JSONObject(body).getJSONArray("data");
        return data.length();
    }

}
