package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.ToolCallTextParser;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.context.TokenUsageTracker;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ExtensionStore;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;

import cn.lineai.model.ModelConfig;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.mvp.agent.AgentProgressSession;
import cn.lineai.mvp.agent.PendingToolExecution;
import cn.lineai.mvp.agent.ToolExecutionBatch;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.util.StringUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONObject;

final class GenerationFlowController {

    interface Host {
        String nextId();

        String projectPath();

        String projectSource();

        String currentConversationId();

        String syncModePermission();

        void persistCurrentConversation();

        void render();

        void stopGenerationKeepAlive();

        void setCurrentCancellationToken(ModelCancellationToken cancellationToken);

        default boolean isSshExecutionMode() {
            return false;
        }

        default boolean isTerminalProviderExecutionMode() {
            return false;
        }

        String formatRetryNotice(int attempt, int maxRetries, String error);

        String formatModelFailed(String error);
    }

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000L;

    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final ModelClient modelClient;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ExtensionStore extensionRepository;
    private final ToolRegistry toolRegistry;
    private final ToolMessageController toolMessageController;
    private final ModelPromptController modelPromptController;
    private final GenerationController generationController;
    private final AgentExecutionController agentExecutionController;
    private final TodoStateStore todoStateStore;
    private final MainThreadDispatcher mainThread;
    private final BackgroundTaskRunner backgroundTasks;
    private final Host host;
    private final ToolConfirmationController toolConfirmationController;
    private final ToolExecutionScheduler toolExecutionScheduler;
    private final StreamingRenderController streamingRenderController;
    private final ContextCompactionController contextCompactionController;
    private final TokenUsageTracker tokenUsageTracker;
    private java.util.function.BooleanSupplier bypassPathProtectionSupplier = () -> false;
    private final AgentExecutionController.Host agentHost = new AgentExecutionController.Host() {
        @Override
        public String projectPath() {
            return host.projectPath();
        }

        @Override
        public String projectSource() {
            return host.projectSource();
        }

        @Override
        public boolean isSshExecutionMode() {
            return host.isSshExecutionMode();
        }

        @Override
        public boolean isTerminalProviderExecutionMode() {
            return host.isTerminalProviderExecutionMode();
        }

        @Override
        public void syncModePermission() {
            host.syncModePermission();
        }

        @Override
        public void addOrReplaceToolResult(ToolResult result) {
            mainThread.dispatch(() -> GenerationFlowController.this.addOrReplaceToolResult(result));
        }

        @Override
        public void render() {
            mainThread.dispatch(host::render);
        }

        @Override
        public void scheduleAgentProgressRender(AgentProgressSession session) {
            GenerationFlowController.this.scheduleAgentProgressRender(session);
        }

        @Override
        public void postToolProgress(
                int generationId,
                ModelCancellationToken cancellationToken,
                String toolCallId,
                String toolName,
                String content,
                boolean error
        ) {
            GenerationFlowController.this.postToolProgress(
                    generationId,
                    cancellationToken,
                    toolCallId,
                    toolName,
                    content,
                    error
            );
        }

        @Override
        public void requestAgentToolReview(String displayToolCallId, ToolCall call, ToolResult pendingToolResult) {
            mainThread.post(() -> {
                toolConfirmationController.putPendingAgentToolRequest(
                        displayToolCallId,
                        new ToolConfirmationController.PendingAgentToolRequest(call, pendingToolResult)
                );
                host.persistCurrentConversation();
                host.render();
            });
        }

        @Override
        public void clearAgentToolReview(String displayToolCallId) {
            mainThread.post(() -> {
                toolConfirmationController.removePendingAgentToolRequest(displayToolCallId);
                host.render();
            });
        }

        @Override
        public void runInBackground(String name, Runnable runnable) {
            backgroundTasks.execute(name, runnable);
        }
    };

