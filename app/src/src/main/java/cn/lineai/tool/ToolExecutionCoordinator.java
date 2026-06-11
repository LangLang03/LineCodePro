package cn.lineai.tool;

import java.util.ArrayList;
import java.util.List;

public final class ToolExecutionCoordinator {
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
        if (toolCall == null) {
            return false;
        }
        String name = toolCall.getName();
        return "file_read".equals(name)
                || "glob".equals(name)
                || "list_dir".equals(name)
                || "web_search".equals(name)
                || "web_fetch".equals(name);
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
