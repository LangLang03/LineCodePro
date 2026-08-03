package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ModelConfig;
import cn.lineai.mvp.agent.PendingToolExecution;
import cn.lineai.tool.builtin.ShellExecuteTool;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

final class ToolConfirmationController {
    private static final String SHELL_EXECUTE_TOOL = ShellExecuteTool.NAME;
    private static final String TOOL_REVIEW_SESSION_AUTO = "session_auto";

    interface Callback {
        boolean isActiveGeneration(int generationId);

        void addOrReplaceToolResult(ToolResult result);

        void persistCurrentConversation();

        void render();

        void continueToolExecution(
                int generationId,
                ModelConfig selectedModel,
                List<ToolCall> remainingCalls,
                int usedToolCallCount,
                String homePath,
                ModelCancellationToken cancellationToken
        );

        void executeAcceptedPendingTool(PendingToolExecution pending);

        String currentConversationId();
    }

    private final Callback callback;
    private final Set<String> sessionAutoConfirmedTools = new HashSet<>();
    private final HashMap<String, PendingAgentToolReview> pendingAgentToolReviews = new HashMap<>();
    private final HashMap<String, PendingAgentToolRequest> pendingAgentToolRequests = new HashMap<>();
    private String sessionAutoConfirmedConversationId = "";
    private PendingToolExecution pendingToolExecution;

    ToolConfirmationController(Callback callback) {
        this.callback = callback;
    }

    void handleToolReview(String state) {
        PendingToolExecution pending = pendingToolExecution;
        if (pending == null) {
            return;
        }
        handlePendingToolReview(pending, state);
    }

