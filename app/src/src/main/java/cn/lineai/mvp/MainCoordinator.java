package cn.lineai.mvp;

import android.content.Context;
import android.os.SystemClock;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.ToolCallTextParser;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.ai.protocol.OpenAiCompatibleCapabilities;
import cn.lineai.context.ContextCompactionResult;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationRepository;
import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.data.repository.FileTreeRepository;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.LearningContextRepository;
import cn.lineai.data.repository.MemoryExtractionService;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectRepository;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeRepository;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatMode;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.InputSettings;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelRepository;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.model.SheetOption;
import cn.lineai.model.SkillRecord;
import cn.lineai.model.SshConfig;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.security.UrlPolicy;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainCoordinator implements MainUiController {
    private static final long STREAM_RENDER_INTERVAL_MS = 80L;
    private static final long AGENT_PROGRESS_RENDER_INTERVAL_MS = 100L;
    private static final int AGENT_MAX_TURNS = 8;
    private static final String DIRECTORY_PICKER_MODE_SSH_REMOTE = "ssh_remote";
    private static final String AGENT_TERMINATED_MESSAGE = "Agent 已终止。";
    private static final String SHELL_EXECUTE_TOOL = "shell_execute";
    private static final String TOOL_REVIEW_SESSION_AUTO = "session_auto";

    private final ChatSessionStore chatSessionStore = new ChatSessionStore();
    private final ArrayList<ChatMessage> messages = chatSessionStore.mutableMessages();
    private final ScreenNavigationController screenNavigationController = new ScreenNavigationController();
    private final Set<String> expandedFilePaths = new HashSet<>();
    private final Set<String> sessionAutoConfirmedTools = new HashSet<>();
    private final MainThreadDispatcher mainThread;
    private final BackgroundTaskRunner backgroundTasks;
    private final ChatUiStateAssembler chatUiStateAssembler;
    private final ToolRunController toolRunController;
    private final GenerationController generationController = new GenerationController();
    private final ModelManagementController modelManagementController;
    private final SettingsManagementController settingsManagementController;
    private final SshFileTreeController sshFileTreeController;
    private final FileOperationController fileOperationController;
    private final PermissionModeController permissionModeController;
    private final ProjectSheetController projectSheetController;
    private final ModelRepository modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ChatModeRepository chatModeRepository;
    private final InputSettingsRepository inputSettingsRepository;
    private final OutputSettingsRepository outputSettingsRepository;
    private final ThemeSettingsRepository themeSettingsRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;
    private final LearningContextRepository learningContextRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final ToolSettingsRepository toolSettingsRepository;
    private final ExtensionRepository extensionRepository;
    private final DiffRepository diffRepository;
    private final FileTreeRepository fileTreeRepository;
    private final SshService sshService;
    private final SshFileTreeRepository sshFileTreeRepository;
    private final ContextManager contextManager;
    private final ContextCompactionService contextCompactionService;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolExecutionCoordinator toolExecutionCoordinator;
    private final SystemPromptProvider systemPromptProvider;
    private final StoragePermissionManager storagePermissionManager;
    private final SafPathResolver safPathResolver;
    private final LineCodeArchiveController lineCodeArchiveController;
    private MainContract.View view;
    private final ScreenNavigationController.Host navigationHost = new ScreenNavigationController.Host() {
        @Override
        public void hideOverlays() {
            if (view != null) {
                view.hideOverlays();
            }
        }

        @Override
        public void showScreen(String screenId) {
            if (view != null) {
                view.showScreen(screenId);
            }
        }

        @Override
        public void showChatScreen() {
            if (view != null) {
                view.showChatScreen();
            }
        }
    };
    private ModelCancellationToken currentCancellationToken;
    private String lastMessageModelId = "";
    private final StringBuilder pendingStreamTextDelta = new StringBuilder();
    private final StringBuilder pendingStreamReasoningDelta = new StringBuilder();
    private final HashMap<String, StringBuilder> streamingRawTextByMessageId = new HashMap<>();
    private String pendingStreamAssistantId = "";
    private int pendingStreamGenerationId = -1;
    private boolean streamRenderScheduled;
    private long lastStreamRenderAt;
    private String projectLabel = "LineCode";
    private String projectPath = "";
    private String projectSource = WorkspacePaths.SOURCE_DEFAULT;
    private boolean pendingExternalProjectOpen;
    private PendingToolExecution pendingToolExecution;
    private String sessionAutoConfirmedConversationId = "";
    private final Set<String> directoryPickerExpandedPaths = new HashSet<>();
    private FileTreeNode directoryPickerTree;
    private String directoryPickerMode = "";
    private String directoryPickerSelectedPath = "";
    private String directoryPickerRootPath = "";
    private boolean directoryPickerLoading;
    private String directoryPickerMessage = "";
    private final Set<String> attachmentPickerExpandedPaths = new HashSet<>();
    private FileTreeNode attachmentPickerTree;
    private String attachmentPickerRootPath = "";
    private String attachmentPickerSource = "";
    private boolean attachmentPickerLoading;
    private String attachmentPickerMessage = "";
    private boolean attachmentPickerActive;
    private int attachmentPickerLoadGeneration;
    private boolean startupProjectAvailabilityChecked;

    private static final class ToolExecutionBatch {
        private final ArrayList<ToolResult> completedResults;
        private final ToolCall pendingCall;
        private final ArrayList<ToolCall> remainingCalls;

        ToolExecutionBatch(ArrayList<ToolResult> completedResults, ToolCall pendingCall, ArrayList<ToolCall> remainingCalls) {
            this.completedResults = completedResults == null ? new ArrayList<>() : completedResults;
            this.pendingCall = pendingCall;
            this.remainingCalls = remainingCalls == null ? new ArrayList<>() : remainingCalls;
        }
    }

    private static final class PendingToolExecution {
        private final int generationId;
        private final ModelConfig selectedModel;
        private final ToolCall toolCall;
        private final ArrayList<ToolCall> remainingCalls;
        private final int usedToolCallCount;
        private final String homePath;
        private final ModelCancellationToken cancellationToken;

        PendingToolExecution(
                int generationId,
                ModelConfig selectedModel,
                ToolCall toolCall,
                ArrayList<ToolCall> remainingCalls,
                int usedToolCallCount,
                String homePath,
                ModelCancellationToken cancellationToken
        ) {
            this.generationId = generationId;
            this.selectedModel = selectedModel;
            this.toolCall = toolCall;
            this.remainingCalls = remainingCalls == null ? new ArrayList<>() : new ArrayList<>(remainingCalls);
            this.usedToolCallCount = usedToolCallCount;
            this.homePath = homePath == null ? "" : homePath;
            this.cancellationToken = cancellationToken;
        }
    }

    private static final class AgentRunResult {
        private final String output;
        private final int toolCallCount;
        private final boolean error;

        AgentRunResult(String output, int toolCallCount, boolean error) {
            this.output = output == null ? "" : output;
            this.toolCallCount = toolCallCount;
            this.error = error;
        }
    }

    private interface AgentProgressMirror {
        void onAgentProgress(String payload, boolean error);
    }

    private static final class PipelineAgent {
        private final String id;
        private final String type;
        private final String description;
        private final String prompt;
        private final ArrayList<String> readScope;
        private final ArrayList<String> writeScope;
        private final ArrayList<String> dependencies;

        PipelineAgent(
                String id,
                String type,
                String description,
                String prompt,
                ArrayList<String> readScope,
                ArrayList<String> writeScope,
                ArrayList<String> dependencies
        ) {
            this.id = id == null ? "" : id;
            this.type = type == null ? "" : type;
            this.description = description == null ? "" : description;
            this.prompt = prompt == null ? "" : prompt;
            this.readScope = readScope == null ? new ArrayList<>() : readScope;
            this.writeScope = writeScope == null ? new ArrayList<>() : writeScope;
            this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
        }
    }

    private final class AgentProgressSession {
        private final int generationId;
        private final String toolCallId;
        private final String toolName;
        private final String type;
        private final String description;
        private final LinkedHashMap<String, ToolCall> displayToolCalls = new LinkedHashMap<>();
        private final LinkedHashMap<String, ToolResult> displayToolResults = new LinkedHashMap<>();
        private final HashMap<String, String> displayIdByOriginalId = new HashMap<>();
        private String output = "";
        private String thinking = "";
        private String status = "running";
        private String modelContent = "";
        private boolean error;
        private boolean renderScheduled;
        private long lastRenderAt;
        private final AgentProgressMirror mirror;

        AgentProgressSession(int generationId, String toolCallId, String toolName, String type, String description) {
            this(generationId, toolCallId, toolName, type, description, null);
        }

        AgentProgressSession(
                int generationId,
                String toolCallId,
                String toolName,
                String type,
                String description,
                AgentProgressMirror mirror
        ) {
            this.generationId = generationId;
            this.toolCallId = toolCallId == null ? "" : toolCallId;
            this.toolName = toolName == null ? "" : toolName;
            this.type = type == null ? "" : type;
            this.description = description == null ? "" : description;
            this.mirror = mirror;
        }

        synchronized boolean canRender() {
            return toolCallId.length() > 0;
        }

        synchronized boolean canMirror() {
            return mirror != null;
        }

        synchronized void beginTurn() {
            output = "";
            status = "running";
            error = false;
        }

        synchronized void appendText(String delta) {
            if (delta != null && delta.length() > 0) {
                output += delta;
                addToolCalls(ToolCallTextParser.parseStreamingPreview(output).getToolCalls());
            }
        }

        synchronized void appendThinking(String delta) {
            if (delta != null && delta.length() > 0) {
                thinking += delta;
            }
        }

        synchronized void setTurnResult(String nextOutput, String nextThinking) {
            output = nextOutput == null ? "" : nextOutput;
            if (nextThinking != null && nextThinking.trim().length() > 0) {
                thinking = nextThinking;
            }
        }

        synchronized void addToolCalls(List<ToolCall> calls) {
            if (calls == null) {
                return;
            }
            for (ToolCall call : calls) {
                if (call == null || call.getId().length() == 0 || call.getName().length() == 0) {
                    continue;
                }
                if (displayIdByOriginalId.containsKey(call.getId())) {
                    continue;
                }
                String displayId = toolCallId + "_agent_" + displayToolCalls.size();
                displayIdByOriginalId.put(call.getId(), displayId);
                displayToolCalls.put(displayId, new ToolCall(displayId, call.getName(), call.getArguments()));
            }
        }

        synchronized void putToolResult(ToolCall originalCall, ToolResult result) {
            if (originalCall == null || result == null) {
                return;
            }
            String displayId = displayIdByOriginalId.get(originalCall.getId());
            if (displayId == null || displayId.length() == 0) {
                addToolCalls(java.util.Collections.singletonList(originalCall));
                displayId = displayIdByOriginalId.get(originalCall.getId());
            }
            if (displayId == null || displayId.length() == 0) {
                return;
            }
            displayToolResults.put(displayId, result.withCall(displayId, originalCall.getName()));
        }

        synchronized void setFinished(String nextStatus, boolean nextError, String nextModelContent) {
            status = nextStatus == null || nextStatus.length() == 0 ? "done" : nextStatus;
            error = nextError;
            modelContent = nextModelContent == null ? "" : nextModelContent;
        }

        synchronized boolean shouldScheduleRender() {
            if ((!canRender() && !canMirror()) || renderScheduled) {
                return false;
            }
            renderScheduled = true;
            return true;
        }

        synchronized long renderDelayMs() {
            long now = SystemClock.uptimeMillis();
            return Math.max(0L, AGENT_PROGRESS_RENDER_INTERVAL_MS - (now - lastRenderAt));
        }

        synchronized ToolResult snapshotResult() {
            renderScheduled = false;
            lastRenderAt = SystemClock.uptimeMillis();
            return new ToolResult(toolCallId, toolName, payload(), error);
        }

        synchronized void notifyMirror() {
            if (mirror != null) {
                renderScheduled = false;
                lastRenderAt = SystemClock.uptimeMillis();
                mirror.onAgentProgress(payload(), error);
            }
        }

        private String payload() {
            try {
                JSONObject object = new JSONObject();
                object.put("linecode_agent_progress", true);
                object.put("kind", toolName);
                object.put("status", status);
                object.put("type", type);
                object.put("description", description);
                object.put("output", visibleOutput(output));
                object.put("thinking", thinking);
                object.put("tool_call_count", displayToolCalls.size());
                object.put("model_content", modelContent);
                JSONArray tools = new JSONArray();
                for (ToolCall call : displayToolCalls.values()) {
                    JSONObject item = new JSONObject()
                            .put("id", call.getId())
                            .put("name", call.getName())
                            .put("arguments", call.getArguments());
                    ToolResult result = displayToolResults.get(call.getId());
                    if (result != null) {
                        item.put("result", new JSONObject()
                                .put("content", result.getContent())
                                .put("is_error", result.isError())
                                .put("diff_id", result.getDiffId())
                                .put("review_state", result.getReviewState())
                                .put("review_message", result.getReviewMessage()));
                    }
                    tools.put(item);
                }
                object.put("tool_calls", tools);
                return object.toString();
            } catch (Exception e) {
                return modelContent.length() > 0 ? modelContent : visibleOutput(output);
            }
        }

        private String visibleOutput(String rawOutput) {
            ToolCallTextParser.Result parsed = ToolCallTextParser.parse(rawOutput);
            return parsed.hasToolMarkup() ? parsed.getText() : rawOutput == null ? "" : rawOutput;
        }
    }

    private final class PipelineProgressSession {
        private final ToolContext parentContext;
        private final ArrayList<PipelineAgent> agents;
        private final LinkedHashMap<String, PipelineAgentState> stateById = new LinkedHashMap<>();
        private String status = "running";
        private boolean error;

        PipelineProgressSession(ToolContext parentContext, ArrayList<PipelineAgent> agents) {
            this.parentContext = parentContext;
            this.agents = agents == null ? new ArrayList<>() : agents;
            for (PipelineAgent agent : this.agents) {
                stateById.put(agent.id, new PipelineAgentState(agent));
            }
        }

        void beginAgent(PipelineAgent agent) {
            PipelineAgentState state = stateById.get(agent.id);
            if (state != null) {
                state.status = "running";
            }
            publish(false);
        }

        void updateAgent(PipelineAgent agent, String agentProgressPayload, boolean agentError) {
            PipelineAgentState state = stateById.get(agent.id);
            if (state == null) {
                return;
            }
            try {
                JSONObject object = new JSONObject(agentProgressPayload);
                state.status = object.optString("status", state.status);
                state.output = object.optString("output", state.output);
                state.thinking = object.optString("thinking", state.thinking);
                state.toolCallCount = object.optInt("tool_call_count", state.toolCallCount);
                JSONArray toolCalls = object.optJSONArray("tool_calls");
                if (toolCalls != null) {
                    state.toolCalls = new JSONArray(toolCalls.toString());
                }
                state.error = agentError || object.optBoolean("error", false) || "error".equals(state.status);
            } catch (Exception ignored) {
                state.output = agentProgressPayload == null ? state.output : agentProgressPayload;
                state.error = agentError;
            }
            error = error || state.error;
            publish(error);
        }

        void finishAgent(PipelineAgent agent, AgentRunResult result) {
            PipelineAgentState state = stateById.get(agent.id);
            if (state != null) {
                state.status = result.error ? "error" : "done";
                state.output = result.output;
                state.toolCallCount = result.toolCallCount;
                state.error = result.error;
            }
            error = error || result.error;
            publish(error);
        }

        void terminate() {
            status = "error";
            error = true;
            for (PipelineAgentState state : stateById.values()) {
                if ("running".equals(state.status) || "waiting".equals(state.status)) {
                    state.status = "error";
                    state.error = true;
                    state.output = AGENT_TERMINATED_MESSAGE;
                }
            }
            publish(true);
        }

        private void publish(boolean nextError) {
            if (parentContext == null || parentContext.getToolCallId().length() == 0) {
                return;
            }
            parentContext.reportToolProgress("agent_pipeline", payload(), nextError);
        }

        private String payload() {
            try {
                JSONObject object = new JSONObject();
                object.put("linecode_agent_pipeline_progress", true);
                object.put("kind", "agent_pipeline");
                object.put("status", status);
                object.put("total", agents.size());
                object.put("completed", countStatus("done"));
                object.put("running", countStatus("running"));
                object.put("failed", countFailed());
                JSONArray array = new JSONArray();
                for (PipelineAgentState state : stateById.values()) {
                    array.put(state.toJson());
                }
                object.put("agents", array);
                return object.toString();
            } catch (Exception e) {
                return "Agent 流水线运行中。";
            }
        }

        private int countStatus(String value) {
            int count = 0;
            for (PipelineAgentState state : stateById.values()) {
                if (value.equals(state.status)) {
                    count++;
                }
            }
            return count;
        }

        private int countFailed() {
            int count = 0;
            for (PipelineAgentState state : stateById.values()) {
                if (state.error || "error".equals(state.status)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class PipelineAgentState {
        private final String id;
        private final String type;
        private final String description;
        private String status = "waiting";
        private String output = "";
        private String thinking = "";
        private int toolCallCount;
        private boolean error;
        private JSONArray toolCalls = new JSONArray();

        PipelineAgentState(PipelineAgent agent) {
            this.id = agent == null ? "" : agent.id;
            this.type = agent == null ? "" : agent.type;
            this.description = agent == null ? "" : agent.description;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject()
                    .put("id", id)
                    .put("type", type)
                    .put("description", description)
                    .put("status", status)
                    .put("output", output)
                    .put("thinking", thinking)
                    .put("tool_call_count", toolCallCount)
                    .put("error", error)
                    .put("tool_calls", toolCalls);
        }
    }

    public MainCoordinator(Context context) {
        this(new MainDependencies(context));
    }

    MainCoordinator(MainDependencies dependencies) {
        modelRepository = dependencies.modelRepository;
        modelManagementController = new ModelManagementController(
                modelRepository,
                new ModelManagementController.Host() {
                    @Override
                    public void refreshModelsScreen() {
                        refreshVisibleScreen("models");
                    }

                    @Override
                    public void returnToModelsScreen() {
                        returnToScreen("models");
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        aiBehaviorSettingsRepository = dependencies.aiBehaviorSettingsRepository;
        chatModeRepository = dependencies.chatModeRepository;
        inputSettingsRepository = dependencies.inputSettingsRepository;
        outputSettingsRepository = dependencies.outputSettingsRepository;
        themeSettingsRepository = dependencies.themeSettingsRepository;
        promptTemplateRepository = dependencies.promptTemplateRepository;
        conversationRepository = dependencies.conversationRepository;
        projectRepository = dependencies.projectRepository;
        learningContextRepository = dependencies.learningContextRepository;
        memoryExtractionService = dependencies.memoryExtractionService;
        toolSettingsRepository = dependencies.toolSettingsRepository;
        extensionRepository = dependencies.extensionRepository;
        diffRepository = dependencies.diffRepository;
        fileTreeRepository = dependencies.fileTreeRepository;
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
        sshFileTreeController = new SshFileTreeController(
                sshFileTreeRepository,
                new SshFileTreeController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String projectLabel() {
                        return projectLabel;
                    }

                    @Override
                    public boolean isExpanded(String path) {
                        return expandedFilePaths.contains(path);
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        if (path != null && path.length() > 0) {
                            expandedFilePaths.add(path);
                        }
                    }

                    @Override
                    public void setProjectPathFromSshRoot(String path) {
                        projectPath = path == null ? "" : path;
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        fileOperationController = new FileOperationController(
                fileTreeRepository,
                sshFileTreeRepository,
                new FileOperationController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public void showInputDialog(String title, String message, String initialValue, String actionId) {
                        if (view != null) {
                            view.showInputDialog(title, message, initialValue, actionId);
                        }
                    }

                    @Override
                    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
                        if (view != null) {
                            view.showConfirmationDialog(title, message, confirmLabel, danger, actionId);
                        }
                    }

                    @Override
                    public void showFileActionDialog(String title, String subtitle, ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showFileActionDialog(title, subtitle, options);
                        }
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        if (path != null && path.length() > 0) {
                            expandedFilePaths.add(path);
                        }
                    }

                    @Override
                    public void refreshSshDirectoryAfterFileOperation(String path) {
                        sshFileTreeController.refreshDirectoryAfterFileOperation(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public String parentPath(String path) {
                        return MainCoordinator.this.parentPath(path);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        permissionModeController = new PermissionModeController(
                toolSettingsRepository,
                chatModeRepository,
                new PermissionModeController.Host() {
                    @Override
                    public boolean hasExternalStorageAccess() {
                        return storagePermissionManager.hasExternalStorageAccess();
                    }

                    @Override
                    public String storagePermissionMessage() {
                        return storagePermissionManager.permissionDeniedMessage();
                    }

                    @Override
                    public void showPermissionSheet(ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showSheet("权限设置", options);
                        }
                    }
                }
        );
        projectSheetController = new ProjectSheetController(
                projectRepository,
                new ProjectSheetController.Host() {
                    @Override
                    public String executionMode() {
                        return toolSettingsRepository.getExecutionMode();
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return MainCoordinator.this.isTermuxSshHost();
                    }

                    @Override
                    public boolean hasExternalStorageAccess() {
                        return storagePermissionManager.hasExternalStorageAccess();
                    }

                    @Override
                    public String storagePermissionMessage() {
                        return storagePermissionManager.permissionDeniedMessage();
                    }
                }
        );
        settingsManagementController = new SettingsManagementController(
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                promptTemplateRepository,
                learningContextRepository,
                outputSettingsRepository,
                themeSettingsRepository,
                toolSettingsRepository,
                new SettingsManagementController.Host() {
                    @Override
                    public String currentProjectPath() {
                        return projectPath;
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void recreateForTheme(String screenId) {
                        if (view != null) {
                            view.recreateForTheme(screenId);
                        }
                    }

                    @Override
                    public void afterMcpExecutionModeChanged(String executionMode) {
                        applyProject(projectRepository.ensureSelectedProjectPath(executionMode));
                        sshFileTreeController.invalidateFileTree();
                        requestSshFileTreeLoad(true);
                        refreshVisibleScreen("mcp");
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void refreshMcpScreen() {
                        refreshVisibleScreen("mcp");
                    }

                    @Override
                    public void returnToToolSettings() {
                        returnToScreen("toolSettings");
                    }
                }
        );
        lineCodeArchiveController = new LineCodeArchiveController(
                dependencies.lineCodeArchiveService,
                new LineCodeArchiveController.Host() {
                    @Override
                    public void openExportPicker(String fileName) {
                        if (view != null) {
                            view.openLineCodeExportPicker(fileName);
                        }
                    }

                    @Override
                    public void persistBeforeExport() {
                        persistCurrentConversation();
                    }

                    @Override
                    public void openImportPicker() {
                        if (view != null) {
                            view.openLineCodeImportPicker();
                        }
                    }

                    @Override
                    public void showImportConfirmation(String sourceName) {
                        if (view != null) {
                            view.showConfirmationDialog(
                                    "覆盖导入 .linecode",
                                    "将从「" + sourceName + "」恢复数据库、聊天记录、配置和 .linecode 工作区文件。当前本机数据会被覆盖。",
                                    "覆盖导入",
                                    true,
                                    "data:import_linecode"
                            );
                        }
                    }

                    @Override
                    public void beforeImport() {
                        cancelActiveGeneration();
                        chatSessionStore.setStreaming(false);
                    }

                    @Override
                    public void afterImport() {
                        reloadAfterLineCodeImport();
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        chatUiStateAssembler = new ChatUiStateAssembler(
                modelRepository,
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                outputSettingsRepository,
                contextManager
        );
        toolRunController = new ToolRunController(toolExecutionCoordinator, toolRegistry, toolSettingsRepository);
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        expandedFilePaths.add(projectPath);
        loadCurrentConversation();
    }

    @Override
    public void attachView(MainContract.View view) {
        this.view = view;
        render();
        requestSshFileTreeLoad(false);
        validateSelectedProjectAvailabilityOnStartup();
    }

    @Override
    public void detachView() {
        view = null;
    }

    @Override
    public void destroy() {
        detachView();
        cancelActiveGeneration();
        HttpServerTool.stopActiveServer();
        backgroundTasks.shutdownNow();
    }

    @Override
    public void onMenuClick() {
        requestSshFileTreeLoad(false);
        if (view != null) {
            view.showDrawer();
        }
    }

    @Override
    public void onProjectClick() {
        if (view == null) {
            return;
        }
        ProjectSheetController.ProjectSheet sheet = projectSheetController.buildProjectSheet();
        view.showSheet(sheet.getTitle(), sheet.getOptions());
    }

    @Override
    public void onPermissionClick() {
        permissionModeController.showPermissionSheet();
    }

    @Override
    public void onNewConversation() {
        cancelActiveGeneration();
        if (chatSessionStore.isStreaming()) {
            markStreamingMessagesStopped();
            markRunningAgentProgressStopped();
        }
        chatSessionStore.setStreaming(false);
        persistCurrentConversation();
        chatSessionStore.startNewConversation(System.currentTimeMillis());
        clearSessionAutoToolConfirmations();
        lastMessageModelId = "";
        if (view != null) {
            view.hideOverlays();
            view.showChatScreen();
        }
        render();
    }

    @Override
    public void onConversationSelected(String id) {
        if (id == null || id.length() == 0) {
            if (view != null) {
                view.hideOverlays();
            }
            return;
        }
        cancelActiveGeneration();
        boolean wasStreaming = chatSessionStore.isStreaming();
        if (wasStreaming) {
            markStreamingMessagesStopped();
            markRunningAgentProgressStopped();
        }
        chatSessionStore.setStreaming(false);
        if (wasStreaming || !id.equals(chatSessionStore.getCurrentConversationId())) {
            persistCurrentConversation();
        }
        loadConversation(id);
        clearSessionAutoToolConfirmations();
        if (view != null) {
            view.hideOverlays();
            view.showChatScreen();
        }
        render();
    }

    @Override
    public void onConversationDeleted(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        conversationRepository.deleteConversation(id);
        if (id.equals(chatSessionStore.getCurrentConversationId())) {
            cancelActiveGeneration();
            chatSessionStore.setStreaming(false);
            chatSessionStore.clearCurrentConversation();
            clearSessionAutoToolConfirmations();
        }
        render();
    }

    @Override
    public void onCurrentProjectRemoveRequested() {
        String executionMode = toolSettingsRepository.getExecutionMode();
        ProjectRecord selected = projectRepository.getSelectedProject(executionMode);
        if (selected == null || WorkspacePaths.DEFAULT_PROJECT_ID.equals(selected.getId()) || "ssh:default".equals(selected.getId())) {
            return;
        }
        boolean deleted = projectRepository.deleteProject(selected.getId(), executionMode);
        if (deleted) {
            applyProject(projectRepository.ensureSelectedProjectPath(executionMode));
            render();
        }
    }

    @Override
    public void onFileNodeSelected(String path, boolean directory) {
        if (path == null || path.length() == 0) {
            return;
        }
        if (directory) {
            if (expandedFilePaths.contains(path)) {
                expandedFilePaths.remove(path);
                if (isSshExecutionMode()) {
                    sshFileTreeController.rebuildCachedTree();
                }
            } else {
                expandedFilePaths.add(path);
                if (isSshExecutionMode()) {
                    sshFileTreeController.requestDirectoryLoad(path, false, false);
                    sshFileTreeController.rebuildCachedTree();
                }
            }
            render();
        }
    }

    @Override
    public void onFileNodeLongPressed(String path, String name, boolean directory, boolean root) {
        fileOperationController.showFileNodeActions(path, name, directory, root);
    }

    @Override
    public void onFileTreeActivated() {
        requestSshFileTreeLoad(false);
        render();
    }

    @Override
    public void onFileTreeRefresh() {
        expandedFilePaths.clear();
        if (projectPath.length() > 0) {
            expandedFilePaths.add(projectPath);
        }
        requestSshFileTreeLoad(true);
        render();
    }

    @Override
    public void onDirectoryPickerNodeSelected(String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        String selectedPath = path.trim();
        directoryPickerRootPath = selectedPath;
        directoryPickerSelectedPath = selectedPath;
        directoryPickerExpandedPaths.clear();
        directoryPickerExpandedPaths.add(selectedPath);
        directoryPickerTree = rebuildDirectoryPickerTree(directoryPickerTree);
        renderDirectoryPicker();
        refreshDirectoryPicker();
    }

    @Override
    public void onDirectoryPickerConfirmed() {
        String selectedPath = directoryPickerSelectedPath == null ? "" : directoryPickerSelectedPath.trim();
        if (selectedPath.length() == 0) {
            return;
        }
        if (isSshDirectoryPicker()) {
            ProjectRecord project = projectRepository.saveSshProject(selectedPath, WorkspacePaths.basename(selectedPath));
            applyProject(project);
            requestSshFileTreeLoad(true);
        } else {
            ProjectRecord project = projectRepository.saveExternalProject(selectedPath, WorkspacePaths.basename(selectedPath));
            applyProject(project);
        }
        directoryPickerMode = "";
        directoryPickerTree = null;
        if (view != null) {
            view.hideDirectoryPicker();
        }
        render();
    }

    @Override
    public void onDirectoryPickerCancelled() {
        directoryPickerMode = "";
        directoryPickerLoading = false;
    }

    @Override
    public void onDialogInputSubmitted(String actionId, String value) {
        String id = actionId == null ? "" : actionId;
        if (id.startsWith("project:create:")) {
            createProjectFromInput(id.substring("project:create:".length()), value);
            return;
        }
        if (id.startsWith("file:create_file:")) {
            fileOperationController.createFileFromInput(id.substring("file:create_file:".length()), value);
            return;
        }
        if (id.startsWith("file:create_folder:")) {
            fileOperationController.createFolderFromInput(id.substring("file:create_folder:".length()), value);
            return;
        }
        if (id.startsWith("file:rename:")) {
            fileOperationController.renameFileNodeFromInput(id.substring("file:rename:".length()), value);
        }
    }

    @Override
    public void onDialogConfirmed(String actionId) {
        String id = actionId == null ? "" : actionId;
        if (id.startsWith("file:delete:")) {
            fileOperationController.deleteFileNode(id.substring("file:delete:".length()));
            return;
        }
        if ("data:import_linecode".equals(id)) {
            lineCodeArchiveController.confirmImport();
        }
    }

    @Override
    public void onMoreClick() {
        if (view == null) {
            return;
        }
        ArrayList<SheetOption> options = new ArrayList<>();
        options.add(new SheetOption("tutorial", "教程", "打开初学者教程", false));
        options.add(new SheetOption("settings", "设置", "模型、主题、数据与实验功能", false));
        options.add(new SheetOption("compact", "压缩上下文", "将早期上下文总结为隐藏摘要", false));
        options.add(new SheetOption("clear", "清空对话", "清空当前对话消息", false));
        view.showSheet("更多", options);
    }

    @Override
    public void onSendMessage(String text) {
        onSendMessage(text, Collections.emptyList());
    }

    @Override
    public void onSendMessage(String text, List<InputAttachment> attachments) {
        String trimmed = text == null ? "" : text.trim();
        ArrayList<InputAttachment> safeAttachments = sanitizeAttachments(attachments);
        if ((trimmed.isEmpty() && safeAttachments.isEmpty()) || chatSessionStore.isStreaming()) {
            return;
        }
        ensureCurrentConversation();
        String userContent = composeUserContent(trimmed, safeAttachments);
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.USER, userContent, false, safeAttachments));
        persistCurrentConversation();
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT,
                    "还没有可用模型。请进入 设置 → 模型管理 → 添加模型，保存后再发送消息。",
                    false));
            persistCurrentConversation();
            render();
            return;
        }

        String currentModelId = selectedModel.getModelId();
        if (lastMessageModelId.length() > 0 && !lastMessageModelId.equals(currentModelId)) {
            messages.add(ChatMessage.modelSwitchNotice(nextId(), lastMessageModelId, currentModelId));
            persistCurrentConversation();
        }
        lastMessageModelId = currentModelId;

        int generationId = chatSessionStore.nextGenerationId();
        ModelCancellationToken cancellationToken = new ModelCancellationToken();
        currentCancellationToken = cancellationToken;
        chatSessionStore.setStreaming(true);
        render();

        String activeUserMessageId = messages.get(messages.size() - 1).getId();
        if (shouldAutoCompactBeforeRequest(selectedModel, activeUserMessageId)) {
            startContextCompaction(generationId, selectedModel, cancellationToken, true, activeUserMessageId, userContent);
            return;
        }
        startInitialModelRequest(generationId, selectedModel, cancellationToken, userContent);
    }

    @Override
    public void onRecallMessage(String messageId) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        String targetId = messageId == null ? "" : messageId;
        if (targetId.length() == 0) {
            return;
        }
        int targetIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (targetId.equals(message.getId()) && message.getRole() == ChatMessage.Role.USER) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            return;
        }
        ChatMessage recalled = messages.get(targetIndex);
        String recalledText = recallText(recalled.getContent(), recalled.getAttachments());
        ArrayList<InputAttachment> recalledAttachments = new ArrayList<>(recalled.getAttachments());
        while (messages.size() > targetIndex) {
            messages.remove(messages.size() - 1);
        }
        persistCurrentConversation();
        render();
        if (view != null) {
            view.setComposerDraft(recalledText, recalledAttachments);
        }
    }

    @Override
    public void onAttachmentPickerRequested() {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        attachmentPickerActive = true;
        attachmentPickerSource = isSshExecutionMode() ? InputAttachment.SOURCE_SSH : InputAttachment.SOURCE_LOCAL;
        attachmentPickerRootPath = attachmentRootPath(attachmentPickerSource);
        attachmentPickerExpandedPaths.clear();
        if (attachmentPickerRootPath.length() > 0) {
            attachmentPickerExpandedPaths.add(attachmentPickerRootPath);
        }
        refreshAttachmentPicker(true);
    }

    @Override
    public void onAttachmentPickerNodeSelected(String path, boolean directory) {
        if (!attachmentPickerActive || !directory || path == null || path.length() == 0) {
            return;
        }
        String cleanPath = path.trim();
        if (attachmentPickerExpandedPaths.contains(cleanPath)) {
            attachmentPickerExpandedPaths.remove(cleanPath);
        } else {
            attachmentPickerExpandedPaths.add(cleanPath);
        }
        refreshAttachmentPicker(false);
    }

    @Override
    public void onAttachmentPickerCancelled() {
        attachmentPickerActive = false;
        attachmentPickerLoading = false;
        attachmentPickerMessage = "";
        attachmentPickerLoadGeneration++;
    }

    private ArrayList<InputAttachment> sanitizeAttachments(List<InputAttachment> rawAttachments) {
        ArrayList<InputAttachment> result = new ArrayList<>();
        if (rawAttachments == null) {
            return result;
        }
        for (InputAttachment attachment : rawAttachments) {
            if (attachment == null || attachment.getPath().length() == 0) {
                continue;
            }
            boolean exists = false;
            for (InputAttachment current : result) {
                if (current.matches(attachment.getPath(), attachment.getSource())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(new InputAttachment(attachment.getName(), attachment.getPath(), attachment.getSource()));
            }
        }
        return result;
    }

    private String composeUserContent(String text, List<InputAttachment> attachments) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() > 0) {
            return trimmed;
        }
        return attachments == null || attachments.isEmpty() ? "" : "已附加文件";
    }

    private String recallText(String content, List<InputAttachment> attachments) {
        String value = content == null ? "" : content.trim();
        if ("已附加文件".equals(value) && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        return content == null ? "" : content;
    }

    @Override
    public void onChatModeChanged(String mode) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        chatModeRepository.applyMode(mode, toolSettingsRepository);
        syncModePermission();
        render();
    }

    @Override
    public void onStopGeneration() {
        flushPendingAssistantDelta();
        cancelActiveGeneration();
        chatSessionStore.setStreaming(false);
        chatSessionStore.invalidateActiveGeneration();
        streamingRawTextByMessageId.clear();
        markStreamingMessagesStopped();
        markRunningAgentProgressStopped();
        persistCurrentConversation();
        render();
    }

    private void startInitialModelRequest(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            String userInput
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return;
        }
        ArrayList<ModelMessage> requestMessages = buildModelMessages(userInput);
        String assistantId = nextId();
        streamingRawTextByMessageId.put(assistantId, new StringBuilder());
        messages.add(new ChatMessage(assistantId, ChatMessage.Role.ASSISTANT, "", true));
        persistCurrentConversation();
        render();

        backgroundTasks.execute("linecode-model-stream", () -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions requestOptions = requestOptions(aiSettings, selectedModel, 0);
                ModelCompletionResponse response = modelClient.stream(selectedModel, requestMessages, new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        appendAssistantDelta(generationId, assistantId, delta, "");
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        appendAssistantDelta(generationId, assistantId, "", delta);
                    }
                }, cancellationToken, requestOptions);
                finishGeneration(generationId, assistantId, selectedModel, requestOptions, response, 0);
            } catch (ModelCompletionException e) {
                failGeneration(generationId, assistantId, "模型通信失败：\n" + e.getMessage());
            }
        });
    }

    private boolean shouldAutoCompactBeforeRequest(ModelConfig selectedModel, String activeUserMessageId) {
        if (selectedModel == null) {
            return false;
        }
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        int contextTokens = ModelContextParser.parse(selectedModel.getModelId()).getContextTokens();
        if (!contextCompactionService.shouldCompact(messages, contextTokens, contextManager, aiSettings.isPreserveReasoningEnabled())) {
            return false;
        }
        ArrayList<ChatMessage> preservedTail = getAutoCompactPreservedTail(activeUserMessageId);
        HashSet<String> preservedIds = messageIdSet(preservedTail);
        for (ChatMessage message : messages) {
            if (preservedIds.contains(message.getId()) || message.isExcludeFromContext()) {
                continue;
            }
            if (message.getContent().trim().length() > 0 || message.getReasoningContent().trim().length() > 0 || message.hasToolCalls()) {
                return true;
            }
        }
        return false;
    }

    private void showCompactConfirmation() {
        if (view == null) {
            return;
        }
        ArrayList<SheetOption> options = new ArrayList<>();
        options.add(new SheetOption("compact:confirm", "确认压缩",
                "把早期上下文总结成隐藏摘要，旧消息仍保留在历史中。", false));
        options.add(new SheetOption("compact:cancel", "取消", "返回当前对话", false));
        view.showSheet("压缩上下文", options);
    }

    private void startManualContextCompaction() {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            showNotice("还没有可用模型。请先配置模型，再压缩上下文。");
            return;
        }
        if (messages.size() < 4) {
            showNotice("当前上下文不足，无需压缩。");
            return;
        }
        ensureCurrentConversation();
        int generationId = chatSessionStore.nextGenerationId();
        ModelCancellationToken cancellationToken = new ModelCancellationToken();
        currentCancellationToken = cancellationToken;
        chatSessionStore.setStreaming(true);
        startContextCompaction(generationId, selectedModel, cancellationToken, false, "", "");
    }

    private void startContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String activeUserMessageId,
            String userInput
    ) {
        ArrayList<ChatMessage> preservedTail = continueAfterCompaction
                ? getAutoCompactPreservedTail(activeUserMessageId)
                : new ArrayList<>();
        HashSet<String> preservedIds = messageIdSet(preservedTail);
        ArrayList<ChatMessage> baseMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!preservedIds.contains(message.getId())) {
                baseMessages.add(message);
            }
        }
        if (!hasCompactableBaseMessages(baseMessages)) {
            if (continueAfterCompaction) {
                startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            } else {
                chatSessionStore.setStreaming(false);
                currentCancellationToken = null;
                render();
            }
            return;
        }
        String progressId = nextId();
        messages.add(ChatMessage.compactProgress(progressId, ChatMessage.COMPACT_STATUS_RUNNING));
        persistCurrentConversation();
        render();

        ArrayList<ChatMessage> baseSnapshot = new ArrayList<>(baseMessages);
        backgroundTasks.execute("linecode-context-compact", () -> {
            try {
                ContextCompactionResult result = contextCompactionService.compact(selectedModel, baseSnapshot, cancellationToken);
                mainThread.post(() -> finishContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        baseSnapshot,
                        preservedIds,
                        progressId,
                        result
                ));
            } catch (ModelCompletionException e) {
                mainThread.post(() -> failContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        progressId,
                        "上下文压缩失败：" + e.getMessage()
                ));
            }
        });
    }

    private void finishContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String userInput,
            ArrayList<ChatMessage> baseSnapshot,
            HashSet<String> preservedIds,
            String progressId,
            ContextCompactionResult result
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            markCompactProgress(progressId, ChatMessage.COMPACT_STATUS_ERROR, false);
            persistCurrentConversation();
            render();
            return;
        }
        if (!chatSessionStore.isActiveGeneration(generationId)) {
            return;
        }
        if (result == null || result.getSummaryContent().trim().length() == 0) {
            failContextCompaction(generationId, selectedModel, cancellationToken, continueAfterCompaction, userInput,
                    progressId, "上下文压缩失败：模型没有返回摘要。");
            return;
        }
        HashSet<String> baseIds = messageIdSet(baseSnapshot);
        ArrayList<ChatMessage> compacted = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (progressId.equals(message.getId()) || preservedIds.contains(message.getId())) {
                continue;
            }
            compacted.add(baseIds.contains(message.getId()) ? message.withExcludeFromContext(true) : message);
        }
        ChatMessage summaryMessage = new ChatMessage(nextId(), ChatMessage.Role.USER,
                result.getSummaryContent(), "", false, true, false)
                .withResponseInputItemJson(result.getResponseInputItemJson());
        compacted.add(summaryMessage);
        for (ChatMessage message : messages) {
            if (preservedIds.contains(message.getId())) {
                compacted.add(message);
            }
        }
        compacted.add(ChatMessage.compactProgress(progressId, ChatMessage.COMPACT_STATUS_DONE)
                .withCompactStatus(ChatMessage.COMPACT_STATUS_DONE, false));
        messages.clear();
        messages.addAll(compacted);
        persistCurrentConversation();
        render();
        if (continueAfterCompaction) {
            startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            return;
        }
        chatSessionStore.setStreaming(false);
        currentCancellationToken = null;
        render();
    }

    private void failContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String userInput,
            String progressId,
            String message
    ) {
        if (!chatSessionStore.isActiveGeneration(generationId)) {
            return;
        }
        markCompactProgress(progressId, ChatMessage.COMPACT_STATUS_ERROR, false);
        if (message != null && message.trim().length() > 0) {
            messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, message, false)
                    .withExcludeFromContext(true));
        }
        persistCurrentConversation();
        render();
        if (continueAfterCompaction && (cancellationToken == null || !cancellationToken.isCancelled())) {
            startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            return;
        }
        chatSessionStore.setStreaming(false);
        currentCancellationToken = null;
        render();
    }

    private ArrayList<ChatMessage> getAutoCompactPreservedTail(String activeUserMessageId) {
        ArrayList<ChatMessage> contextMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!message.isExcludeFromContext()) {
                contextMessages.add(message);
            }
        }
        if (contextMessages.isEmpty()) {
            return new ArrayList<>();
        }
        ChatMessage last = contextMessages.get(contextMessages.size() - 1);
        if (last.getRole() == ChatMessage.Role.USER
                && (activeUserMessageId == null || activeUserMessageId.length() == 0 || activeUserMessageId.equals(last.getId()))) {
            ArrayList<ChatMessage> tail = new ArrayList<>();
            tail.add(last);
            return tail;
        }
        if (last.getRole() == ChatMessage.Role.TOOL) {
            for (int i = contextMessages.size() - 1; i >= 0; i--) {
                ChatMessage message = contextMessages.get(i);
                if (message.getRole() == ChatMessage.Role.ASSISTANT && message.hasToolCalls()) {
                    return new ArrayList<>(contextMessages.subList(i, contextMessages.size()));
                }
            }
        }
        return new ArrayList<>();
    }

    private HashSet<String> messageIdSet(List<ChatMessage> source) {
        HashSet<String> ids = new HashSet<>();
        if (source == null) {
            return ids;
        }
        for (ChatMessage message : source) {
            if (message != null) {
                ids.add(message.getId());
            }
        }
        return ids;
    }

    private boolean hasCompactableBaseMessages(List<ChatMessage> source) {
        if (source == null) {
            return false;
        }
        for (ChatMessage message : source) {
            if (message == null || message.isExcludeFromContext()) {
                continue;
            }
            if (message.getContent().trim().length() > 0
                    || message.getReasoningContent().trim().length() > 0
                    || message.hasToolCalls()
                    || message.getResponseInputItemJson().length() > 0) {
                return true;
            }
        }
        return false;
    }

    private void markCompactProgress(String progressId, String status, boolean nextStreaming) {
        int index = findMessageIndex(progressId);
        if (index < 0) {
            return;
        }
        messages.set(index, messages.get(index).withCompactStatus(status, nextStreaming));
    }

    @Override
    public void onToolReview(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        if (pendingToolExecution != null && toolCallId.equals(pendingToolExecution.toolCall.getId())) {
            handlePendingToolReview(state);
            return;
        }
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        String resolvedDiffId = diffId == null ? "" : diffId;
        if ("rejected".equals(normalizedState)) {
            if (resolvedDiffId.length() == 0) {
                resolvedDiffId = findToolMessageDiffId(toolCallId);
            }
            if (resolvedDiffId.length() > 0) {
                String targetDiffId = resolvedDiffId;
                backgroundTasks.execute("linecode-diff-revert", () -> {
                    DiffRecord diffRecord = diffRepository.getDiff(targetDiffId);
                    DiffRepository.RevertResult result = diffRepository.revertDiff(targetDiffId);
                    String filePath = diffRecord == null ? "" : diffRecord.getFilePath();
                    mainThread.post(() -> {
                        updateToolReview(toolCallId, targetDiffId, result.isSuccess() ? "rejected" : "", result.getMessage());
                        if (result.isSuccess()) {
                            refreshFileTreeAfterRevert(filePath);
                        }
                        persistCurrentConversation();
                        render();
                    });
                });
                return;
            }
        }
        updateToolReview(toolCallId, resolvedDiffId, normalizedState, "");
        persistCurrentConversation();
        render();
    }

    @Override
    public void onSheetOptionSelected(String id) {
        if (id != null && id.startsWith("project:select:")) {
            selectProject(id.substring("project:select:".length()));
        } else if (id != null && id.startsWith("project:delete:")) {
            deleteProjectFromPicker(id.substring("project:delete:".length()));
            return;
        } else if ("project:open_local_saf".equals(id)) {
            requestOpenLocalProjectSaf();
            return;
        } else if ("project:create".equals(id)) {
            if (view != null) {
                view.showInputDialog("创建工作区", "输入工作区名称", "", "project:create:" + toolSettingsRepository.getExecutionMode());
            }
            return;
        } else if (id != null && id.startsWith("file:create_file:")) {
            fileOperationController.requestCreateFile(id.substring("file:create_file:".length()));
            return;
        } else if (id != null && id.startsWith("file:create_folder:")) {
            fileOperationController.requestCreateFolder(id.substring("file:create_folder:".length()));
            return;
        } else if (id != null && id.startsWith("file:copy:")) {
            fileOperationController.copyFileNode(id.substring("file:copy:".length()));
        } else if (id != null && id.startsWith("file:paste:")) {
            fileOperationController.pasteFileNode(id.substring("file:paste:".length()));
            return;
        } else if (id != null && id.startsWith("file:rename:")) {
            fileOperationController.requestRenameFileNode(id.substring("file:rename:".length()));
            return;
        } else if (id != null && id.startsWith("file:delete:")) {
            fileOperationController.requestDeleteFileNode(id.substring("file:delete:".length()));
            return;
        } else if ("storage:manage_all_files".equals(id)) {
            openStoragePermissionSettings();
        } else if (permissionModeController.applyPermissionModeOption(id)) {
            // Handled above.
        } else if ("settings".equals(id)) {
            showScreen("settings");
        } else if ("tutorial".equals(id)) {
            showScreen("tutorial");
        } else if ("compact".equals(id)) {
            showCompactConfirmation();
            return;
        } else if ("compact:confirm".equals(id)) {
            startManualContextCompaction();
        } else if ("compact:cancel".equals(id)) {
            // The bottom sheet is closed below.
        } else if ("clear".equals(id)) {
            String currentConversationId = chatSessionStore.getCurrentConversationId();
            messages.clear();
            if (currentConversationId.length() > 0) {
                conversationRepository.deleteConversation(currentConversationId);
            }
            chatSessionStore.clearCurrentConversation();
            clearSessionAutoToolConfirmations();
        }
        if (view != null && !"settings".equals(id) && !"tutorial".equals(id)) {
            view.hideOverlays();
        }
        render();
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
            if (view != null) {
                view.openExternalUrl(safeUrl);
            }
            return;
        }
        showScreen("browser:" + safeUrl);
    }

    @Override
    public void showModelManagement() {
        showScreen("models");
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
        return extensionRepository.getOverview(projectPath);
    }

    @Override
    public void onAgentExtensionSaved(ExtensionAgentConfig config) {
        extensionRepository.saveAgentExtension(config);
        toolRegistry.reloadExtensions();
        returnToScreen("extension:agent");
        render();
    }

    @Override
    public ExtensionAgentConfig onAgentDraftGenerated(String description) throws Exception {
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            throw new IllegalStateException("缺少模型，请先在设置中添加并选择一个模型。");
        }
        String request = safe(description).trim();
        if (request.length() == 0) {
            throw new IllegalArgumentException("请先描述你想创建的 Agent。");
        }
        ArrayList<BaseTool> tools = getExtensionAvailableTools();
        JSONArray toolList = new JSONArray();
        HashSet<String> allowedTools = new HashSet<>();
        for (BaseTool tool : tools) {
            allowedTools.add(tool.getName());
            toolList.put(new JSONObject()
                    .put("name", tool.getName())
                    .put("category", tool.getCategory().name().toLowerCase(Locale.ROOT))
                    .put("description", tool.getDescription()));
        }
        JSONArray mcpList = agentMcpOptionsJson();
        HashSet<String> allowedMcps = agentMcpOptionIds();
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new SystemModelMessage("你是 LineCode 的自定义 Agent 配置生成器。\n"
                + "根据用户描述生成一个 Agent 配置草稿，只输出 JSON 对象，不要 Markdown，不要解释。\n"
                + "JSON 字段必须是：name, slug, prompt, trigger, toolNames, mcpIds。\n"
                + "name 使用简短中文名；slug 使用小写英文、数字、- 或 _，必须以小写字母开头。\n"
                + "prompt 要写成可直接作为 Agent 系统提示词使用的中文说明，包含角色、任务边界、工作流程、输出要求和安全约束。\n"
                + "trigger 用中文描述何时适合触发这个 Agent。\n"
                + "toolNames 只能从可用工具 name 中选择；mcpIds 只能从可用 MCP id 中选择；不确定时优先选择 file_read 和 glob。"));
        messages.add(new UserModelMessage(new JSONObject()
                .put("userNeed", request)
                .put("availableTools", toolList)
                .put("availableMcp", mcpList)
                .toString(2)));
        ModelCompletionResponse response = modelClient.complete(selectedModel, messages);
        JSONObject draft = extractJsonObject(response.getText());
        String name = draft.optString("name").trim();
        String slug = normalizeAgentSlug(draft.optString("slug", name));
        String prompt = draft.optString("prompt").trim();
        String trigger = draft.optString("trigger").trim();
        if (name.length() == 0 || slug.length() == 0 || prompt.length() == 0) {
            throw new IllegalStateException("AI 返回的配置缺少 name、slug 或 prompt。");
        }
        List<String> selectedTools = filteredStringArray(draft.optJSONArray("toolNames"), allowedTools);
        if (selectedTools.isEmpty()) {
            selectedTools = defaultAgentTools(allowedTools);
        }
        List<String> selectedMcps = filteredStringArray(draft.optJSONArray("mcpIds"), allowedMcps);
        return new ExtensionAgentConfig("", true, name, slug, prompt, trigger, selectedTools, selectedMcps, 0, 0);
    }

    @Override
    public ArrayList<BaseTool> getExtensionAvailableTools() {
        toolRegistry.reloadExtensions();
        ArrayList<BaseTool> tools = new ArrayList<>();
        for (BaseTool tool : toolRegistry.getAll()) {
            if (tool != null && !ToolRegistry.isExtensionToolName(tool.getName())) {
                tools.add(tool);
            }
        }
        return tools;
    }

    @Override
    public void onMcpExtensionSaved(ExtensionMcpConfig config) {
        extensionRepository.saveMcpExtension(config);
        toolRegistry.reloadExtensions();
        returnToScreen("extension:mcp");
        render();
    }

    @Override
    public List<McpToolSummary> onMcpToolsQuery(String url, List<McpRequestHeader> headers) throws Exception {
        return extensionRepository.queryMcpTools(url, headers);
    }

    @Override
    public SkillRecord onSkillCreated(String location, String name, String description, String content) {
        SkillRecord skill = extensionRepository.createSkill(projectPath, location, name, description, content);
        returnToScreen("extension:skills");
        render();
        return skill;
    }

    @Override
    public SkillRecord onSkillInstalled(String location, String sourcePath, String name) throws Exception {
        SkillRecord skill = extensionRepository.installSkill(projectPath, location, sourcePath, name);
        returnToScreen("extension:skills");
        render();
        return skill;
    }

    @Override
    public SkillRecord onSkillInstalledFromUri(String location, String uri, String displayName) throws Exception {
        SkillRecord skill = extensionRepository.installSkillFromUri(projectPath, location, uri, displayName);
        returnToScreen("extension:skills");
        render();
        return skill;
    }

    @Override
    public void onExtensionEnabledChanged(String kind, String id, boolean enabled) {
        if ("agent".equals(kind)) {
            extensionRepository.setAgentEnabled(id, enabled);
        } else if ("mcp".equals(kind)) {
            extensionRepository.setMcpEnabled(id, enabled);
        } else if ("skills".equals(kind)) {
            extensionRepository.setSkillEnabled(id, enabled);
        }
        toolRegistry.reloadExtensions();
        refreshVisibleScreen("extension:" + kind);
        render();
    }

    @Override
    public void onExtensionDeleted(String kind, String id) {
        if ("agent".equals(kind)) {
            extensionRepository.deleteAgent(id);
        } else if ("mcp".equals(kind)) {
            extensionRepository.deleteMcp(id);
        } else if ("skills".equals(kind)) {
            extensionRepository.deleteSkill(id);
        }
        toolRegistry.reloadExtensions();
        refreshVisibleScreen("extension:" + kind);
        render();
    }

    private JSONArray agentMcpOptionsJson() throws Exception {
        JSONArray array = new JSONArray();
        for (McpToolConfig config : toolSettingsRepository.getConfigs()) {
            JSONArray tools = new JSONArray();
            for (String tool : config.getTools()) {
                tools.put(tool);
            }
            array.put(new JSONObject()
                    .put("id", "builtin:" + config.getId())
                    .put("name", config.getName())
                    .put("description", tools.toString()));
        }
        for (ExtensionMcpConfig mcp : extensionRepository.getMcpExtensions()) {
            if (!mcp.isEnabled()) {
                continue;
            }
            JSONArray tools = new JSONArray();
            for (McpToolSummary tool : mcp.getTools()) {
                if (tool.isEnabled()) {
                    tools.put(tool.getName());
                }
            }
            array.put(new JSONObject()
                    .put("id", "custom:" + mcp.getId())
                    .put("name", mcp.getName())
                    .put("description", tools.toString()));
        }
        return array;
    }

    private HashSet<String> agentMcpOptionIds() {
        HashSet<String> ids = new HashSet<>();
        for (McpToolConfig config : toolSettingsRepository.getConfigs()) {
            ids.add("builtin:" + config.getId());
        }
        for (ExtensionMcpConfig mcp : extensionRepository.getMcpExtensions()) {
            if (mcp.isEnabled()) {
                ids.add("custom:" + mcp.getId());
            }
        }
        return ids;
    }

    private void reloadAfterLineCodeImport() {
        toolRegistry.reloadExtensions();
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        sshFileTreeController.invalidateFileTree();
        requestSshFileTreeLoad(true);
        ConversationRecord current = conversationRepository.getCurrentConversation();
        if (current == null) {
            chatSessionStore.clearCurrentConversation();
        } else {
            applyConversation(current);
        }
        refreshVisibleScreen("data");
        render();
    }

    private JSONObject extractJsonObject(String text) throws Exception {
        String source = safe(text);
        int fenceStart = source.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = source.indexOf('\n', fenceStart);
            int fenceEnd = contentStart < 0 ? -1 : source.indexOf("```", contentStart + 1);
            if (contentStart >= 0 && fenceEnd > contentStart) {
                source = source.substring(contentStart + 1, fenceEnd);
            }
        }
        int start = source.indexOf('{');
        int end = source.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI 没有返回有效 JSON。");
        }
        return new JSONObject(source.substring(start, end + 1));
    }

    private List<String> filteredStringArray(JSONArray array, Set<String> allowed) {
        if (array == null || allowed == null || allowed.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (allowed.contains(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> defaultAgentTools(Set<String> allowed) {
        ArrayList<String> values = new ArrayList<>();
        if (allowed.contains("file_read")) {
            values.add("file_read");
        }
        if (allowed.contains("glob")) {
            values.add("glob");
        }
        if (values.isEmpty() && !allowed.isEmpty()) {
            values.add(allowed.iterator().next());
        }
        return values;
    }

    private String normalizeAgentSlug(String value) {
        String raw = safe(value).trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < raw.length() && builder.length() < 48; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
                lastDash = false;
            } else if (ch == '-') {
                if (!lastDash && builder.length() > 0) {
                    builder.append(ch);
                    lastDash = true;
                }
            } else if (!lastDash && builder.length() > 0) {
                builder.append('-');
                lastDash = true;
            }
        }
        String clean = builder.toString();
        while (clean.endsWith("-") || clean.endsWith("_")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.length() == 0) {
            clean = "custom-agent";
        }
        char first = clean.charAt(0);
        if (first < 'a' || first > 'z') {
            clean = "agent-" + clean;
        }
        return clean;
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
    public List<ConversationRecord> getConversationMetas() {
        return conversationRepository.getConversationMetas();
    }

    @Override
    public String getCurrentConversationId() {
        return chatSessionStore.getCurrentConversationId();
    }

    @Override
    public FileTreeNode getFileTree() {
        if (isSshExecutionMode()) {
            return sshFileTreeController.getFileTree();
        }
        return fileTreeRepository.buildTree(projectPath, expandedFilePaths);
    }

    @Override
    public boolean canRemoveCurrentProject() {
        ProjectRecord selected = projectRepository.getSelectedProject();
        return selected != null && !WorkspacePaths.DEFAULT_PROJECT_ID.equals(selected.getId());
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
    public void onModelQuickSwitch(String modelId) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        modelRepository.setSelectedModelId(modelId);
        render();
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
    public void onExternalProjectTreePicked(String treeUri) {
        pendingExternalProjectOpen = false;
        String path = safPathResolver.treeUriToFileSystemPath(treeUri);
        if (path.length() == 0) {
            showNotice("无法将 SAF 目录转换为文件系统路径。请选择内部存储、Download 或具体目录。");
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            showNotice("外部工作区不可访问：\n" + path + "\n请确认已开启“管理所有文件”权限。");
            return;
        }
        ProjectRecord project;
        if (isSshExecutionMode() && isTermuxSshHost()) {
            project = projectRepository.saveSshProject(path, WorkspacePaths.basename(path));
            applyProject(project);
            requestSshFileTreeLoad(true);
        } else {
            project = projectRepository.saveExternalProject(path, WorkspacePaths.basename(path));
            applyProject(project);
        }
        if (view != null) {
            view.hideOverlays();
        }
        render();
    }

    @Override
    public void onExternalProjectPickerCancelled() {
        pendingExternalProjectOpen = false;
    }

    @Override
    public void onStoragePermissionResult() {
        if (!pendingExternalProjectOpen || view == null || !storagePermissionManager.hasExternalStorageAccess()) {
            return;
        }
        pendingExternalProjectOpen = false;
        view.openExternalProjectPicker();
    }

    private void showScreen(String screenId) {
        screenNavigationController.showScreen(screenId, navigationHost);
    }

    private void refreshVisibleScreen(String screenId) {
        screenNavigationController.refreshVisibleScreen(screenId, navigationHost);
    }

    private void returnToScreen(String screenId) {
        screenNavigationController.returnToScreen(screenId, navigationHost);
    }

    private void selectProject(String id) {
        String executionMode = toolSettingsRepository.getExecutionMode();
        projectRepository.setSelected(id, executionMode);
        ProjectRecord project = projectRepository.ensureSelectedProjectPath(executionMode);
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(project.getSource())
                && !storagePermissionManager.hasExternalStorageAccess()) {
            pendingExternalProjectOpen = false;
            if (view != null) {
                view.hideOverlays();
                if (storagePermissionManager.needsManageAllFilesPermission()) {
                    view.openManageAllFilesPermissionSettings();
                } else {
                    view.requestLegacyStoragePermissions();
                }
            }
            showNotice(storagePermissionManager.permissionDeniedMessage());
            return;
        }
        applyProject(project);
        requestSshFileTreeLoad(true);
    }

    private void deleteProjectFromPicker(String id) {
        String executionMode = toolSettingsRepository.getExecutionMode();
        boolean deleted = projectRepository.deleteProject(id, executionMode);
        if (deleted) {
            applyProject(projectRepository.ensureSelectedProjectPath(executionMode));
        }
        render();
        if (view != null) {
            onProjectClick();
        }
    }

    private void applyProject(ProjectRecord project) {
        if (project == null) {
            return;
        }
        projectLabel = project.getLabel().length() == 0 ? "LineCode" : project.getLabel();
        projectSource = project.getSource();
        projectPath = WorkspacePaths.displayPath(project.getPath());
        if (!WorkspacePaths.SOURCE_SSH.equals(projectSource) && projectPath.length() == 0) {
            projectPath = projectRepository.getDefaultHomePath();
        }
        expandedFilePaths.clear();
        if (projectPath.length() > 0) {
            expandedFilePaths.add(projectPath);
        }
        sshFileTreeController.invalidateFileTree();
    }

    private void requestOpenExternalProject() {
        if (view == null) {
            return;
        }
        if (isSshExecutionMode()) {
            startSshDirectoryPicker();
            return;
        }
        requestOpenLocalProjectForCurrentMode();
    }

    private void requestOpenLocalProjectForCurrentMode() {
        requestOpenLocalProjectSaf();
    }

    private void requestOpenLocalProjectSaf() {
        pendingExternalProjectOpen = true;
        if (view == null) {
            return;
        }
        if (storagePermissionManager.hasExternalStorageAccess()) {
            pendingExternalProjectOpen = false;
            view.hideOverlays();
            view.openExternalProjectPicker();
            return;
        }
        view.hideOverlays();
        if (storagePermissionManager.needsManageAllFilesPermission()) {
            view.openManageAllFilesPermissionSettings();
        } else {
            view.requestLegacyStoragePermissions();
        }
    }

    private void openStoragePermissionSettings() {
        pendingExternalProjectOpen = false;
        if (view == null) {
            return;
        }
        view.hideOverlays();
        if (storagePermissionManager.needsManageAllFilesPermission()) {
            view.openManageAllFilesPermissionSettings();
        } else if (!storagePermissionManager.hasExternalStorageAccess()) {
            view.requestLegacyStoragePermissions();
        }
    }

    private void createProjectFromInput(String executionMode, String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() == 0) {
            showNotice("工作区名称不能为空。");
            return;
        }
        if (ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))) {
            backgroundTasks.execute("linecode-ssh-project-create", () -> {
                try {
                    String path = sshFileTreeRepository.createManagedProject(cleanName);
                    ProjectRecord project = projectRepository.saveSshProject(path, cleanName);
                    mainThread.post(() -> {
                        applyProject(project);
                        requestSshFileTreeLoad(true);
                        render();
                    });
                } catch (Exception e) {
                    mainThread.post(() -> showNotice("创建 SSH 工作区失败: " + e.getMessage()));
                }
            });
            return;
        }
        try {
            ProjectRecord project = projectRepository.createManagedProject(cleanName);
            applyProject(project);
            render();
        } catch (RuntimeException e) {
            showNotice("创建工作区失败: " + e.getMessage());
        }
    }

    private void startLocalDirectoryPicker(String mode) {
        directoryPickerMode = ToolSettingsRepository.normalizeExecutionMode(mode);
        directoryPickerRootPath = defaultExternalStorageRoot();
        directoryPickerSelectedPath = directoryPickerRootPath;
        directoryPickerExpandedPaths.clear();
        directoryPickerExpandedPaths.add(directoryPickerRootPath);
        refreshDirectoryPicker();
    }

    private void startSshDirectoryPicker() {
        directoryPickerMode = DIRECTORY_PICKER_MODE_SSH_REMOTE;
        directoryPickerRootPath = projectPath.length() == 0 ? "." : projectPath;
        directoryPickerSelectedPath = directoryPickerRootPath;
        directoryPickerExpandedPaths.clear();
        directoryPickerExpandedPaths.add(directoryPickerRootPath);
        refreshDirectoryPicker();
    }

    private void refreshDirectoryPicker() {
        if (view == null || directoryPickerMode.length() == 0) {
            return;
        }
        if (isRemoteSshDirectoryPicker()) {
            refreshSshDirectoryPicker();
            return;
        }
        try {
            directoryPickerLoading = false;
            directoryPickerMessage = "";
            directoryPickerTree = fileTreeRepository.buildReadableTree(directoryPickerRootPath, directoryPickerExpandedPaths);
        } catch (RuntimeException e) {
            directoryPickerTree = null;
            directoryPickerMessage = e.getMessage();
        }
        renderDirectoryPicker();
    }

    private void refreshSshDirectoryPicker() {
        directoryPickerLoading = true;
        directoryPickerMessage = "正在读取 SSH 目录...";
        renderDirectoryPicker();
        String root = directoryPickerRootPath;
        HashSet<String> expanded = new HashSet<>(directoryPickerExpandedPaths);
        backgroundTasks.execute("linecode-ssh-directory-picker", () -> {
            try {
                FileTreeNode tree = sshFileTreeRepository.buildTree(root, expanded);
                mainThread.post(() -> {
                    directoryPickerTree = tree;
                    String previousRoot = directoryPickerRootPath;
                    String previousSelected = directoryPickerSelectedPath;
                    directoryPickerRootPath = tree.getPath();
                    if (directoryPickerSelectedPath.length() == 0
                            || isSamePickerPath(previousSelected, root)
                            || isSshHomeAlias(previousSelected)) {
                        directoryPickerSelectedPath = tree.getPath();
                    }
                    if (isSamePickerPath(previousRoot, root) || isSshHomeAlias(previousRoot)) {
                        directoryPickerExpandedPaths.add(tree.getPath());
                    }
                    directoryPickerExpandedPaths.add(tree.getPath());
                    directoryPickerTree = rebuildDirectoryPickerTree(directoryPickerTree);
                    directoryPickerLoading = false;
                    directoryPickerMessage = "";
                    renderDirectoryPicker();
                });
            } catch (Exception e) {
                mainThread.post(() -> {
                    directoryPickerLoading = false;
                    directoryPickerMessage = e.getMessage();
                    renderDirectoryPicker();
                });
            }
        });
    }

    private FileTreeNode rebuildDirectoryPickerTree(FileTreeNode node) {
        if (node == null) {
            return null;
        }
        ArrayList<FileTreeNode> children = new ArrayList<>();
        List<FileTreeNode> rawChildren = node.getChildren();
        for (int i = 0; i < rawChildren.size(); i++) {
            children.add(rebuildDirectoryPickerTree(rawChildren.get(i)));
        }
        boolean expanded = node.isDirectory()
                && (node.isExpanded() || directoryPickerExpandedPaths.contains(node.getPath()));
        return new FileTreeNode(node.getName(), node.getPath(), node.isDirectory(), expanded, children);
    }

    private String normalizePickerPath(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isSamePickerPath(String left, String right) {
        return normalizePickerPath(left).equals(normalizePickerPath(right));
    }

    private boolean isSshHomeAlias(String path) {
        String value = path == null ? "" : path.trim();
        return value.length() == 0 || ".".equals(value) || "~".equals(value);
    }

    private void renderDirectoryPicker() {
        if (view == null) {
            return;
        }
        boolean sshMode = isSshDirectoryPicker();
        String title = sshMode ? "选择 SSH 工作区" : "选择本地工作区";
        String subtitle = directoryPickerSelectedPath.length() == 0 ? directoryPickerRootPath : directoryPickerSelectedPath;
        view.showDirectoryPicker(title, subtitle, directoryPickerTree, directoryPickerSelectedPath, directoryPickerLoading, directoryPickerMessage);
    }

    private boolean isRemoteSshDirectoryPicker() {
        return DIRECTORY_PICKER_MODE_SSH_REMOTE.equals(directoryPickerMode)
                || (ToolSettingsRepository.EXECUTION_SSH.equals(directoryPickerMode) && !isTermuxSshHost());
    }

    private boolean isSshDirectoryPicker() {
        return DIRECTORY_PICKER_MODE_SSH_REMOTE.equals(directoryPickerMode)
                || ToolSettingsRepository.EXECUTION_SSH.equals(directoryPickerMode);
    }

    private void refreshAttachmentPicker(boolean resetTree) {
        if (!attachmentPickerActive || view == null) {
            return;
        }
        if (resetTree) {
            attachmentPickerTree = null;
        }
        if (InputAttachment.SOURCE_SSH.equals(attachmentPickerSource)) {
            refreshSshAttachmentPicker();
            return;
        }
        try {
            attachmentPickerLoading = false;
            attachmentPickerMessage = "";
            attachmentPickerTree = fileTreeRepository.buildReadableTree(attachmentPickerRootPath, attachmentPickerExpandedPaths);
        } catch (RuntimeException e) {
            attachmentPickerTree = null;
            attachmentPickerMessage = e.getMessage();
        }
        renderAttachmentPicker();
    }

    private void refreshSshAttachmentPicker() {
        attachmentPickerLoading = true;
        attachmentPickerMessage = "正在通过 SFTP 读取 SSH 文件...";
        renderAttachmentPicker();
        int generation = ++attachmentPickerLoadGeneration;
        String root = attachmentPickerRootPath;
        HashSet<String> expanded = new HashSet<>(attachmentPickerExpandedPaths);
        backgroundTasks.execute("linecode-ssh-attachment-picker", () -> {
            try {
                FileTreeNode tree = sshFileTreeRepository.buildTree(root, expanded);
                mainThread.post(() -> {
                    if (!attachmentPickerActive || generation != attachmentPickerLoadGeneration) {
                        return;
                    }
                    attachmentPickerTree = tree;
                    attachmentPickerRootPath = tree.getPath();
                    attachmentPickerExpandedPaths.add(tree.getPath());
                    attachmentPickerLoading = false;
                    attachmentPickerMessage = "";
                    renderAttachmentPicker();
                });
            } catch (Exception e) {
                mainThread.post(() -> {
                    if (!attachmentPickerActive || generation != attachmentPickerLoadGeneration) {
                        return;
                    }
                    attachmentPickerTree = null;
                    attachmentPickerLoading = false;
                    attachmentPickerMessage = e.getMessage();
                    renderAttachmentPicker();
                });
            }
        });
    }

    private void renderAttachmentPicker() {
        if (!attachmentPickerActive || view == null) {
            return;
        }
        boolean ssh = InputAttachment.SOURCE_SSH.equals(attachmentPickerSource);
        view.showAttachmentPicker(
                ssh ? "选择 SSH 文件" : "选择本地文件",
                attachmentPickerTree,
                attachmentPickerLoading,
                attachmentPickerMessage,
                attachmentPickerSource
        );
    }

    private String attachmentRootPath(String source) {
        if (InputAttachment.SOURCE_SSH.equals(source)) {
            return projectPath.length() == 0 ? "." : projectPath;
        }
        if (projectPath.length() > 0) {
            return projectPath;
        }
        return projectRepository.getDefaultHomePath();
    }

    private void requestSshFileTreeLoad(boolean force) {
        sshFileTreeController.requestFileTreeLoad(force);
    }

    private boolean isSshExecutionMode() {
        return ToolSettingsRepository.EXECUTION_SSH.equals(toolSettingsRepository.getExecutionMode());
    }

    private boolean isTermuxSshHost() {
        SshConfig config = sshService.getConfig();
        String host = config == null ? "" : config.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    private void validateSelectedProjectAvailabilityOnStartup() {
        if (startupProjectAvailabilityChecked) {
            return;
        }
        startupProjectAvailabilityChecked = true;
        String executionMode = toolSettingsRepository.getExecutionMode();
        ProjectRecord selected = projectRepository.getSelectedProject(executionMode);
        if (selected == null) {
            return;
        }
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(selected.getSource())) {
            String path = WorkspacePaths.displayPath(selected.getPath());
            if (path.length() > 0 && !new File(path).isDirectory()) {
                switchToDefaultProjectWithDialog(
                        executionMode,
                        "工作区不可访问",
                        "已保存的工作区不存在或无法访问：\n" + path + "\n\n已自动切换到默认 home。"
                );
            }
            return;
        }
        if (!WorkspacePaths.SOURCE_SSH.equals(selected.getSource())) {
            return;
        }
        String path = WorkspacePaths.displayPath(selected.getPath());
        if (path.length() == 0) {
            return;
        }
        backgroundTasks.execute("linecode-project-startup-check", () -> {
            try {
                boolean exists = sshFileTreeRepository.directoryExists(path);
                if (!exists) {
                    mainThread.post(() -> switchToDefaultProjectWithDialog(
                            ToolSettingsRepository.EXECUTION_SSH,
                            "SSH 工作区不可访问",
                            "已保存的 SSH 工作区不存在：\n" + path + "\n\n已自动切换到 ~。"
                    ));
                }
            } catch (Exception e) {
                mainThread.post(() -> switchToDefaultProjectWithDialog(
                        ToolSettingsRepository.EXECUTION_SSH,
                        "SSH 工作区不可访问",
                        "无法访问已保存的 SSH 工作区：\n" + path + "\n\n" + e.getMessage() + "\n\n已自动切换到 ~。"
                ));
            }
        });
    }

    private void switchToDefaultProjectWithDialog(String executionMode, String title, String message) {
        ProjectRecord fallback = projectRepository.selectDefaultProject(executionMode);
        applyProject(fallback);
        requestSshFileTreeLoad(true);
        render();
        if (view != null) {
            view.showConfirmationDialog(title, message, "知道了", false, "project:missing_notice");
        }
    }

    private String defaultExternalStorageRoot() {
        File primary = new File("/storage/emulated/0");
        if (primary.isDirectory()) {
            return primary.getAbsolutePath();
        }
        File storage = new File("/storage");
        return storage.isDirectory() ? storage.getAbsolutePath() : "/";
    }

    private String basename(String path) {
        return WorkspacePaths.basename(path == null ? "" : path);
    }

    private String parentPath(String path) {
        String value = path == null ? "" : path.trim();
        int index = value.lastIndexOf('/');
        if (index <= 0) {
            return projectPath;
        }
        return value.substring(0, index);
    }

    private void showNotice(String text) {
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
        render();
    }

    private String syncModePermission() {
        String mode = chatModeRepository.getMode();
        chatModeRepository.applyMode(mode, toolSettingsRepository);
        return chatModeRepository.getMode();
    }

    private void render() {
        if (view == null) {
            return;
        }
        String activeChatMode = syncModePermission();
        view.render(chatUiStateAssembler.assemble(
                projectLabel,
                projectSource,
                projectPath,
                chatSessionStore.getCurrentConversationId(),
                activeChatMode,
                chatSessionStore.isStreaming(),
                messages
        ));
    }

    private ArrayList<ModelMessage> buildModelMessages(String userInput) {
        return buildModelMessages(userInput, 0);
    }

    private ArrayList<ModelMessage> buildModelMessages(String userInput, int usedToolCallCount) {
        ArrayList<ModelMessage> modelMessages = new ArrayList<>();
        String activeChatMode = syncModePermission();
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        String learningContext = aiSettings.isLearningModeEnabled()
                ? learningContextRepository.buildLearningContext(projectPath, userInput, chatSessionStore.getCurrentConversationId())
                : "";
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        String promptHomePath = promptHomePath();
        String extensionContext = extensionRepository.buildExtensionPrompt(projectPath);
        String attachmentContext = buildAttachmentPrompt(messages);
        String systemContext = joinPromptContext(joinPromptContext(learningContext, attachmentContext), extensionContext);
        String systemPrompt = systemPromptProvider.build(
                promptHomePath,
                aiSettings.getToneMode(),
                chatModePromptContext(activeChatMode),
                systemContext,
                buildToolPrompt(selectedModel, usedToolCallCount)
        );
        modelMessages.add(new SystemModelMessage(systemPrompt));
        int contextTokens = selectedModel == null
                ? ModelContextParser.parse("").getContextTokens()
                : ModelContextParser.parse(selectedModel.getModelId()).getContextTokens();
        int reservedTokens = contextManager.estimateTokens(systemPrompt) + 2048;
        boolean includeReasoning = aiSettings.isPreserveReasoningEnabled();
        List<ChatMessage> contextWindow = contextManager.selectWindow(messages, contextTokens, reservedTokens, includeReasoning);
        for (ChatMessage message : contextWindow) {
            modelMessages.add(toModelMessage(message, includeReasoning));
        }
        return modelMessages;
    }

    private String chatModePromptContext(String mode) {
        String normalized = ChatMode.normalize(mode);
        if (ChatMode.CHAT.equals(normalized)) {
            return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CHAT_MODE_CHAT);
        }
        if (ChatMode.PLAN.equals(normalized)) {
            return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CHAT_MODE_PLAN);
        }
        return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CHAT_MODE_AGENT);
    }

    private ModelMessage toModelMessage(ChatMessage message, boolean includeReasoning) {
        if (message.getRole() == ChatMessage.Role.SYSTEM) {
            return new SystemModelMessage(message.getContent());
        }
        if (message.getRole() == ChatMessage.Role.TOOL) {
            return new ToolModelMessage(
                    modelToolContent(message),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError()
            );
        }
        if (message.getRole() == ChatMessage.Role.USER) {
            return new UserModelMessage(message.getContent(), message.getResponseInputItemJson());
        }
        return new AssistantModelMessage(MessageContentSanitizer.forModel(message),
                includeReasoning ? message.getReasoningContent() : "",
                message.getToolCalls());
    }

    private String buildToolPrompt(ModelConfig selectedModel, int usedToolCallCount) {
        syncModePermission();
        if (!hasRemainingToolCalls(selectedModel, usedToolCallCount)) {
            return "## 可用工具\n当前模型的工具调用次数限制已用尽，当前没有可用工具。";
        }
        toolRegistry.reloadExtensions();
        String prompt = toolSettingsRepository.buildToolPrompt(toolRegistry.getAll(), supportsNativeTools(selectedModel));
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        if (limit == ModelConfig.UNLIMITED_TOOL_CALLS) {
            return prompt + "\n\n工具调用次数限制：不限制。";
        }
        int used = Math.max(0, usedToolCallCount);
        int remaining = Math.max(0, limit - used);
        return prompt + "\n\n工具调用次数限制：最多 " + limit + " 次；已使用 " + used + " 次；剩余 " + remaining + " 次。";
    }

    private String buildAttachmentPrompt(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int sectionCount = 0;
        for (ChatMessage message : history) {
            if (message == null || message.getRole() != ChatMessage.Role.USER || !message.hasAttachments()) {
                continue;
            }
            String label = recallText(message.getContent(), message.getAttachments()).trim();
            if (label.length() == 0) {
                label = "用户消息 " + (sectionCount + 1);
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("### ").append(label).append('\n');
            for (InputAttachment attachment : message.getAttachments()) {
                builder.append("- ")
                        .append(attachment.getName())
                        .append(" (")
                        .append(attachment.getSource())
                        .append("): ")
                        .append(attachment.getPath())
                        .append('\n');
            }
            sectionCount++;
        }
        if (sectionCount == 0) {
            return "";
        }
        return "## 附加文件位置\n"
                + "这些路径来自用户在输入框左侧选择的文件；除非用户明确要求，不要在回复中原样复述。\n"
                + builder.toString().trim();
    }

    private String joinPromptContext(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.length() == 0) {
            return right;
        }
        if (right.length() == 0) {
            return left;
        }
        return left + "\n\n" + right;
    }

    private String promptHomePath() {
        if (WorkspacePaths.SOURCE_SSH.equals(projectSource) && projectPath.length() == 0) {
            return "~";
        }
        return projectPath;
    }

    private boolean supportsNativeTools(ModelConfig selectedModel) {
        if (selectedModel == null) {
            return false;
        }
        ModelProtocolType type = selectedModel.getProtocolType();
        if (type == ModelProtocolType.OPENAI_COMPATIBLE) {
            return OpenAiCompatibleCapabilities.supportsNativeTools(selectedModel);
        }
        return type == ModelProtocolType.ANTHROPIC_MESSAGES
                || type == ModelProtocolType.CODEX_RESPONSES;
    }

    private void appendAssistantDelta(int generationId, String assistantId, String textDelta, String reasoningDelta) {
        mainThread.post(() -> {
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            if (pendingStreamGenerationId != generationId || !pendingStreamAssistantId.equals(assistantId)) {
                flushPendingAssistantDelta();
                pendingStreamGenerationId = generationId;
                pendingStreamAssistantId = assistantId;
            }
            if (textDelta != null && textDelta.length() > 0) {
                pendingStreamTextDelta.append(textDelta);
            }
            if (reasoningDelta != null && reasoningDelta.length() > 0) {
                pendingStreamReasoningDelta.append(reasoningDelta);
            }
            scheduleAssistantDeltaFlush();
        });
    }

    private void scheduleAgentProgressRender(AgentProgressSession session) {
        if (session == null || !session.shouldScheduleRender()) {
            return;
        }
        mainThread.postDelayed(() -> flushAgentProgress(session), session.renderDelayMs());
    }

    private void flushAgentProgress(AgentProgressSession session) {
        if (!mainThread.isMainThread()) {
            mainThread.post(() -> flushAgentProgress(session));
            return;
        }
        if (session == null || !session.canRender()) {
            if (session != null) {
                session.notifyMirror();
            }
            return;
        }
        if (!chatSessionStore.isActiveGeneration(session.generationId)) {
            return;
        }
        session.notifyMirror();
        addOrReplaceToolResult(session.snapshotResult());
        render();
    }

    private String modelToolContent(ChatMessage message) {
        return MessageContentSanitizer.toolContentForModel(message);
    }

    private ModelRequestOptions requestOptions(AiBehaviorSettings aiSettings, ModelConfig selectedModel, int usedToolCallCount) {
        syncModePermission();
        toolRegistry.reloadExtensions();
        Set<String> enabledToolNames = toolSettingsRepository.getEnabledToolNames(toolRegistry.getAll());
        return new ModelRequestOptions(
                aiSettings.getReasoningEffort(),
                aiSettings.isPreserveReasoningEnabled(),
                hasRemainingToolCalls(selectedModel, usedToolCallCount)
                        ? toolRegistry.getByNameSet(enabledToolNames)
                        : new ArrayList<BaseTool>()
        );
    }

    private void finishGeneration(
            int generationId,
            String assistantId,
            ModelConfig selectedModel,
            ModelRequestOptions requestOptions,
            ModelCompletionResponse response,
            int usedToolCallCount
    ) {
        mainThread.post(() -> {
            flushPendingAssistantDelta();
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index < 0) {
                return;
            }
            ChatMessage message = messages.get(index);
            String rawResponseText = response.getText();
            StringBuilder rawStream = streamingRawTextByMessageId.remove(assistantId);
            if (rawResponseText.trim().length() == 0 && rawStream != null) {
                rawResponseText = rawStream.toString();
            }
            ToolCallTextParser.Result parsedTextToolCalls = ToolCallTextParser.parse(rawResponseText);
            List<ToolCall> toolCalls = mergeToolCalls(response.getToolCalls(), parsedTextToolCalls.getToolCalls());
            String parsedResponseText = parsedTextToolCalls.hasToolMarkup() ? parsedTextToolCalls.getText() : rawResponseText;
            String finalText = parsedTextToolCalls.hasToolMarkup()
                    ? parsedResponseText
                    : parsedResponseText.trim().length() == 0 ? message.getContent() : parsedResponseText;
            String finalReasoning = response.getReasoningContent().trim().length() == 0 ? message.getReasoningContent() : response.getReasoningContent();
            boolean hasToolCalls = !toolCalls.isEmpty();
            if (finalText.trim().length() == 0 && finalReasoning.trim().length() == 0 && !hasToolCalls) {
                finalText = "模型没有返回文本。";
            }
            messages.set(index, message.withContent(finalText, finalReasoning, false)
                    .withToolCalls(toolCalls, false));
            if (hasToolCalls) {
                if (!canExecuteToolCalls(selectedModel, usedToolCallCount, toolCalls.size())) {
                    messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT,
                            toolLimitMessage(selectedModel, usedToolCallCount, toolCalls.size()), false));
                    chatSessionStore.setStreaming(false);
                    currentCancellationToken = null;
                    persistCurrentConversation();
                    render();
                    return;
                }
                persistCurrentConversation();
                render();
                executeToolsAndContinue(generationId, selectedModel, toolCalls, usedToolCallCount + toolCalls.size());
                return;
            }
            chatSessionStore.setStreaming(false);
            currentCancellationToken = null;
            persistCurrentConversation();
            scheduleMemoryExtractionIfNeeded(selectedModel);
            render();
        });
    }

    private List<ToolCall> mergeToolCalls(List<ToolCall> nativeCalls, List<ToolCall> textCalls) {
        ArrayList<ToolCall> merged = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (nativeCalls != null) {
            for (ToolCall call : nativeCalls) {
                if (call == null || call.getName().length() == 0 || seen.contains(call.getId())) {
                    continue;
                }
                merged.add(call);
                seen.add(call.getId());
            }
        }
        if (textCalls != null) {
            for (ToolCall call : textCalls) {
                if (call == null || call.getName().length() == 0 || seen.contains(call.getId())) {
                    continue;
                }
                merged.add(call);
                seen.add(call.getId());
            }
        }
        return merged;
    }

    private void scheduleMemoryExtractionIfNeeded(ModelConfig selectedModel) {
        if (!aiBehaviorSettingsRepository.get().isLearningModeEnabled() || selectedModel == null) {
            return;
        }
        String userInput = recentUserInput();
        String transcript = recentTurnTranscript();
        if (userInput.trim().length() == 0 || transcript.trim().length() == 0) {
            return;
        }
        String capturedProjectPath = projectPath;
        backgroundTasks.execute("linecode-memory-extract", () -> memoryExtractionService.extractAndStore(
                selectedModel,
                capturedProjectPath,
                userInput,
                transcript
        ));
    }

    private String recentUserInput() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                return message.getContent();
            }
        }
        return "";
    }

    private String recentTurnTranscript() {
        int start = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.isHidden() || message.isExcludeFromContext()) {
                continue;
            }
            if (message.getRole() == ChatMessage.Role.USER) {
                appendTranscriptMessage(builder, "user", message.getContent(), 1400);
            } else if (message.getRole() == ChatMessage.Role.ASSISTANT) {
                appendTranscriptMessage(builder, "assistant", message.getContent(), 2200);
            }
            if (builder.length() > 6000) {
                return builder.substring(0, 5997) + "...";
            }
        }
        return builder.toString().trim();
    }

    private void appendTranscriptMessage(StringBuilder builder, String role, String content, int maxChars) {
        String text = MessageContentSanitizer.stripInlineDataImages(content).trim();
        if (text.length() == 0) {
            return;
        }
        if (text.length() > maxChars) {
            text = text.substring(0, Math.max(0, maxChars - 3)) + "...";
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(role).append(": ").append(text);
    }

    private void executeToolsAndContinue(
            int generationId,
            ModelConfig selectedModel,
            List<ToolCall> toolCalls,
            int usedToolCallCount
    ) {
        continueToolExecution(
                generationId,
                selectedModel,
                toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls),
                usedToolCallCount,
                projectPath,
                currentCancellationToken
        );
    }

    private void continueToolExecution(
            int generationId,
            ModelConfig selectedModel,
            List<ToolCall> toolCalls,
            int usedToolCallCount,
            String homePath,
            ModelCancellationToken cancellationToken
    ) {
        backgroundTasks.execute("linecode-tool-execute", () -> {
            ToolExecutionBatch batch = executeToolCallsUntilPending(toolCalls, homePath, selectedModel, cancellationToken, generationId);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            mainThread.post(() -> {
                if (!chatSessionStore.isActiveGeneration(generationId)) {
                    return;
                }
                handleToolExecutionBatch(generationId, selectedModel, usedToolCallCount, homePath, cancellationToken, batch);
            });
        });
    }

    private void handleToolExecutionBatch(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            String homePath,
            ModelCancellationToken cancellationToken,
            ToolExecutionBatch batch
    ) {
        addOrReplaceToolResults(batch.completedResults);
        if (batch.pendingCall != null) {
            ToolResult pendingResult = new ToolResult(
                    batch.pendingCall.getId(),
                    batch.pendingCall.getName(),
                    "",
                    false,
                    "",
                    "pending",
                    ""
            );
            addOrReplaceToolResult(pendingResult);
            pendingToolExecution = new PendingToolExecution(
                    generationId,
                    selectedModel,
                    batch.pendingCall,
                    batch.remainingCalls,
                    usedToolCallCount,
                    homePath,
                    cancellationToken
            );
            persistCurrentConversation();
            render();
            return;
        }
        persistCurrentConversation();
        continueModelAfterTools(generationId, selectedModel, usedToolCallCount, cancellationToken);
    }

    private void continueModelAfterTools(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            ModelCancellationToken cancellationToken
    ) {
        ArrayList<ModelMessage> nextRequestMessages = buildModelMessages("", usedToolCallCount);
        String nextAssistantId = nextId();
        streamingRawTextByMessageId.put(nextAssistantId, new StringBuilder());
        messages.add(new ChatMessage(nextAssistantId, ChatMessage.Role.ASSISTANT, "", true));
        render();
        backgroundTasks.execute("linecode-tool-continuation", () -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions nextRequestOptions = requestOptions(aiSettings, selectedModel, usedToolCallCount);
                ModelCompletionResponse response = modelClient.stream(selectedModel, nextRequestMessages, new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        appendAssistantDelta(generationId, nextAssistantId, delta, "");
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        appendAssistantDelta(generationId, nextAssistantId, "", delta);
                    }
                }, cancellationToken, nextRequestOptions);
                finishGeneration(generationId, nextAssistantId, selectedModel, nextRequestOptions, response, usedToolCallCount);
            } catch (ModelCompletionException e) {
                failGeneration(generationId, nextAssistantId, "模型通信失败：\n" + e.getMessage());
            }
        });
    }

    private boolean canExecuteToolCalls(ModelConfig selectedModel, int usedToolCallCount, int requestedCount) {
        return generationController.canExecuteToolCalls(selectedModel, usedToolCallCount, requestedCount);
    }

    private boolean hasRemainingToolCalls(ModelConfig selectedModel, int usedToolCallCount) {
        return generationController.hasRemainingToolCalls(selectedModel, usedToolCallCount);
    }

    private String toolLimitMessage(ModelConfig selectedModel, int usedToolCallCount, int requestedCount) {
        return generationController.toolLimitMessage(selectedModel, usedToolCallCount, requestedCount);
    }

    private ToolResult executeToolCallWithSessionPolicy(ToolCall call, ToolContext context) {
        if (isSessionAutoConfirmed(call)) {
            return toolExecutor.executeConfirmed(call, context).withReview("accepted", "");
        }
        return toolExecutor.execute(call, context);
    }

    private ToolExecutionBatch executeToolCallsUntilPending(
            List<ToolCall> toolCalls,
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        syncModePermission();
        toolRegistry.reloadExtensions();
        ToolExecutionCoordinator.ToolExecutionPlan plan = toolRunController.createPlan(toolCalls);
        HashMap<String, ToolResult> resultById = new HashMap<>();
        ToolContext context = toolContext(homePath, selectedModel, cancellationToken, generationId);

        if (!plan.getConcurrentTasks().isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, plan.getConcurrentTasks().size()));
            ArrayList<ToolCall> concurrentCalls = new ArrayList<>(plan.getConcurrentTasks());
            ArrayList<Future<ToolResult>> futures = new ArrayList<>();
            for (ToolCall call : concurrentCalls) {
                futures.add(executor.submit(() -> toolExecutor.execute(call, context)));
            }
            for (int i = 0; i < futures.size(); i++) {
                ToolCall call = concurrentCalls.get(i);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    executor.shutdownNow();
                    return new ToolExecutionBatch(new ArrayList<>(), null, new ArrayList<>());
                }
                try {
                    ToolResult result = futures.get(i).get();
                    resultById.put(call.getId(), result);
                } catch (Exception e) {
                    resultById.put(call.getId(), new ToolResult(call.getId(), call.getName(), "执行失败: " + e.getMessage(), true));
                }
            }
            executor.shutdownNow();
        }

        List<ToolCall> sequentialTasks = plan.getSequentialTasks();
        for (int i = 0; i < sequentialTasks.size(); i++) {
            ToolCall call = sequentialTasks.get(i);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return new ToolExecutionBatch(new ArrayList<>(), null, new ArrayList<>());
            }
            if (shouldPauseForConfirmation(call)) {
                return new ToolExecutionBatch(
                        orderedResults(toolCalls, resultById),
                        call,
                        remainingCalls(sequentialTasks, i + 1)
                );
            }
            resultById.put(call.getId(), executeToolCallWithSessionPolicy(call, context));
        }

        return new ToolExecutionBatch(orderedResults(toolCalls, resultById), null, new ArrayList<>());
    }

    private ToolContext toolContext(
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        return new ToolContext(homePath, extensionRepository.skillWriteRoots(homePath), new ToolContext.AgentRunner() {
            @Override
            public ToolResult runAgent(JSONObject input, ToolContext context) {
                return runAgentTool(input, context, selectedModel, cancellationToken, generationId);
            }

            @Override
            public ToolResult runAgentPipeline(JSONObject input, ToolContext context) {
                return runAgentPipelineTool(input, context, selectedModel, cancellationToken, generationId);
            }
        }, "", (toolCallId, toolName, content, error) ->
                postToolProgress(generationId, cancellationToken, toolCallId, toolName, content, error));
    }

    private void postToolProgress(
            int generationId,
            ModelCancellationToken cancellationToken,
            String toolCallId,
            String toolName,
            String content,
            boolean error
    ) {
        mainThread.post(() -> {
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            if (toolCallId == null || toolCallId.length() == 0) {
                return;
            }
            addOrReplaceToolResult(new ToolResult(
                    toolCallId,
                    toolName,
                    content,
                    error,
                    "",
                    "running",
                    ""
            ));
            render();
        });
    }

    private ToolResult runAgentTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        if (selectedModel == null) {
            return new ToolResult("", "agent", "当前没有可用模型，无法运行 Agent。", true);
        }
        String type = AgentTool.normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        ArrayList<String> readScope = scopeList(input.optJSONArray("read_scope"));
        ArrayList<String> writeScope = scopeList(input.optJSONArray("write_scope"));
        String homePath = parentContext == null ? projectPath : parentContext.getHomePath();
        AgentProgressSession progress = new AgentProgressSession(
                generationId,
                parentContext == null ? "" : parentContext.getToolCallId(),
                "agent",
                type,
                description
        );
        AgentRunResult result = runAgentLoop(
                type,
                description,
                prompt,
                readScope,
                writeScope,
                homePath,
                selectedModel,
                cancellationToken,
                progress
        );
        StringBuilder builder = new StringBuilder();
        builder.append("Agent 完成: ").append(description).append('\n')
                .append("类型: ").append(type).append('\n')
                .append("工具调用: ").append(result.toolCallCount).append('\n')
                .append("输出:\n").append(result.output);
        progress.setTurnResult(result.output, "");
        progress.setFinished(result.error ? "error" : "done", result.error, builder.toString().trim());
        flushAgentProgress(progress);
        return new ToolResult("", "agent", progress.snapshotResult().getContent(), result.error);
    }

    private ToolResult runAgentPipelineTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        if (selectedModel == null) {
            return new ToolResult("", "agent_pipeline", "当前没有可用模型，无法运行 Agent 流水线。", true);
        }
        ArrayList<PipelineAgent> agents = parsePipelineAgents(input.optJSONArray("agents"));
        if (agents.isEmpty()) {
            return new ToolResult("", "agent_pipeline", "agent_pipeline.agents 不能为空。", true);
        }
        String dependencyError = validatePipelineDependencies(agents);
        if (dependencyError.length() > 0) {
            return new ToolResult("", "agent_pipeline", dependencyError, true);
        }
        ArrayList<ArrayList<PipelineAgent>> levels = dependencyLevels(agents);
        if (levels.isEmpty()) {
            return new ToolResult("", "agent_pipeline", "Agent 流水线存在循环依赖或重复 id，无法执行。", true);
        }

        LinkedHashMap<String, AgentRunResult> results = new LinkedHashMap<>();
        StringBuilder summary = new StringBuilder();
        summary.append("Agent 流水线完成: ").append(agents.size()).append(" 个任务");
        PipelineProgressSession pipelineProgress = new PipelineProgressSession(parentContext, agents);
        pipelineProgress.publish(false);
        boolean hasError = false;
        int totalToolCalls = 0;
        for (ArrayList<PipelineAgent> level : levels) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                pipelineProgress.terminate();
                return new ToolResult("", "agent_pipeline", "Agent 流水线已终止。", true);
            }
            for (PipelineAgent agent : level) {
                String prompt = agent.prompt + dependencyOutputContext(agent, results);
                pipelineProgress.beginAgent(agent);
                AgentProgressSession agentProgress = new AgentProgressSession(
                        generationId,
                        "",
                        "agent",
                        agent.type,
                        agent.description,
                        (payload, progressError) -> pipelineProgress.updateAgent(agent, payload, progressError)
                );
                AgentRunResult result = runAgentLoop(
                        agent.type,
                        agent.description,
                        prompt,
                        agent.readScope,
                        agent.writeScope,
                        parentContext == null ? projectPath : parentContext.getHomePath(),
                        selectedModel,
                        cancellationToken,
                        agentProgress
                );
                pipelineProgress.finishAgent(agent, result);
                results.put(agent.id, result);
                totalToolCalls += result.toolCallCount;
                hasError = hasError || result.error;
                summary.append("\n\n## ").append(agent.id).append(" · ").append(agent.description)
                        .append('\n').append("类型: ").append(agent.type)
                        .append('\n').append("状态: ").append(result.error ? "error" : "done")
                        .append('\n').append("工具调用: ").append(result.toolCallCount)
                        .append('\n').append(result.output);
            }
        }
        summary.append("\n\n总工具调用: ").append(totalToolCalls);
        return new ToolResult("", "agent_pipeline", summary.toString().trim(), hasError);
    }

    private AgentRunResult runAgentLoop(
            String type,
            String description,
            String prompt,
            List<String> readScope,
            List<String> writeScope,
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            AgentProgressSession progress
    ) {
        ArrayList<BaseTool> agentTools = agentTools(type);
        Set<String> allowedToolNames = toolNames(agentTools);
        ArrayList<ModelMessage> agentMessages = new ArrayList<>();
        agentMessages.add(new SystemModelMessage(agentSystemPrompt(type, description, readScope, writeScope, homePath, selectedModel, agentTools)));
        agentMessages.add(new UserModelMessage(prompt));
        int toolCallCount = 0;
        String lastOutput = "";

        for (int turn = 0; turn < AGENT_MAX_TURNS; turn++) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                if (progress != null) {
                    progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                    flushAgentProgress(progress);
                }
                return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
            }
            try {
                if (progress != null) {
                    progress.beginTurn();
                    scheduleAgentProgressRender(progress);
                }
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                List<BaseTool> nativeTools = supportsNativeTools(selectedModel) ? agentTools : new ArrayList<>();
                ModelRequestOptions options = new ModelRequestOptions(
                        aiSettings.getReasoningEffort(),
                        aiSettings.isPreserveReasoningEnabled(),
                        nativeTools
                );
                ModelStreamCallback callback = progress == null ? null : new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        progress.appendText(delta);
                        scheduleAgentProgressRender(progress);
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        progress.appendThinking(delta);
                        scheduleAgentProgressRender(progress);
                    }
                };
                ModelCompletionResponse response = modelClient.stream(selectedModel, agentMessages, callback, cancellationToken, options);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    if (progress != null) {
                        progress.setTurnResult(AGENT_TERMINATED_MESSAGE, response.getReasoningContent());
                        progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                        flushAgentProgress(progress);
                    }
                    return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
                }
                ToolCallTextParser.Result parsedTextToolCalls = ToolCallTextParser.parse(response.getText());
                List<ToolCall> calls = mergeToolCalls(response.getToolCalls(), parsedTextToolCalls.getToolCalls());
                String output = parsedTextToolCalls.hasToolMarkup() ? parsedTextToolCalls.getText() : response.getText();
                if (progress != null) {
                    progress.setTurnResult(output, response.getReasoningContent());
                    progress.addToolCalls(calls);
                    flushAgentProgress(progress);
                }
                if (output.trim().length() > 0) {
                    lastOutput = output;
                }
                if (calls.isEmpty()) {
                    String finalOutput = lastOutput.trim().length() == 0 ? "Agent 没有返回文本。" : lastOutput;
                    return new AgentRunResult(finalOutput, toolCallCount, false);
                }

                agentMessages.add(new AssistantModelMessage(output, response.getReasoningContent(), calls));
                for (ToolCall call : calls) {
                    if (cancellationToken != null && cancellationToken.isCancelled()) {
                        if (progress != null) {
                            progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                            flushAgentProgress(progress);
                        }
                        return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
                    }
                    ToolResult toolResult = executeAgentToolCall(call, allowedToolNames, type, writeScope, homePath);
                    toolCallCount++;
                    if (progress != null) {
                        progress.putToolResult(call, toolResult);
                        flushAgentProgress(progress);
                    }
                    agentMessages.add(new ToolModelMessage(
                            toolResult.getContent(),
                            toolResult.getToolCallId(),
                            toolResult.getToolName(),
                            toolResult.isError()
                    ));
                }
            } catch (ModelCompletionException e) {
                if (progress != null) {
                    progress.setFinished("error", true, "Agent 模型通信失败：\n" + e.getMessage());
                    flushAgentProgress(progress);
                }
                return new AgentRunResult("Agent 模型通信失败：\n" + e.getMessage(), toolCallCount, true);
            } catch (Exception e) {
                if (progress != null) {
                    progress.setFinished("error", true, "Agent 执行失败：\n" + e.getMessage());
                    flushAgentProgress(progress);
                }
                return new AgentRunResult("Agent 执行失败：\n" + e.getMessage(), toolCallCount, true);
            }
        }
        if (progress != null) {
            progress.setFinished("error", true, "Agent 达到最大轮次 " + AGENT_MAX_TURNS + "，最后输出：\n" + lastOutput);
            flushAgentProgress(progress);
        }
        return new AgentRunResult("Agent 达到最大轮次 " + AGENT_MAX_TURNS + "，最后输出：\n" + lastOutput, toolCallCount, true);
    }

    private ArrayList<BaseTool> agentTools(String type) {
        syncModePermission();
        ArrayList<BaseTool> tools = new ArrayList<>();
        Set<String> enabled = toolSettingsRepository.getEnabledToolNames();
        for (BaseTool tool : toolRegistry.getByNameSet(enabled)) {
            if (tool == null || !isAgentToolAllowed(tool, type)) {
                continue;
            }
            tools.add(tool);
        }
        return tools;
    }

    private boolean isAgentToolAllowed(BaseTool tool, String type) {
        String name = tool.getName();
        if ("agent".equals(name) || "agent_pipeline".equals(name) || "shell_execute".equals(name) || "file_delete".equals(name)) {
            return false;
        }
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return tool.getCategory() == ToolCategory.READ;
        }
        return tool.getCategory() == ToolCategory.READ
                || tool.getCategory() == ToolCategory.WRITE
                || "http_server".equals(name);
    }

    private Set<String> toolNames(List<BaseTool> tools) {
        HashSet<String> names = new HashSet<>();
        if (tools == null) {
            return names;
        }
        for (BaseTool tool : tools) {
            if (tool != null) {
                names.add(tool.getName());
            }
        }
        return names;
    }

    private ToolResult executeAgentToolCall(
            ToolCall call,
            Set<String> allowedToolNames,
            String type,
            List<String> writeScope,
            String homePath
    ) {
        syncModePermission();
        if (call == null) {
            return new ToolResult("", "", "Agent 工具调用为空", true);
        }
        if (!allowedToolNames.contains(call.getName())) {
            return new ToolResult(call.getId(), call.getName(), "Agent 不允许调用此工具: " + call.getName(), true);
        }
        ToolContext context = new ToolContext(homePath, extensionRepository.skillWriteRoots(homePath), null, "", null);
        ToolResult scopeError = validateAgentWriteScope(call, type, writeScope, context);
        if (scopeError != null) {
            return scopeError;
        }
        return toolExecutor.execute(call, context);
    }

    private String agentSystemPrompt(
            String type,
            String description,
            List<String> readScope,
            List<String> writeScope,
            String homePath,
            ModelConfig selectedModel,
            List<BaseTool> agentTools
    ) {
        syncModePermission();
        return agentRolePrompt(type)
                + "\n\n你的任务: " + description
                + "\n\n" + agentWorkspacePrompt(homePath)
                + "\n\n" + agentScopePrompt(type, readScope, writeScope)
                + "\n\n" + extensionRepository.buildExtensionPrompt(homePath)
                + "\n\n" + toolSettingsRepository.buildToolPrompt(agentTools, supportsNativeTools(selectedModel));
    }

    private String agentRolePrompt(String type) {
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return "你是一个代码探索 Agent。你的任务是快速定位和分析代码，回答用户的问题。\n"
                    + "规则：\n"
                    + "- 只读取代码，不做任何修改，不调用任何写入类工具。\n"
                    + "- 优先使用只读工具搜索和读取关键文件。\n"
                    + "- 给出简洁准确的中文回答，并标注文件路径和必要的行号。";
        }
        return "你是一个编程 Agent。你的任务是完成边界清晰的编程子任务。\n"
                + "规则：\n"
                + "- 只负责用户明确分派给你的任务区域，不要修改无关文件。\n"
                + "- 只能修改 write_scope 中列出的文件或目录；没有 write_scope 时禁止写入文件，只能汇报需要主模型重新分配。\n"
                + "- 如果发现必须修改 write_scope 外的文件，停止写入并在输出中说明需要扩大或重新分配范围。\n"
                + "- 修改前先读取目标文件，完成后做最小可行验证。\n"
                + "- 如果工具失败，先重新读取和分析，不要盲目重复。\n"
                + "- 用中文总结完成内容、验证结果和剩余风险。";
    }

    private String agentWorkspacePrompt(String homePath) {
        String path = homePath == null || homePath.trim().length() == 0 ? promptHomePath() : homePath.trim();
        return "当前工作区: " + path + "\n"
                + "所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 API key、token、密码等敏感数据。";
    }

    private String agentScopePrompt(String type, List<String> readScope, List<String> writeScope) {
        StringBuilder builder = new StringBuilder();
        builder.append("## Agent 范围\n");
        builder.append("read_scope: ").append(scopeSummary(readScope)).append('\n');
        builder.append("write_scope: ").append(scopeSummary(writeScope)).append('\n');
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            builder.append("这是 explore Agent，write_scope 必须视为无效，禁止任何写入。");
        } else if (writeScope == null || writeScope.isEmpty()) {
            builder.append("没有授权写入范围。禁止写入文件；如果任务需要修改文件，直接说明需要主模型重新分配 write_scope。");
        } else {
            builder.append("只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把多个 Agent 的职责混到同一个文件里。");
        }
        return builder.toString();
    }

    private ToolResult validateAgentWriteScope(
            ToolCall call,
            String type,
            List<String> writeScope,
            ToolContext context
    ) {
        if (call == null || !isFileWriteTool(call.getName())) {
            return null;
        }
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return new ToolResult(call.getId(), call.getName(), "explore Agent 不允许写入文件。", true);
        }
        if (writeScope == null || writeScope.isEmpty()) {
            return new ToolResult(call.getId(), call.getName(),
                    "Agent 未声明 write_scope，禁止写入文件。请让主模型重新分配明确的写入范围。", true);
        }
        try {
            JSONObject arguments = call.getArguments().trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(call.getArguments());
            String filePath = arguments.optString("file_path").trim();
            if (filePath.length() == 0) {
                return null;
            }
            File target = FileToolPathPolicy.resolve(context, filePath);
            for (String scope : writeScope) {
                if (scope == null || scope.trim().length() == 0) {
                    continue;
                }
                File allowed = FileToolPathPolicy.resolve(context, scope);
                if (isInsidePath(allowed, target)) {
                    return null;
                }
            }
            return new ToolResult(call.getId(), call.getName(),
                    "Agent 写入路径超出 write_scope: " + filePath
                            + "\n允许写入范围: " + scopeSummary(writeScope)
                            + "\n请停止写入并让主模型重新分配。", true);
        } catch (Exception e) {
            return new ToolResult(call.getId(), call.getName(), "Agent 写入范围检查失败: " + e.getMessage(), true);
        }
    }

    private boolean isFileWriteTool(String name) {
        return "file_write".equals(name) || "file_edit".equals(name);
    }

    private boolean isInsidePath(File root, File target) throws java.io.IOException {
        File canonicalRoot = root.getCanonicalFile();
        File canonicalTarget = target.getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String targetPath = canonicalTarget.getPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
    }

    private String scopeSummary(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "未声明";
        }
        StringBuilder builder = new StringBuilder();
        for (String scope : scopes) {
            if (scope == null || scope.trim().length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(scope.trim());
        }
        return builder.length() == 0 ? "未声明" : builder.toString();
    }

    private ArrayList<PipelineAgent> parsePipelineAgents(JSONArray array) {
        ArrayList<PipelineAgent> agents = new ArrayList<>();
        if (array == null) {
            return agents;
        }
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                return new ArrayList<>();
            }
            String id = object.optString("id").trim();
            if (id.length() == 0 || ids.contains(id)) {
                return new ArrayList<>();
            }
            ids.add(id);
            agents.add(new PipelineAgent(
                    id,
                    AgentTool.normalizeType(object.optString("type")),
                    object.optString("description").trim(),
                    object.optString("prompt").trim(),
                    scopeList(object.optJSONArray("read_scope")),
                    scopeList(object.optJSONArray("write_scope")),
                    dependencyList(object.optJSONArray("depends_on"))
            ));
        }
        return agents;
    }

    private String validatePipelineDependencies(ArrayList<PipelineAgent> agents) {
        if (agents == null) {
            return "agent_pipeline.agents 不能为空。";
        }
        for (PipelineAgent agent : agents) {
            for (String dependency : agent.dependencies) {
                if (agent.id.equals(dependency)) {
                    return "Agent 不能依赖自身: " + agent.id;
                }
            }
        }
        return "";
    }

    private ArrayList<String> scopeList(JSONArray array) {
        ArrayList<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    private ArrayList<String> dependencyList(JSONArray array) {
        ArrayList<String> dependencies = new ArrayList<>();
        if (array == null) {
            return dependencies;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                dependencies.add(value);
            }
        }
        return dependencies;
    }

    private ArrayList<ArrayList<PipelineAgent>> dependencyLevels(ArrayList<PipelineAgent> agents) {
        ArrayList<ArrayList<PipelineAgent>> levels = new ArrayList<>();
        HashSet<String> allIds = new HashSet<>();
        for (PipelineAgent agent : agents) {
            allIds.add(agent.id);
        }
        for (PipelineAgent agent : agents) {
            for (String dependency : agent.dependencies) {
                if (!allIds.contains(dependency)) {
                    return new ArrayList<>();
                }
            }
        }

        HashSet<String> completed = new HashSet<>();
        while (completed.size() < agents.size()) {
            ArrayList<PipelineAgent> level = new ArrayList<>();
            for (PipelineAgent agent : agents) {
                if (completed.contains(agent.id)) {
                    continue;
                }
                if (completed.containsAll(agent.dependencies)) {
                    level.add(agent);
                }
            }
            if (level.isEmpty()) {
                return new ArrayList<>();
            }
            for (PipelineAgent agent : level) {
                completed.add(agent.id);
            }
            levels.add(level);
        }
        return levels;
    }

    private String dependencyOutputContext(PipelineAgent agent, LinkedHashMap<String, AgentRunResult> results) {
        if (agent.dependencies.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n## 上游 Agent 输出\n");
        for (String dependency : agent.dependencies) {
            AgentRunResult result = results.get(dependency);
            if (result == null) {
                continue;
            }
            builder.append("\n### ").append(dependency).append('\n').append(result.output).append('\n');
        }
        builder.append("\n请基于以上结果继续你的任务。");
        return builder.toString();
    }

    private ArrayList<ToolResult> orderedResults(List<ToolCall> toolCalls, HashMap<String, ToolResult> resultById) {
        return toolRunController.orderedResults(toolCalls, resultById);
    }

    private ArrayList<ToolCall> remainingCalls(List<ToolCall> calls, int startIndex) {
        return toolRunController.remainingCalls(calls, startIndex);
    }

    private boolean shouldPauseForConfirmation(ToolCall call) {
        syncModePermission();
        if (isSessionAutoConfirmed(call)) {
            return false;
        }
        return toolRunController.shouldPauseForConfirmation(call);
    }

    private boolean isSessionAutoReview(String state, ToolCall call) {
        return TOOL_REVIEW_SESSION_AUTO.equals(state)
                && call != null
                && SHELL_EXECUTE_TOOL.equals(call.getName());
    }

    private void rememberSessionAutoConfirmation(ToolCall call) {
        if (call == null || !SHELL_EXECUTE_TOOL.equals(call.getName())) {
            return;
        }
        synchronized (sessionAutoConfirmedTools) {
            syncSessionAutoToolConfirmationsLocked();
            sessionAutoConfirmedTools.add(call.getName());
        }
    }

    private boolean isSessionAutoConfirmed(ToolCall call) {
        if (call == null) {
            return false;
        }
        synchronized (sessionAutoConfirmedTools) {
            syncSessionAutoToolConfirmationsLocked();
            return sessionAutoConfirmedTools.contains(call.getName());
        }
    }

    private void clearSessionAutoToolConfirmations() {
        synchronized (sessionAutoConfirmedTools) {
            sessionAutoConfirmedTools.clear();
            sessionAutoConfirmedConversationId = chatSessionStore.getCurrentConversationId();
        }
    }

    private void syncSessionAutoToolConfirmationsLocked() {
        String conversationId = chatSessionStore.getCurrentConversationId();
        if (!conversationId.equals(sessionAutoConfirmedConversationId)) {
            sessionAutoConfirmedTools.clear();
            sessionAutoConfirmedConversationId = conversationId;
        }
    }

    private void handlePendingToolReview(String state) {
        PendingToolExecution pending = pendingToolExecution;
        if (pending == null || pending.toolCall == null) {
            return;
        }
        if (!chatSessionStore.isActiveGeneration(pending.generationId)) {
            pendingToolExecution = null;
            return;
        }
        boolean sessionAutoAccepted = isSessionAutoReview(state, pending.toolCall);
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        pendingToolExecution = null;
        if ("rejected".equals(normalizedState)) {
            ToolResult rejected = new ToolResult(
                    pending.toolCall.getId(),
                    pending.toolCall.getName(),
                    rejectedToolMessage(pending.toolCall),
                    true,
                    "",
                    "rejected",
                    ""
            );
            addOrReplaceToolResult(rejected);
            persistCurrentConversation();
            render();
            continueToolExecution(
                    pending.generationId,
                    pending.selectedModel,
                    pending.remainingCalls,
                    pending.usedToolCallCount,
                    pending.homePath,
                    pending.cancellationToken
            );
            return;
        }
        if (sessionAutoAccepted) {
            rememberSessionAutoConfirmation(pending.toolCall);
        }

        ToolResult accepted = new ToolResult(
                pending.toolCall.getId(),
                pending.toolCall.getName(),
                "",
                false,
                "",
                "accepted",
                ""
        );
        addOrReplaceToolResult(accepted);
        persistCurrentConversation();
        render();
        executeAcceptedPendingTool(pending);
    }

    private void executeAcceptedPendingTool(PendingToolExecution pending) {
        backgroundTasks.execute("linecode-tool-confirmed", () -> {
            ToolResult result;
            try {
                syncModePermission();
                toolRegistry.reloadExtensions();
                result = toolExecutor
                        .executeConfirmed(pending.toolCall, toolContext(
                                pending.homePath,
                                pending.selectedModel,
                                pending.cancellationToken,
                                pending.generationId
                        ))
                        .withReview("accepted", "");
            } catch (Exception e) {
                result = new ToolResult(
                        pending.toolCall.getId(),
                        pending.toolCall.getName(),
                        "执行失败: " + e.getMessage(),
                        true,
                        "",
                        "accepted",
                        ""
                );
            }
            ToolResult finalResult = result;
            if (pending.cancellationToken != null && pending.cancellationToken.isCancelled()) {
                return;
            }
            mainThread.post(() -> {
                if (!chatSessionStore.isActiveGeneration(pending.generationId)) {
                    return;
                }
                addOrReplaceToolResult(finalResult);
                persistCurrentConversation();
                render();
                continueToolExecution(
                        pending.generationId,
                        pending.selectedModel,
                        pending.remainingCalls,
                        pending.usedToolCallCount,
                        pending.homePath,
                        pending.cancellationToken
                );
            });
        });
    }

    private void addOrReplaceToolResults(List<ToolResult> results) {
        if (results == null) {
            return;
        }
        for (ToolResult result : results) {
            addOrReplaceToolResult(result);
        }
    }

    private void addOrReplaceToolResult(ToolResult result) {
        if (result == null || result.getToolCallId().length() == 0) {
            return;
        }
        int index = findToolMessageIndex(result.getToolCallId());
        String messageId = index >= 0 ? messages.get(index).getId() : nextId();
        ChatMessage message = ChatMessage.toolResult(
                messageId,
                result.getContent(),
                result.getToolCallId(),
                result.getToolName(),
                result.isError(),
                result.getDiffId(),
                result.getReviewState(),
                result.getReviewMessage()
        );
        if (index >= 0) {
            messages.set(index, message);
        } else {
            messages.add(message);
        }
        appendInlineToolResultToAssistant(result);
    }

    private void appendInlineToolResultToAssistant(ToolResult result) {
        if (!isFinalSuccessfulImageGenerationResult(result)) {
            return;
        }
        String markdown = imageGenerationDisplayMarkdown(result.getContent());
        if (markdown.length() == 0) {
            return;
        }
        int assistantIndex = findAssistantMessageIndexForToolCall(result.getToolCallId());
        if (assistantIndex < 0) {
            return;
        }
        ChatMessage assistant = messages.get(assistantIndex);
        if (assistant.getContent().contains(markdown)) {
            return;
        }
        String current = assistant.getContent().trim();
        String nextContent = current.length() == 0 ? markdown : current + "\n\n" + markdown;
        messages.set(assistantIndex, assistant.withContent(nextContent, assistant.getReasoningContent(), assistant.isStreaming()));
    }

    private boolean isFinalSuccessfulImageGenerationResult(ToolResult result) {
        return result != null
                && "image_generation".equals(result.getToolName())
                && !result.isError()
                && result.getContent().trim().length() > 0
                && result.getReviewState().length() == 0;
    }

    private String imageGenerationDisplayMarkdown(String content) {
        return MessageContentSanitizer.imageGenerationDisplayMarkdown(content);
    }

    private int findToolMessageIndex(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                return i;
            }
        }
        return -1;
    }

    private int findAssistantMessageIndexForToolCall(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return -1;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != ChatMessage.Role.ASSISTANT || !message.hasToolCalls()) {
                continue;
            }
            for (ToolCall call : message.getToolCalls()) {
                if (toolCallId.equals(call.getId())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String rejectedToolMessage(ToolCall call) {
        String reason = "";
        try {
            JSONObject input = call.getArguments().trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(call.getArguments());
            reason = input.optString("reason").trim();
        } catch (Exception ignored) {
            reason = "";
        }
        if (reason.length() == 0) {
            return "用户拒绝执行此工具。";
        }
        return "用户拒绝删除：" + reason;
    }

    private void failGeneration(int generationId, String assistantId, String text) {
        mainThread.post(() -> {
            flushPendingAssistantDelta();
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index >= 0) {
                ChatMessage message = messages.get(index);
                messages.set(index, message.withContent(text, message.getReasoningContent(), false));
            } else {
                messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
            }
            streamingRawTextByMessageId.remove(assistantId);
            chatSessionStore.setStreaming(false);
            currentCancellationToken = null;
            persistCurrentConversation();
            render();
        });
    }

    private void cancelActiveGeneration() {
        pendingToolExecution = null;
        if (currentCancellationToken != null) {
            currentCancellationToken.cancel();
            currentCancellationToken = null;
        }
    }

    private void scheduleAssistantDeltaFlush() {
        if (streamRenderScheduled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delay = Math.max(0L, STREAM_RENDER_INTERVAL_MS - (now - lastStreamRenderAt));
        streamRenderScheduled = true;
        mainThread.postDelayed(this::flushPendingAssistantDelta, delay);
    }

    private void flushPendingAssistantDelta() {
        streamRenderScheduled = false;
        if (pendingStreamTextDelta.length() == 0 && pendingStreamReasoningDelta.length() == 0) {
            return;
        }
        int generationId = pendingStreamGenerationId;
        String assistantId = pendingStreamAssistantId;
        String textDelta = pendingStreamTextDelta.toString();
        String reasoningDelta = pendingStreamReasoningDelta.toString();
        pendingStreamTextDelta.setLength(0);
        pendingStreamReasoningDelta.setLength(0);
        pendingStreamGenerationId = -1;
        pendingStreamAssistantId = "";
        if (!chatSessionStore.isActiveGeneration(generationId)) {
            return;
        }
        int index = findMessageIndex(assistantId);
        if (index < 0) {
            return;
        }
        ChatMessage message = messages.get(index);
        StringBuilder rawText = streamingRawTextByMessageId.get(assistantId);
        if (rawText == null) {
            rawText = new StringBuilder(message.getContent());
            streamingRawTextByMessageId.put(assistantId, rawText);
        }
        rawText.append(textDelta);
        ToolCallTextParser.Result parsedToolCalls = ToolCallTextParser.parseStreamingPreview(rawText.toString());
        String visibleText = parsedToolCalls.hasToolMarkup()
                ? parsedToolCalls.getText()
                : message.getContent() + textDelta;
        List<ToolCall> toolCalls = parsedToolCalls.hasToolMarkup()
                ? mergeToolCalls(message.getToolCalls(), parsedToolCalls.getToolCalls())
                : message.getToolCalls();
        messages.set(index, message.withContent(
                visibleText,
                message.getReasoningContent() + reasoningDelta,
                true
        ).withToolCalls(toolCalls, false));
        lastStreamRenderAt = SystemClock.uptimeMillis();
        render();
    }

    private void markStreamingMessagesStopped() {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.isStreaming()) {
                if (message.isCompactBlock()) {
                    messages.set(i, message.withCompactStatus(ChatMessage.COMPACT_STATUS_ERROR, false));
                } else {
                    messages.set(i, message.withContent(message.getContent(), message.getReasoningContent(), false));
                }
            }
        }
    }

    private void markRunningAgentProgressStopped() {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != ChatMessage.Role.TOOL) {
                continue;
            }
            try {
                JSONObject object = new JSONObject(message.getContent());
                if (!object.optBoolean("linecode_agent_progress")) {
                    continue;
                }
                String status = object.optString("status");
                if (!"running".equals(status) && !"waiting_unlock".equals(status)) {
                    continue;
                }
                object.put("status", "error");
                String output = object.optString("output").trim();
                if (output.length() == 0) {
                    object.put("output", AGENT_TERMINATED_MESSAGE);
                } else if (!output.contains(AGENT_TERMINATED_MESSAGE)) {
                    object.put("output", output + "\n\n" + AGENT_TERMINATED_MESSAGE);
                }
                object.put("model_content", AGENT_TERMINATED_MESSAGE);
                messages.set(i, new ChatMessage(
                        message.getId(),
                        ChatMessage.Role.TOOL,
                        object.toString(),
                        message.getReasoningContent(),
                        false,
                        message.isHidden(),
                        message.isExcludeFromContext(),
                        message.getToolCalls(),
                        message.getToolResults(),
                        message.getToolCallId(),
                        message.getToolName(),
                        true,
                        message.getDiffId(),
                        message.getReviewState(),
                        message.getReviewMessage()
                ));
            } catch (Exception ignored) {
            }
        }
        addTerminatedResultsForUnfinishedAgents();
    }

    private void addTerminatedResultsForUnfinishedAgents() {
        ArrayList<ToolResult> terminatedResults = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.getRole() != ChatMessage.Role.ASSISTANT || !message.hasToolCalls()) {
                continue;
            }
            for (ToolCall call : message.getToolCalls()) {
                if (call == null || findToolMessageIndex(call.getId()) >= 0) {
                    continue;
                }
                if ("agent".equals(call.getName()) || "agent_pipeline".equals(call.getName())) {
                    terminatedResults.add(new ToolResult(call.getId(), call.getName(), AGENT_TERMINATED_MESSAGE, true));
                }
            }
        }
        addOrReplaceToolResults(terminatedResults);
    }

    private int findMessageIndex(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private String findToolMessageDiffId(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return "";
        }
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.TOOL
                    && toolCallId.equals(message.getToolCallId())
                    && message.getDiffId().length() > 0) {
                return message.getDiffId();
            }
            if (message.getRole() == ChatMessage.Role.TOOL) {
                String nestedDiffId = findNestedToolDiffId(message.getContent(), toolCallId);
                if (nestedDiffId.length() > 0) {
                    return nestedDiffId;
                }
            }
        }
        return "";
    }

    private void updateToolReview(String toolCallId, String diffId, String reviewState, String reviewMessage) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            ChatMessage next = message;
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                String resolvedDiffId = diffId == null || diffId.length() == 0 ? message.getDiffId() : diffId;
                next = next.withToolReview(resolvedDiffId, reviewState, reviewMessage);
            }
            if (message.getRole() == ChatMessage.Role.TOOL) {
                String updatedContent = updateNestedToolReviewContent(
                        next.getContent(),
                        toolCallId,
                        diffId,
                        reviewState,
                        reviewMessage
                );
                if (!updatedContent.equals(next.getContent())) {
                    next = next.withContent(updatedContent, next.getReasoningContent(), next.isStreaming());
                }
            }
            if (next != message) {
                messages.set(i, next);
            }
        }
    }

    private String findNestedToolDiffId(String content, String toolCallId) {
        if (content == null || content.trim().length() == 0) {
            return "";
        }
        try {
            return findNestedToolDiffId(new JSONObject(content), toolCallId);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String findNestedToolDiffId(JSONObject object, String toolCallId) {
        if (object == null) {
            return "";
        }
        String diffId = findToolCallArrayDiffId(object.optJSONArray("tool_calls"), toolCallId);
        if (diffId.length() > 0) {
            return diffId;
        }
        JSONArray agents = object.optJSONArray("agents");
        if (agents == null) {
            return "";
        }
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            diffId = findToolCallArrayDiffId(agent == null ? null : agent.optJSONArray("tool_calls"), toolCallId);
            if (diffId.length() > 0) {
                return diffId;
            }
        }
        return "";
    }

    private String findToolCallArrayDiffId(JSONArray calls, String toolCallId) {
        if (calls == null) {
            return "";
        }
        for (int i = 0; i < calls.length(); i++) {
            JSONObject item = calls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (toolCallId.equals(item.optString("id"))) {
                JSONObject result = item.optJSONObject("result");
                return result == null ? "" : result.optString("diff_id");
            }
            String diffId = findNestedToolDiffId(item, toolCallId);
            if (diffId.length() > 0) {
                return diffId;
            }
        }
        return "";
    }

    private String updateNestedToolReviewContent(
            String content,
            String toolCallId,
            String diffId,
            String reviewState,
            String reviewMessage
    ) {
        if (content == null || content.trim().length() == 0) {
            return content == null ? "" : content;
        }
        try {
            JSONObject object = new JSONObject(content);
            return updateNestedToolReview(object, toolCallId, diffId, reviewState, reviewMessage)
                    ? object.toString()
                    : content;
        } catch (Exception ignored) {
            return content;
        }
    }

    private boolean updateNestedToolReview(
            JSONObject object,
            String toolCallId,
            String diffId,
            String reviewState,
            String reviewMessage
    ) throws Exception {
        if (object == null) {
            return false;
        }
        boolean changed = updateToolCallArrayReview(
                object.optJSONArray("tool_calls"),
                toolCallId,
                diffId,
                reviewState,
                reviewMessage
        );
        JSONArray agents = object.optJSONArray("agents");
        if (agents != null) {
            for (int i = 0; i < agents.length(); i++) {
                JSONObject agent = agents.optJSONObject(i);
                if (agent == null) {
                    continue;
                }
                changed = updateToolCallArrayReview(
                        agent.optJSONArray("tool_calls"),
                        toolCallId,
                        diffId,
                        reviewState,
                        reviewMessage
                ) || changed;
            }
        }
        return changed;
    }

    private boolean updateToolCallArrayReview(
            JSONArray calls,
            String toolCallId,
            String diffId,
            String reviewState,
            String reviewMessage
    ) throws Exception {
        if (calls == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < calls.length(); i++) {
            JSONObject item = calls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (toolCallId.equals(item.optString("id"))) {
                JSONObject result = item.optJSONObject("result");
                if (result == null) {
                    result = new JSONObject();
                    item.put("result", result);
                }
                String resolvedDiffId = diffId == null || diffId.length() == 0
                        ? result.optString("diff_id")
                        : diffId;
                result.put("diff_id", resolvedDiffId == null ? "" : resolvedDiffId);
                result.put("review_state", reviewState == null ? "" : reviewState);
                result.put("review_message", reviewMessage == null ? "" : reviewMessage);
                changed = true;
            }
            changed = updateNestedToolReview(item, toolCallId, diffId, reviewState, reviewMessage) || changed;
        }
        return changed;
    }

    private void refreshFileTreeAfterRevert(String filePath) {
        String parentPath = parentPath(filePath);
        if (parentPath.length() > 0) {
            expandedFilePaths.add(parentPath);
        }
        if (isSshExecutionMode()) {
            sshFileTreeController.refreshDirectoryAfterFileOperation(parentPath);
        }
    }

    private void loadCurrentConversation() {
        ConversationRecord conversation = conversationRepository.getCurrentConversation();
        if (conversation != null) {
            applyConversation(conversation);
        }
    }

    private void loadConversation(String id) {
        ConversationRecord conversation = conversationRepository.getConversation(id);
        if (conversation == null) {
            return;
        }
        applyConversation(conversation);
        conversationRepository.setCurrentConversationId(conversation.getId());
        lastMessageModelId = "";
    }

    private void applyConversation(ConversationRecord conversation) {
        chatSessionStore.applyConversation(conversation);
    }

    private void ensureCurrentConversation() {
        chatSessionStore.ensureCurrentConversation(System.currentTimeMillis());
    }

    private void persistCurrentConversation() {
        String currentConversationId = chatSessionStore.getCurrentConversationId();
        if (currentConversationId.length() == 0) {
            return;
        }
        if (messages.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ArrayList<MessageRecord> records = new ArrayList<>();
        for (ChatMessage message : messages) {
            records.add(new MessageRecord(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getReasoningContent(),
                    now,
                    false,
                    message.isHidden(),
                    message.isExcludeFromContext(),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError(),
                    messageRawJson(message)
            ));
        }
        ConversationRecord conversation = new ConversationRecord(
                currentConversationId,
                deriveTitle(),
                projectPath,
                chatSessionStore.getCurrentConversationCreatedAt() > 0 ? chatSessionStore.getCurrentConversationCreatedAt() : now,
                now,
                true,
                "",
                records
        );
        conversationRepository.saveConversation(conversation);
        if (aiBehaviorSettingsRepository.get().isLearningModeEnabled()) {
            learningContextRepository.indexConversation(projectPath, conversation);
        }
    }

    private String deriveTitle() {
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                String firstLine = message.getContent().trim().replace('\n', ' ');
                return firstLine.length() > 28 ? firstLine.substring(0, 28) + "..." : firstLine;
            }
        }
        return "新对话";
    }

    private String nextId() {
        return chatSessionStore.nextMessageId();
    }

    private String messageRawJson(ChatMessage message) {
        if (message == null) {
            return "";
        }
        try {
            JSONObject object = new JSONObject();
            if (message.getRole() == ChatMessage.Role.TOOL) {
                object.put("diff_id", message.getDiffId());
                object.put("review_state", message.getReviewState());
                object.put("review_message", message.getReviewMessage());
            }
            if (message.hasToolCalls()) {
                JSONArray array = new JSONArray();
                for (ToolCall call : message.getToolCalls()) {
                    array.put(new JSONObject()
                            .put("id", call.getId())
                            .put("name", call.getName())
                            .put("arguments", call.getArguments()));
                }
                object.put("tool_calls", array);
            }
            if (message.isCompactBlock()) {
                object.put("compact_status", message.getCompactStatus());
            }
            if (message.getResponseInputItemJson().length() > 0) {
                object.put("response_input_item_json", message.getResponseInputItemJson());
            }
            if (message.hasAttachments()) {
                JSONArray array = new JSONArray();
                for (InputAttachment attachment : message.getAttachments()) {
                    array.put(new JSONObject()
                            .put("name", attachment.getName())
                            .put("path", attachment.getPath())
                            .put("source", attachment.getSource()));
                }
                object.put("attachments", array);
            }
            return object.length() == 0 ? "" : object.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