    private final ToolConfirmationController.Callback reviewCallback = new ToolConfirmationController.Callback() {
        @Override
        public boolean isActiveGeneration(int generationId) {
            return chatSessionStore.isActiveGeneration(generationId);
        }

        @Override
        public void addOrReplaceToolResult(ToolResult result) {
            GenerationFlowController.this.addOrReplaceToolResult(result);
        }

        @Override
        public void persistCurrentConversation() {
            host.persistCurrentConversation();
        }

        @Override
        public void render() {
            host.render();
        }

        @Override
        public void continueToolExecution(
                int generationId,
                ModelConfig selectedModel,
                List<ToolCall> remainingCalls,
                int usedToolCallCount,
                String homePath,
                ModelCancellationToken cancellationToken
        ) {
            GenerationFlowController.this.continueToolExecution(
                    generationId, selectedModel, remainingCalls,
                    usedToolCallCount, homePath, cancellationToken
            );
        }

        @Override
        public void executeAcceptedPendingTool(PendingToolExecution pending) {
            GenerationFlowController.this.executeAcceptedPendingTool(pending);
        }

        @Override
        public String currentConversationId() {
            return host.currentConversationId();
        }
    };

    private final ToolExecutionScheduler.Host schedulerHost;

    GenerationFlowController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ModelClient modelClient,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ExtensionStore extensionRepository,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolExecutionCoordinator toolExecutionCoordinator,
            cn.lineai.data.repository.ToolSettingsStore toolSettingsStore,
            ToolMessageController toolMessageController,
            ModelPromptController modelPromptController,
            GenerationController generationController,
            AgentExecutionController agentExecutionController,
            TodoStateStore todoStateStore,
            MainThreadDispatcher mainThread,
            BackgroundTaskRunner backgroundTasks,
            ContextCompactionController contextCompactionController,
            TokenUsageTracker tokenUsageTracker,
            Host host
    ) {
        this(
                messages,
                chatSessionStore,
                modelClient,
                aiBehaviorSettingsRepository,
                extensionRepository,
                toolRegistry,
                toolExecutor,
                new ToolRunController(toolExecutionCoordinator, toolRegistry, toolSettingsStore),
                toolMessageController,
                modelPromptController,
                generationController,
                agentExecutionController,
                todoStateStore,
                mainThread,
                backgroundTasks,
                contextCompactionController,
                tokenUsageTracker,
                host
        );
    }

    GenerationFlowController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ModelClient modelClient,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ExtensionStore extensionRepository,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolRunController toolRunController,
            ToolMessageController toolMessageController,
            ModelPromptController modelPromptController,
            GenerationController generationController,
            AgentExecutionController agentExecutionController,
            TodoStateStore todoStateStore,
            MainThreadDispatcher mainThread,
            BackgroundTaskRunner backgroundTasks,
            ContextCompactionController contextCompactionController,
            TokenUsageTracker tokenUsageTracker,
            Host host
    ) {
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.modelClient = modelClient;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.extensionRepository = extensionRepository;
        this.toolRegistry = toolRegistry;
        this.toolMessageController = toolMessageController;
        this.modelPromptController = modelPromptController;
        this.generationController = generationController;
        this.agentExecutionController = agentExecutionController;
        this.todoStateStore = todoStateStore;
        this.mainThread = mainThread;
        this.backgroundTasks = backgroundTasks;
        this.contextCompactionController = contextCompactionController;
        this.tokenUsageTracker = tokenUsageTracker;
        this.host = host;
        this.schedulerHost = () -> host.syncModePermission();
        this.toolConfirmationController = new ToolConfirmationController(reviewCallback);
        this.toolExecutionScheduler = new ToolExecutionScheduler(
                toolRunController, toolExecutor, toolRegistry, toolConfirmationController, schedulerHost
        );
        this.streamingRenderController = new StreamingRenderController(mainThread, result -> {
            if (result == null) {
                return;
            }
            if (!chatSessionStore.isActiveGeneration(result.generationId)) {
                return;
            }
            int index = findMessageIndex(result.assistantId);
            if (index < 0) {
                return;
            }
            ChatMessage message = messages.get(index);
            String visibleText = result.parsedToolCalls.hasToolMarkup()
                    ? result.parsedToolCalls.getText()
                    : message.getContent() + result.textDelta;
            List<ToolCall> toolCalls = result.parsedToolCalls.hasToolMarkup()
                    ? mergeToolCalls(message.getToolCalls(), result.parsedToolCalls.getToolCalls())
                    : message.getToolCalls();
            messages.set(index, message.withContent(
                    visibleText,
                    message.getReasoningContent() + result.reasoningDelta,
                    true
            ).withToolCalls(toolCalls, false));
            host.render();
        }, generationId -> chatSessionStore.isActiveGeneration(generationId));
        if (this.agentExecutionController != null) {
            this.agentExecutionController.setToolReviewAwaiter(new AgentExecutionController.ToolReviewAwaiter() {
                @Override
                public String awaitReview(String displayToolCallId, ToolCall call, ModelCancellationToken cancellationToken) throws InterruptedException {
                    return toolConfirmationController.awaitAgentToolReview(displayToolCallId, call, cancellationToken);
                }

                @Override
                public boolean isAutoConfirmed(ToolCall call) {
                    return toolConfirmationController.isSessionAutoConfirmed(call);
                }
            });
        }
    }

    void startInitialModelRequest(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            String userInput
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return;
        }
        ArrayList<ModelMessage> requestMessages = modelPromptController.buildModelMessages(userInput);
        retryableModelStream(generationId, selectedModel, cancellationToken, requestMessages, 0, 0, userInput);
    }

    private void retryableModelStream(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            ArrayList<ModelMessage> requestMessages,
            int usedToolCallCount,
            int attempt,
            String userInput
    ) {
        String assistantId = host.nextId();
        streamingRenderController.initRawText(assistantId);
        messages.add(new ChatMessage(assistantId, ChatMessage.Role.ASSISTANT, "", true));
        host.persistCurrentConversation();
        host.render();

        backgroundTasks.execute(attempt == 0 ? "linecode-model-stream" : "linecode-model-stream-retry", () -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions requestOptions = modelPromptController.requestOptions(aiSettings, selectedModel, usedToolCallCount);
                ModelCompletionResponse response = modelClient.stream(selectedModel, requestMessages, new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        streamingRenderController.appendDelta(generationId, assistantId, delta, "");
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        streamingRenderController.appendDelta(generationId, assistantId, "", delta);
                    }
                }, cancellationToken, requestOptions);
                // codex 式 token 统计：记录服务器观测到的 usage，供压缩触发判断使用。
                if (tokenUsageTracker != null) {
                    tokenUsageTracker.record(response);
                }
                finishGeneration(generationId, assistantId, selectedModel, response, cancellationToken, usedToolCallCount);
            } catch (ModelCompletionException e) {
                handleModelError(generationId, assistantId, selectedModel, cancellationToken,
                        requestMessages, usedToolCallCount, attempt, e, userInput);
            }
        });
    }

    private void handleModelError(
            int generationId,
            String failedAssistantId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            ArrayList<ModelMessage> requestMessages,
            int usedToolCallCount,
            int failedAttempt,
            ModelCompletionException error,
            String userInput
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            failGeneration(generationId, failedAssistantId, host.formatModelFailed(error.getMessage()));
            return;
        }
        int nextAttempt = failedAttempt + 1;
        if (nextAttempt >= MAX_RETRIES) {
            failGeneration(generationId, failedAssistantId, host.formatModelFailed(error.getMessage()));
            return;
        }

        mainThread.post(() -> {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            int index = findMessageIndex(failedAssistantId);
            if (index >= 0) {
                messages.remove(index);
            }
            streamingRenderController.removeRawText(failedAssistantId);

            String retryText = host.formatRetryNotice(nextAttempt + 1, MAX_RETRIES, error.getMessage());
            messages.add(ChatMessage.retryNotice(host.nextId(), retryText));
            host.persistCurrentConversation();
            host.render();

            mainThread.postDelayed(() -> {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    return;
                }
                retryableModelStream(generationId, selectedModel, cancellationToken,
                        requestMessages, usedToolCallCount, nextAttempt, userInput);
            }, RETRY_DELAY_MS);
        });
    }

    void handleToolReview(String state) {
        toolConfirmationController.handleToolReview(state);
    }

    boolean handleAgentToolReview(String toolCallId, String state) {
        return toolConfirmationController.handleAgentToolReview(toolCallId, state);
    }

    boolean isPendingAgentToolReview(String toolCallId) {
        return toolConfirmationController.isPendingAgentToolReview(toolCallId);
    }

    void acceptAgentToolReview(String toolCallId, String state) {
        toolConfirmationController.acceptAgentToolReview(toolCallId, state);
    }

    boolean isPendingToolReview(String toolCallId) {
        return toolConfirmationController.isPendingToolReview(toolCallId);
    }

    void postToolProgress(
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
            String reviewState = resolveProgressReviewState(content, error);
            String existing = toolMessageController.currentReviewState(toolCallId);
            boolean existingIsFinal = existing.length() > 0
                    && !"running".equals(existing)
                    && !"pending".equals(existing)
                    && !"accepted".equals(existing);
            if (existingIsFinal && "running".equals(reviewState)) {
                return;
            }
            addOrReplaceToolResult(ToolResult.withReview(
                    toolCallId,
                    toolName,
                    content,
                    error,
                    "",
                    reviewState,
                    ""
            ));
            host.render();
        });
    }

    private static String resolveProgressReviewState(String content, boolean error) {
        if (content == null || content.trim().length() == 0) {
            return error ? "error" : "running";
        }
        try {
            JSONObject object = new JSONObject(content);
            if (object.has("status")) {
                String status = object.optString("status", "running");
                if (status.length() > 0) {
                    return status;
                }
            }
        } catch (Exception ignored) {
        }
        return error ? "error" : "running";
    }

    void flushPendingAssistantDelta() {
        streamingRenderController.flush();
    }

    void cancelActiveGeneration() {
        toolConfirmationController.cancelPendingReviews();
        streamingRenderController.clear();
    }

    void clearStreamingRawText() {
        streamingRenderController.clear();
    }

    void clearSessionAutoToolConfirmations() {
        toolConfirmationController.clearSessionAutoToolConfirmations();
    }

    void scheduleAgentProgressRender(AgentProgressSession session) {
        if (session == null || !session.shouldScheduleRender()) {
            return;
        }
        mainThread.postDelayed(() -> flushAgentProgress(session), session.renderDelayMs());
    }

    void markRunningAgentProgressStopped(String terminatedMessage) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != ChatMessage.Role.TOOL) {
                continue;
            }
            ConversationResumeSanitizer.SanitizedPayload payload =
                    ConversationResumeSanitizer.sanitizeToolContent(message.getContent(), terminatedMessage);
            if (!payload.changed()) {
                continue;
            }
            messages.set(i, new ChatMessage(
                    message.getId(),
                    ChatMessage.Role.TOOL,
                    payload.content(),
                    message.getReasoningContent(),
                    false,
                    message.isHidden(),
                    message.isExcludeFromContext(),
                    message.getToolCalls(),
                    message.getToolResults(),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError() || payload.error(),
                    message.getDiffId(),
                    message.getReviewState(),
                    message.getReviewMessage()
            ));
        }
        addTerminatedResultsForUnfinishedToolCalls(terminatedMessage);
    }

    private void finishGeneration(
            int generationId,
            String assistantId,
            ModelConfig selectedModel,
            ModelCompletionResponse response,
            ModelCancellationToken cancellationToken,
            int usedToolCallCount
    ) {
        mainThread.post(() -> {
            streamingRenderController.flush();
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index < 0) {
                return;
            }
            ChatMessage message = messages.get(index);
            String rawResponseText = response.getText();
            StringBuilder rawStream = streamingRenderController.removeRawText(assistantId);
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
                if (!generationController.canExecuteToolCalls(selectedModel, usedToolCallCount, toolCalls.size())) {
                    messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT,
                            generationController.toolLimitMessage(selectedModel, usedToolCallCount, toolCalls.size()), false));
                    finishActiveGeneration();
                    host.persistCurrentConversation();
                    host.render();
                    return;
                }
                host.persistCurrentConversation();
                host.render();
                executeToolsAndContinue(
                        generationId,
                        selectedModel,
                        toolCalls,
                        usedToolCallCount + toolCalls.size(),
                        cancellationToken
                );
                return;
            }
            finishActiveGeneration();
            host.persistCurrentConversation();
            host.render();
        });
    }

    private void executeToolsAndContinue(
            int generationId,
            ModelConfig selectedModel,
            List<ToolCall> toolCalls,
            int usedToolCallCount,
            ModelCancellationToken cancellationToken
    ) {
        continueToolExecution(
                generationId,
                selectedModel,
                toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls),
                usedToolCallCount,
                host.projectPath(),
                cancellationToken
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
            ToolContext context = toolContext(homePath, selectedModel, cancellationToken, generationId, usedToolCallCount);
            ToolExecutionBatch batch = toolExecutionScheduler.executeToolCallsUntilPending(
                    toolCalls, context, cancellationToken
            );
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
        toolMessageController.addOrReplaceToolResults(batch.getCompletedResults());
        if (batch.getPendingCall() != null) {
            ToolResult pendingResult = ToolResult.withReview(
                    batch.getPendingCall().getId(),
                    batch.getPendingCall().getName(),
                    "",
                    false,
                    "",
                    "pending",
                    ""
            );
            addOrReplaceToolResult(pendingResult);
            toolConfirmationController.setPendingToolExecution(new PendingToolExecution(
                    generationId,
                    selectedModel,
                    batch.getPendingCall(),
                    batch.getRemainingCalls(),
                    usedToolCallCount,
                    homePath,
                    cancellationToken
            ));
            host.persistCurrentConversation();
            host.render();
            return;
        }
        host.persistCurrentConversation();
        continueModelAfterTools(generationId, selectedModel, usedToolCallCount, cancellationToken);
    }

    private void continueModelAfterTools(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            ModelCancellationToken cancellationToken
    ) {
        // 工具循环中的硬触发压缩检查（80%）：模型结束并调用工具后、继续下一次模型请求前，
        // 若上下文占用已到 80%（81-99%），先压缩历史再继续，避免长工具循环中上下文
        // 持续增长超出窗口。压缩会保留当前 assistant+tool 在途分组。
        if (contextCompactionController != null && contextCompactionController.shouldAutoCompactMidLoop(selectedModel)) {
            contextCompactionController.startToolLoopContextCompaction(
                    generationId,
                    selectedModel,
                    cancellationToken,
                    usedToolCallCount
            );
            return;
        }
        ArrayList<ModelMessage> nextRequestMessages = modelPromptController.buildModelMessages("", usedToolCallCount);
        retryableModelStream(generationId, selectedModel, cancellationToken, nextRequestMessages, usedToolCallCount, 0, "");
    }

    /**
     * 工具循环内压缩完成后的续接入口：与 {@link #startInitialModelRequest} 不同，
     * 这里不重置 usedToolCallCount，直接回到工具执行后的模型续跑路径。
     */
    void continueToolLoop(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int usedToolCallCount
    ) {
        continueModelAfterTools(generationId, selectedModel, usedToolCallCount, cancellationToken);
    }

    private ToolContext toolContext(
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            int usedToolCallCount
    ) {
        final int[] counter = new int[]{Math.max(0, usedToolCallCount)};
        return ToolContext.builder()
                .homePath(homePath)
                .extraWriteRoots(extensionRepository.skillWriteRoots(homePath))
                .agentRunner(new ToolContext.AgentRunner() {
                    @Override
                    public ToolResult runAgent(JSONObject input, ToolContext context) {
                        return agentExecutionController.runAgentTool(input, context, selectedModel, cancellationToken, generationId, agentHost, counter[0]);
                    }

                    @Override
                    public ToolResult runAgentPipeline(JSONObject input, ToolContext context) {
                        return agentExecutionController.runAgentPipelineTool(input, context, selectedModel, cancellationToken, generationId, agentHost, counter[0]);
                    }
                })
                .toolCallId("")
                .progressListener((toolCallId, toolName, content, error) ->
                        postToolProgress(generationId, cancellationToken, toolCallId, toolName, content, error))
                .todoStateStore(todoStateStore)
                .agentResultStore(agentExecutionController == null
                        ? null
                        : agentExecutionController.getAgentResultRegistry())
                .bypassPathProtection(isBypassPathProtection())
                .build();
    }

    void setBypassPathProtectionSupplier(java.util.function.BooleanSupplier supplier) {
        this.bypassPathProtectionSupplier = supplier != null ? supplier : () -> false;
    }

    private boolean isBypassPathProtection() {
        try {
            return bypassPathProtectionSupplier.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void executeAcceptedPendingTool(PendingToolExecution pending) {
        backgroundTasks.execute("linecode-tool-confirmed", () -> {
            ToolResult result;
            try {
                result = toolExecutionScheduler
                        .executeConfirmed(pending.getToolCall(), toolContext(
                                pending.getHomePath(),
                                pending.getSelectedModel(),
                                pending.getCancellationToken(),
                                pending.getGenerationId(),
                                pending.getUsedToolCallCount()
                        ))
                        .withReview("accepted", "");
            } catch (Exception e) {
                restoreInterrupt(e);
                result = ToolResult.withReview(
                        pending.getToolCall().getId(),
                        pending.getToolCall().getName(),
                        "执行失败: " + describeException(e),
                        true,
                        "",
                        "accepted",
                        ""
                );
            }
            ToolResult finalResult = result;
            if (pending.getCancellationToken() != null && pending.getCancellationToken().isCancelled()) {
                return;
            }
            mainThread.post(() -> {
                if (!chatSessionStore.isActiveGeneration(pending.getGenerationId())) {
                    return;
                }
                addOrReplaceToolResult(finalResult);
                host.persistCurrentConversation();
                host.render();
                continueToolExecution(
                        pending.getGenerationId(),
                        pending.getSelectedModel(),
                        pending.getRemainingCalls(),
                        pending.getUsedToolCallCount(),
                        pending.getHomePath(),
                        pending.getCancellationToken()
                );
            });
        });
    }

    private void failGeneration(int generationId, String assistantId, String text) {
        final String displayText = StringUtils.decodeUnicodeEscapes(text);
        mainThread.post(() -> {
            streamingRenderController.flush();
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index >= 0) {
                ChatMessage message = messages.get(index);
                messages.set(index, message.withContent(displayText, message.getReasoningContent(), false));
            } else {
                messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT, displayText, false));
            }
            streamingRenderController.removeRawText(assistantId);
            finishActiveGeneration();
            host.persistCurrentConversation();
            host.render();
        });
    }

    private void finishActiveGeneration() {
        chatSessionStore.setStreaming(false);
        host.setCurrentCancellationToken(null);
        host.stopGenerationKeepAlive();
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
        if (!chatSessionStore.isActiveGeneration(session.getGenerationId())) {
            return;
        }
        session.notifyMirror();
        addOrReplaceToolResult(session.snapshotResult());
        host.render();
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

    void addTerminatedResultsForUnfinishedToolCalls(String terminatedMessage) {
        toolMessageController.addTerminatedResultsForUnfinishedToolCalls(terminatedMessage);
    }

    private void addOrReplaceToolResult(ToolResult result) {
        toolMessageController.addOrReplaceToolResult(result);
    }

    private static void restoreInterrupt(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describeException(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (message != null && message.trim().length() > 0) {
            return StringUtils.decodeUnicodeEscapes(message.trim());
        }
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().trim().length() > 0) {
            return StringUtils.decodeUnicodeEscapes(cause.getMessage().trim());
        }
        String name = error.getClass().getSimpleName();
        return name.length() == 0 ? "未知错误" : name;
    }

    private int findMessageIndex(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