    boolean handleAgentToolReview(String toolCallId, String state) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return false;
        }
        PendingAgentToolReview pending;
        synchronized (pendingAgentToolReviews) {
            pending = pendingAgentToolReviews.get(toolCallId);
        }
        if (pending == null) {
            return false;
        }
        pending.resolve(state);
        if (isSessionAutoReview(state, pending.toolCall())) {
            rememberSessionAutoConfirmation(pending.toolCall());
        }
        return true;
    }

    String awaitAgentToolReview(
            String displayToolCallId,
            ToolCall call,
            ModelCancellationToken cancellationToken
    ) throws InterruptedException {
        if (displayToolCallId == null || displayToolCallId.length() == 0) {
            return "accepted";
        }
        PendingAgentToolReview pending = new PendingAgentToolReview(call);
        synchronized (pendingAgentToolReviews) {
            pendingAgentToolReviews.put(displayToolCallId, pending);
        }
        try {
            while (true) {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    return "rejected";
                }
                if (pending.await(250L)) {
                    return pending.state();
                }
            }
        } finally {
            synchronized (pendingAgentToolReviews) {
                pendingAgentToolReviews.remove(displayToolCallId);
            }
        }
    }

    boolean isPendingToolReview(String toolCallId) {
        return toolCallId != null
                && toolCallId.length() > 0
                && pendingToolExecution != null
                && pendingToolExecution.getToolCall() != null
                && toolCallId.equals(pendingToolExecution.getToolCall().getId());
    }

    boolean isPendingAgentToolReview(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return false;
        }
        synchronized (pendingAgentToolRequests) {
            return pendingAgentToolRequests.containsKey(toolCallId);
        }
    }

    void acceptAgentToolReview(String toolCallId, String state) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        PendingAgentToolRequest request;
        synchronized (pendingAgentToolRequests) {
            request = pendingAgentToolRequests.remove(toolCallId);
        }
        if (request == null) {
            return;
        }
        handleAgentToolReview(toolCallId, state);
    }

    void setPendingToolExecution(PendingToolExecution pending) {
        this.pendingToolExecution = pending;
    }

    PendingToolExecution getPendingToolExecution() {
        return pendingToolExecution;
    }

    void putPendingAgentToolRequest(String toolCallId, PendingAgentToolRequest request) {
        synchronized (pendingAgentToolRequests) {
            pendingAgentToolRequests.put(toolCallId, request);
        }
    }

    void removePendingAgentToolRequest(String toolCallId) {
        synchronized (pendingAgentToolRequests) {
            pendingAgentToolRequests.remove(toolCallId);
        }
    }

    void clearPendingAgentToolRequests() {
        synchronized (pendingAgentToolRequests) {
            pendingAgentToolRequests.clear();
        }
    }

    void cancelPendingReviews() {
        pendingToolExecution = null;
        rejectPendingAgentToolReviews();
        synchronized (pendingAgentToolRequests) {
            pendingAgentToolRequests.clear();
        }
    }

    void clearSessionAutoToolConfirmations() {
        synchronized (sessionAutoConfirmedTools) {
            sessionAutoConfirmedTools.clear();
            sessionAutoConfirmedConversationId = callback.currentConversationId();
        }
    }

    boolean isSessionAutoConfirmed(ToolCall call) {
        if (call == null) {
            return false;
        }
        synchronized (sessionAutoConfirmedTools) {
            syncSessionAutoToolConfirmationsLocked();
            return sessionAutoConfirmedTools.contains(call.getName());
        }
    }

    void rememberSessionAutoConfirmation(ToolCall call) {
        if (call == null || !SHELL_EXECUTE_TOOL.equals(call.getName())) {
            return;
        }
        synchronized (sessionAutoConfirmedTools) {
            syncSessionAutoToolConfirmationsLocked();
            sessionAutoConfirmedTools.add(call.getName());
        }
    }

    private void handlePendingToolReview(PendingToolExecution pending, String state) {
        if (pending == null || pending.getToolCall() == null) {
            return;
        }
        if (!callback.isActiveGeneration(pending.getGenerationId())) {
            pendingToolExecution = null;
            return;
        }
        boolean sessionAutoAccepted = isSessionAutoReview(state, pending.getToolCall());
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        pendingToolExecution = null;
        if ("rejected".equals(normalizedState)) {
            ToolResult rejected = ToolResult.withReview(
                    pending.getToolCall().getId(),
                    pending.getToolCall().getName(),
                    rejectedToolMessage(pending.getToolCall()),
                    true,
                    "",
                    "rejected",
                    ""
            );
            callback.addOrReplaceToolResult(rejected);
            callback.persistCurrentConversation();
            callback.render();
            callback.continueToolExecution(
                    pending.getGenerationId(),
                    pending.getSelectedModel(),
                    pending.getRemainingCalls(),
                    pending.getUsedToolCallCount(),
                    pending.getHomePath(),
                    pending.getCancellationToken()
            );
            return;
        }
        if (sessionAutoAccepted) {
            rememberSessionAutoConfirmation(pending.getToolCall());
        }

        ToolResult accepted = ToolResult.withReview(
                pending.getToolCall().getId(),
                pending.getToolCall().getName(),
                "",
                false,
                "",
                "accepted",
                ""
        );
        callback.addOrReplaceToolResult(accepted);
        callback.persistCurrentConversation();
        callback.render();
        callback.executeAcceptedPendingTool(pending);
    }

    private boolean isSessionAutoReview(String state, ToolCall call) {
        return TOOL_REVIEW_SESSION_AUTO.equals(state)
                && call != null
                && SHELL_EXECUTE_TOOL.equals(call.getName());
    }

    private void syncSessionAutoToolConfirmationsLocked() {
        String conversationId = callback.currentConversationId();
        if (!conversationId.equals(sessionAutoConfirmedConversationId)) {
            sessionAutoConfirmedTools.clear();
            sessionAutoConfirmedConversationId = conversationId;
        }
    }

    private void rejectPendingAgentToolReviews() {
        synchronized (pendingAgentToolReviews) {
            for (PendingAgentToolReview pending : pendingAgentToolReviews.values()) {
                if (pending != null) {
                    pending.resolve("rejected");
                }
            }
            pendingAgentToolReviews.clear();
        }
    }

    private static String rejectedToolMessage(ToolCall call) {
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

    static final class PendingAgentToolReview {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final ToolCall toolCall;
        private String state = "accepted";

        PendingAgentToolReview(ToolCall toolCall) {
            this.toolCall = toolCall;
        }

        boolean await(long timeoutMs) throws InterruptedException {
            return latch.await(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        }

        void resolve(String nextState) {
            state = "rejected".equals(nextState) ? "rejected" : "accepted";
            latch.countDown();
        }

        String state() {
            return state;
        }

        ToolCall toolCall() {
            return toolCall;
        }
    }

    static final class PendingAgentToolRequest {
        final ToolCall call;
        final ToolResult pending;

        PendingAgentToolRequest(ToolCall call, ToolResult pending) {
            this.call = call;
            this.pending = pending;
        }
    }
}
