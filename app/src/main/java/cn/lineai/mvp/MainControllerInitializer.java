package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;

import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.IpcFileTreeStore;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.service.LearningContextService;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import cn.lineai.model.SheetOption;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.ai.prompt.SystemPromptProvider;
import java.util.ArrayList;

final class MainControllerInitializer {

    private MainControllerInitializer() {
    }

    static void init(MainCoordinator coordinator, MainDependencies dependencies) {
        Context context = coordinator.context();
        ChatSessionStore chatSessionStore = coordinator.chatSessionStore();
        ArrayList<ChatMessage> messages = chatSessionStore.mutableMessages();
        ProjectStore projectRepository = coordinator.projectRepository();
        ToolSettingsStore toolSettingsRepository = dependencies.toolSettingsRepository;
        ChatModeRepository chatModeRepository = dependencies.chatModeRepository;
        ModelStore modelRepository = dependencies.modelRepository;
        AiBehaviorSettingsRepository aiBehaviorSettingsRepository = dependencies.aiBehaviorSettingsRepository;
        InputSettingsRepository inputSettingsRepository = dependencies.inputSettingsRepository;
        PromptTemplateRepository promptTemplateRepository = dependencies.promptTemplateRepository;
        LearningContextStore learningContextRepository = dependencies.learningContextRepository;
        LearningContextService learningContextService = dependencies.learningContextService;
        OutputSettingsRepository outputSettingsRepository = dependencies.outputSettingsRepository;
        ThemeSettingsRepository themeSettingsRepository = dependencies.themeSettingsRepository;
        ExtensionStore extensionRepository = dependencies.extensionRepository;
        IpcProviderStore ipcProviderRepository = dependencies.ipcProviderRepository;
        DiffStore diffRepository = dependencies.diffRepository;
        FileTreeStore fileTreeRepository = dependencies.fileTreeRepository;
        IpcFileTreeStore ipcFileTreeRepository = dependencies.ipcFileTreeRepository;
        SshFileTreeStore sshFileTreeRepository = dependencies.sshFileTreeRepository;
        ContextManager contextManager = dependencies.contextManager;
        ContextCompactionService contextCompactionService = dependencies.contextCompactionService;
        cn.lineai.ai.ModelClient modelClient = dependencies.modelClient;
        ToolRegistry toolRegistry = dependencies.toolRegistry;
        ToolExecutor toolExecutor = dependencies.toolExecutor;
        ToolExecutionCoordinator toolExecutionCoordinator = dependencies.toolExecutionCoordinator;
        SystemPromptProvider systemPromptProvider = dependencies.systemPromptProvider;
        MainThreadDispatcher mainThread = dependencies.mainThreadDispatcher;
        BackgroundTaskRunner backgroundTasks = dependencies.backgroundTaskRunner;
        cn.lineai.state.TodoStateStore todoStateStore = dependencies.todoStateStore;

        coordinator.modelManagementController = new ModelManagementController(
                modelRepository,
                new ModelManagementController.Host() {
                    @Override
                    public void refreshModelsScreen() {
                        coordinator.refreshVisibleScreen("models");
                    }

                    @Override
                    public void returnToModelsScreen() {
                        coordinator.returnToScreen("models");
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.generationLifecycleController = new GenerationLifecycleController(context, messages);
        coordinator.sshFileTreeController = new SshFileTreeController(
                sshFileTreeRepository,
                new SshFileTreeHost(coordinator),
                backgroundTasks::execute,
                mainThread::post
        );
        coordinator.ipcFileTreeController = new IpcFileTreeController(
                ipcFileTreeRepository,
                new IpcFileTreeHost(coordinator),
                backgroundTasks::execute,
                mainThread::post
        );
        coordinator.fileTreeInteractionController = new FileTreeInteractionController(
                fileTreeRepository,
                coordinator.sshFileTreeController,
                coordinator.ipcFileTreeController,
                new FileTreeInteractionController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return coordinator.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return coordinator.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return coordinator.projectPath();
                    }

                    @Override
                    public String parentPath(String path) {
                        return coordinator.parentPath(path);
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.fileOperationController = new FileOperationController(
                fileTreeRepository,
                sshFileTreeRepository,
                ipcFileTreeRepository,
                new FileOperationHost(coordinator),
                backgroundTasks::execute,
                mainThread::post
        );
        coordinator.permissionModeController = new PermissionModeController(
                toolSettingsRepository,
                chatModeRepository,
                context,
                new PermissionModeController.Host() {
                    @Override
                    public boolean hasExternalStorageAccess() {
                        return coordinator.storagePermissionManager().hasExternalStorageAccess();
                    }

                    @Override
                    public String storagePermissionMessage() {
                        return coordinator.storagePermissionManager().permissionDeniedMessage();
                    }

                    @Override
                    public void showPermissionSheet(ArrayList<SheetOption> options) {
                        coordinator.viewProxy().showSheet(context.getString(R.string.sheet_title_permissions), options);
                    }
                }
        );
        coordinator.projectWorkspaceController = new ProjectWorkspaceController(
                context,
                projectRepository,
                toolSettingsRepository,
                sshFileTreeRepository,
                coordinator.storagePermissionManager(),
                dependencies.safPathResolver,
                new ProjectWorkspaceHost(coordinator),
                backgroundTasks::execute,
                mainThread::post
        );
        coordinator.settingsManagementController = new SettingsManagementController(
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                promptTemplateRepository,
                learningContextRepository,
                learningContextService,
                outputSettingsRepository,
                themeSettingsRepository,
                toolSettingsRepository,
                new SettingsManagementController.Host() {
                    @Override
                    public String currentProjectPath() {
                        return coordinator.projectState().path();
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }

                    @Override
                    public void recreateForTheme(String screenId) {
                        coordinator.viewProxy().recreateForTheme(screenId);
                    }

                    @Override
                    public void afterMcpExecutionModeChanged(String executionMode) {
                        coordinator.applyProject(projectRepository.ensureSelectedProjectPath(executionMode));
                        coordinator.sshFileTreeController.invalidateFileTree();
                        coordinator.ipcFileTreeController.invalidateFileTree();
                        coordinator.requestSshFileTreeLoad(true);
                        coordinator.requestIpcFileTreeLoad(true);
                        coordinator.refreshVisibleScreen("mcp");
                        coordinator.render();
                    }

                    @Override
                    public void refreshMcpScreen() {
                        coordinator.refreshVisibleScreen("mcp");
                    }

                    @Override
                    public void returnToToolSettings() {
                        coordinator.returnToScreen("toolSettings");
                    }
                }
        );
        coordinator.lineCodeArchiveController = new LineCodeArchiveController(
                dependencies.lineCodeArchiveService,
                new LineCodeArchiveController.Host() {
                    @Override
                    public void openExportPicker(String fileName) {
                        coordinator.viewProxy().openLineCodeExportPicker(fileName);
                    }

                    @Override
                    public void persistBeforeExport() {
                        coordinator.persistCurrentConversation();
                    }

                    @Override
                    public void openImportPicker() {
                        coordinator.viewProxy().openLineCodeImportPicker();
                    }

                    @Override
                    public void showImportConfirmation(String sourceName) {
                        coordinator.viewProxy().showConfirmationDialog(
                                "\u8986\u76d6\u5bfc\u5165 .linecode",
                                "\u5c06\u4ece\u300c" + sourceName + "\u300d\u6062\u590d\u6570\u636e\u5e93\u3001\u804a\u5929\u8bb0\u5f55\u3001\u914d\u7f6e\u548c .linecode \u5de5\u4f5c\u533a\u6587\u4ef6\u3002\u5f53\u524d\u672c\u673a\u6570\u636e\u4f1a\u88ab\u8986\u76d6\u3002",
                                context.getString(R.string.common_confirm),
                                true,
                                "data:import_linecode"
                        );
                    }

                    @Override
                    public void beforeImport() {
                        coordinator.generationLifecycleController.cancelActiveGeneration();
                        chatSessionStore.setStreaming(false);
                        coordinator.generationLifecycleController.stopKeepAlive();
                    }

                    @Override
                    public void afterImport() {
                        coordinator.reloadAfterLineCodeImport();
                    }

                    @Override
                    public void showNotice(String text) {
                        coordinator.showNotice(text);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        coordinator.chatUiStateAssembler = new ChatUiStateAssembler(
                modelRepository,
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                outputSettingsRepository,
                contextManager
        );
        coordinator.modelInteractionController = new ModelInteractionController(
                context,
                modelRepository,
                modelClient,
                backgroundTasks,
                mainThread,
                new ModelInteractionController.Host() {
                    @Override
                    public boolean isStreaming() {
                        return chatSessionStore.isStreaming();
                    }

                    @Override
                    public boolean isViewAttached() {
                        return coordinator.viewProxy().isAttached();
                    }

                    @Override
                    public void showTestResult(String message) {
                        coordinator.viewProxy().showConfirmationDialog(
                                context.getString(R.string.screen_model_add_test_result_title),
                                message,
                                context.getString(R.string.screen_model_add_test_result_confirm),
                                false,
                                "modelTestResult"
                        );
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.toolMessageController = new ToolMessageController(messages, coordinator::nextId, toolRegistry);
        coordinator.toolReviewController = new ToolReviewController(
                diffRepository,
                coordinator.toolMessageController,
                backgroundTasks,
                mainThread,
                new ToolReviewController.Host() {
                    @Override
                    public void refreshFileTreeAfterRevert(String filePath) {
                        coordinator.refreshFileTreeAfterRevert(filePath);
                    }

                    @Override
                    public void persistCurrentConversation() {
                        coordinator.persistCurrentConversation();
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.conversationPersistenceController = new ConversationPersistenceController(
                context,
                chatSessionStore,
                messages,
                dependencies.conversationRepository,
                aiBehaviorSettingsRepository,
                learningContextRepository,
                new ConversationPersistenceController.Host() {
                    @Override
                    public String projectPath() {
                        return coordinator.projectState().path();
                    }

                    @Override
                    public String defaultConversationTitle(Context ctx) {
                        return ctx.getString(R.string.drawer_new_conversation);
                    }

                    @Override
                    public String interruptedGenerationMessage(Context ctx) {
                        return ctx.getString(R.string.message_generation_interrupted);
                    }
                }
        );
        coordinator.extensionDraftController = new ExtensionDraftController(
                modelRepository,
                modelClient,
                toolRegistry,
                toolSettingsRepository,
                extensionRepository
        );
        coordinator.extensionManagementController = new ExtensionManagementController(
                extensionRepository,
                ipcProviderRepository,
                toolRegistry,
                new ExtensionManagementController.Host() {
                    @Override
                    public String projectPath() {
                        return coordinator.projectState().path();
                    }

                    @Override
                    public void returnToScreen(String screenId) {
                        coordinator.returnToScreen(screenId);
                    }

                    @Override
                    public void refreshVisibleScreen(String screenId) {
                        coordinator.refreshVisibleScreen(screenId);
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.modelPromptController = new ModelPromptController(
                messages,
                chatSessionStore,
                aiBehaviorSettingsRepository,
                chatModeRepository,
                promptTemplateRepository,
                learningContextService,
                contextManager,
                modelRepository,
                extensionRepository,
                systemPromptProvider,
                toolSettingsRepository,
                toolRegistry,
                todoStateStore,
                new ModelPromptController.Host() {
                    @Override
                    public String syncModePermission() {
                        return coordinator.syncModePermission();
                    }

                    @Override
                    public String projectPath() {
                        return coordinator.projectState().path();
                    }

                    @Override
                    public String projectSource() {
                        return coordinator.projectState().source();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return coordinator.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String interruptedGenerationMessage() {
                        return context.getString(R.string.message_generation_interrupted);
                    }
                }
        );
        coordinator.directoryPickerController = new DirectoryPickerController(
                fileTreeRepository,
                sshFileTreeRepository,
                backgroundTasks::execute,
                mainThread::post,
                new DirectoryPickerController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return coordinator.viewProxy().isAttached();
                    }

                    @Override
                    public String projectPath() {
                        return coordinator.projectState().path();
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return coordinator.isTermuxSshHost();
                    }

                    @Override
                    public void applySelectedProject(String path, boolean ssh) {
                        coordinator.projectWorkspaceController.applyDirectoryPickerProject(path, ssh);
                    }

                    @Override
                    public void hideDirectoryPicker() {
                        coordinator.viewProxy().hideDirectoryPicker();
                    }

                    @Override
                    public void showDirectoryPicker(
                            String title,
                            String subtitle,
                            cn.lineai.model.FileTreeNode tree,
                            String selectedPath,
                            boolean loading,
                            String message
                    ) {
                        coordinator.viewProxy().showDirectoryPicker(title, subtitle, tree, selectedPath, loading, message);
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.storageMaintenanceController = new StorageMaintenanceController(
                context,
                messages,
                chatSessionStore,
                dependencies.conversationRepository,
                dependencies.keepAliveRepository,
                dependencies.storageStatsRepository,
                new StorageMaintenanceController.Host() {
                    @Override
                    public void clearCurrentConversation() {
                        coordinator.generationFlowController.clearSessionAutoToolConfirmations();
                        coordinator.resetTodoState();
                    }

                    @Override
                    public void refreshStorageScreen() {
                        coordinator.refreshVisibleScreen("storage");
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.contextCompactionController = new ContextCompactionController(
                context,
                messages,
                chatSessionStore,
                modelRepository,
                aiBehaviorSettingsRepository,
                contextCompactionService,
                contextManager,
                dependencies.tokenUsageTracker,
                backgroundTasks,
                mainThread,
                new ContextCompactionHost(coordinator)
        );
        coordinator.ipcProviderController = new IpcProviderController(
                context,
                ipcProviderRepository,
                dependencies.ipcProviderScanner,
                dependencies.ipcProviderManager,
                new IpcProviderController.Host() {
                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return coordinator.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public void applyTerminalProviderProjectPath(String path, String label) {
                        coordinator.projectState().applyTerminalProviderPath(path, label);
                        coordinator.fileTreeInteractionController.resetToProjectRoot();
                    }

                    @Override
                    public void clearTerminalProviderProjectPath() {
                        coordinator.projectState().clearTerminalProviderPath();
                        coordinator.fileTreeInteractionController.clearExpandedPaths();
                    }

                    @Override
                    public void requestIpcFileTreeLoad(boolean force) {
                        coordinator.requestIpcFileTreeLoad(force);
                    }

                    @Override
                    public void refreshVisibleScreen(String screenId) {
                        coordinator.refreshVisibleScreen(screenId);
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }
                }
        );
        coordinator.agentExecutionController = new AgentExecutionController(
                modelClient,
                aiBehaviorSettingsRepository,
                (cn.lineai.data.repository.ToolSettingsRepository) toolSettingsRepository,
                toolExecutor,
                toolRegistry,
                (cn.lineai.data.repository.ExtensionRepository) extensionRepository,
                promptTemplateRepository
        );
        coordinator.generationFlowController = new GenerationFlowController(
                messages,
                chatSessionStore,
                modelClient,
                aiBehaviorSettingsRepository,
                extensionRepository,
                toolRegistry,
                toolExecutor,
                toolExecutionCoordinator,
                toolSettingsRepository,
                coordinator.toolMessageController,
                coordinator.modelPromptController,
                coordinator.generationController,
                coordinator.agentExecutionController,
                todoStateStore,
                mainThread,
                backgroundTasks,
                coordinator.contextCompactionController,
                dependencies.tokenUsageTracker,
                new GenerationFlowHost(coordinator)
        );
        coordinator.generationLifecycleController.setGenerationFlowController(coordinator.generationFlowController);
        java.util.function.BooleanSupplier bypassSupplier = () -> outputSettingsRepository.isPathProtectionBypassed();
        coordinator.agentExecutionController.setBypassPathProtectionSupplier(bypassSupplier);
        coordinator.generationFlowController.setBypassPathProtectionSupplier(bypassSupplier);
        coordinator.chatInteractionController = new ChatInteractionController(
                messages,
                chatSessionStore,
                dependencies.conversationRepository,
                modelRepository,
                chatModeRepository,
                toolSettingsRepository,
                coordinator.contextCompactionController,
                coordinator.generationFlowController,
                new ChatInteractionHost(coordinator)
        );
        coordinator.overlayActionController = new OverlayActionController(
                context,
                coordinator.projectWorkspaceController,
                coordinator.fileOperationController,
                coordinator.permissionModeController,
                coordinator.contextCompactionController,
                coordinator.chatInteractionController,
                coordinator.lineCodeArchiveController,
                new OverlayActionController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return coordinator.viewProxy().isAttached();
                    }

                    @Override
                    public void showSheet(String title, ArrayList<SheetOption> options) {
                        coordinator.viewProxy().showSheet(title, options);
                    }

                    @Override
                    public void hideOverlays() {
                        coordinator.viewHideOverlays();
                    }

                    @Override
                    public void showScreen(String screenId) {
                        coordinator.showScreen(screenId);
                    }

                    @Override
                    public void render() {
                        coordinator.render();
                    }

                    @Override
                    public void exportCurrentChat() {
                        coordinator.viewProxy().exportCurrentChat();
                    }

                    @Override
                    public void enterMessageSelectMode() {
                        coordinator.viewProxy().enterMessageSelectMode();
                    }
                }
        );
        coordinator.attachmentPickerController = new AttachmentPickerCoordinator(
                context,
                fileTreeRepository,
                sshFileTreeRepository,
                ipcFileTreeRepository,
                backgroundTasks::execute,
                mainThread::post,
                new AttachmentPickerCoordinator.Host() {
                    @Override
                    public boolean isStreaming() {
                        return chatSessionStore.isStreaming();
                    }

                    @Override
                    public boolean isSshExecutionMode() {
                        return coordinator.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return coordinator.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return coordinator.projectState().path();
                    }

                    @Override
                    public String defaultHomePath() {
                        return projectRepository.getDefaultHomePath();
                    }

                    @Override
                    public boolean isViewAttached() {
                        return coordinator.viewProxy().isAttached();
                    }

                    @Override
                    public void showAttachmentPicker(String title, cn.lineai.model.FileTreeNode tree, boolean loading, String message, String source) {
                        coordinator.viewProxy().showAttachmentPicker(title, tree, loading, message, source);
                    }
                }
        );
    }
}
