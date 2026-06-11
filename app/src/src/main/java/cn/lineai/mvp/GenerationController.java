package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;

public final class GenerationController {
    public boolean canExecuteToolCalls(ModelConfig selectedModel, int usedToolCallCount, int requestedCount) {
        int limit = toolCallLimit(selectedModel);
        if (limit == ModelConfig.UNLIMITED_TOOL_CALLS) {
            return true;
        }
        if (requestedCount <= 0) {
            return true;
        }
        return usedToolCallCount >= 0 && usedToolCallCount + requestedCount <= limit;
    }

    public boolean hasRemainingToolCalls(ModelConfig selectedModel, int usedToolCallCount) {
        int limit = toolCallLimit(selectedModel);
        return limit == ModelConfig.UNLIMITED_TOOL_CALLS || Math.max(0, usedToolCallCount) < limit;
    }

    public String toolLimitMessage(ModelConfig selectedModel, int usedToolCallCount, int requestedCount) {
        int limit = toolCallLimit(selectedModel);
        if (limit == 0) {
            return "当前模型已禁止工具调用，已停止继续执行。";
        }
        return "工具调用次数已达到上限，已停止继续执行。\n"
                + "已使用 " + Math.max(0, usedToolCallCount)
                + " 次，本次请求 " + Math.max(0, requestedCount)
                + " 次，限制为 " + limit + " 次。";
    }

    private int toolCallLimit(ModelConfig selectedModel) {
        return selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
    }
}
