package cn.lineai.tool;
import cn.lineai.model.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;

public final class ToolExecutionCoordinator {
    private final ToolRegistry toolRegistry;

    public ToolExecutionCoordinator() {
        this(null);
    }

    public ToolExecutionCoordinator(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolExecutionPlan createPlan(List<ToolCall> toolCalls) {
        ArrayList<ToolCall> concurrentTasks = new ArrayList<>();
        ArrayList<ToolCall> sequentialTasks = new ArrayList<>();
        if (toolCalls == null) {
            return new ToolExecutionPlan(concurrentTasks, sequentialTasks);
        }
        for (ToolCall toolCall : toolCalls) {
            if (isConcurrencySafe(toolCall)) {
                concurrentTasks.add(toolCall);
            } else {
                sequentialTasks.add(toolCall);
            }
        }
        return new ToolExecutionPlan(concurrentTasks, sequentialTasks);
    }

    private boolean isConcurrencySafe(ToolCall toolCall) {
        if (toolCall == null || toolRegistry == null) {
            return false;
        }
        BaseTool tool = toolRegistry.get(toolCall.getName());
        return tool != null && tool.isConcurrencySafe();
    }

    public static final class ToolExecutionPlan {
        private final List<ToolCall> concurrentTasks;
        private final List<ToolCall> sequentialTasks;

        ToolExecutionPlan(List<ToolCall> concurrentTasks, List<ToolCall> sequentialTasks) {
            this.concurrentTasks = concurrentTasks;
            this.sequentialTasks = sequentialTasks;
        }

        public List<ToolCall> getConcurrentTasks() {
            return concurrentTasks;
        }

        public List<ToolCall> getSequentialTasks() {
            return sequentialTasks;
        }
    }
}
