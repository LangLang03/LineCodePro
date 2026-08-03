package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.mvp.agent.ToolExecutionBatch;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class ToolExecutionScheduler {
    private final ExecutorService concurrentExecutor;
    private final ToolRunController toolRunController;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final ToolConfirmationController toolReviewController;
    private final Host host;

    interface Host {
        void syncModePermission();
    }

    ToolExecutionScheduler(
            ToolRunController toolRunController,
            ToolExecutor toolExecutor,
            ToolRegistry toolRegistry,
            ToolConfirmationController toolReviewController,
            Host host
    ) {
        this.concurrentExecutor = Executors.newFixedThreadPool(4);
        this.toolRunController = toolRunController;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.toolReviewController = toolReviewController;
        this.host = host;
    }

    ToolExecutionBatch executeToolCallsUntilPending(
            List<ToolCall> toolCalls,
            ToolContext context,
            ModelCancellationToken cancellationToken
    ) {
        host.syncModePermission();
        toolRegistry.reloadExtensions();
        ToolExecutionCoordinator.ToolExecutionPlan plan = toolRunController.createPlan(toolCalls);
        HashMap<String, ToolResult> resultById = new HashMap<>();

        if (!plan.getConcurrentTasks().isEmpty()) {
            ArrayList<ToolCall> concurrentCalls = new ArrayList<>(plan.getConcurrentTasks());
            ArrayList<Future<ToolResult>> futures = new ArrayList<>();
            for (ToolCall call : concurrentCalls) {
                futures.add(concurrentExecutor.submit(() -> toolExecutor.execute(call, context)));
            }
            for (int i = 0; i < futures.size(); i++) {
                ToolCall call = concurrentCalls.get(i);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    return new ToolExecutionBatch(new ArrayList<>(), null, new ArrayList<>());
                }
                try {
                    ToolResult result = futures.get(i).get();
                    resultById.put(call.getId(), result);
                } catch (Exception e) {
                    restoreInterrupt(e);
                    resultById.put(call.getId(), ToolResult.of(call.getId(), call.getName(), "执行失败: " + describeException(e), true));
                }
            }
        }

        List<ToolCall> sequentialTasks = plan.getSequentialTasks();
        for (int i = 0; i < sequentialTasks.size(); i++) {
            ToolCall call = sequentialTasks.get(i);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return new ToolExecutionBatch(new ArrayList<>(), null, new ArrayList<>());
            }
            if (shouldPauseForConfirmation(call)) {
                return new ToolExecutionBatch(
                        toolRunController.orderedResults(toolCalls, resultById),
                        call,
                        toolRunController.remainingCalls(sequentialTasks, i + 1)
                );
            }
            resultById.put(call.getId(), executeToolCallWithSessionPolicy(call, context));
        }

        return new ToolExecutionBatch(toolRunController.orderedResults(toolCalls, resultById), null, new ArrayList<>());
    }

    ToolResult executeConfirmed(ToolCall call, ToolContext context) {
        host.syncModePermission();
        toolRegistry.reloadExtensions();
        return toolExecutor.executeConfirmed(call, context);
    }

    private ToolResult executeToolCallWithSessionPolicy(ToolCall call, ToolContext context) {
        if (toolReviewController.isSessionAutoConfirmed(call)) {
            return toolExecutor.executeConfirmed(call, context).withReview("accepted", "");
        }
        return toolExecutor.execute(call, context);
    }

    private boolean shouldPauseForConfirmation(ToolCall call) {
        host.syncModePermission();
        if (toolReviewController.isSessionAutoConfirmed(call)) {
            return false;
        }
        return toolRunController.shouldPauseForConfirmation(call);
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
}
