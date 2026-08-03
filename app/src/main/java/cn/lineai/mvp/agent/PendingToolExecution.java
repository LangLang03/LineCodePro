package cn.lineai.mvp.agent;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.model.ModelConfig;
import cn.lineai.ai.ModelCancellationToken;
import java.util.ArrayList;

public final class PendingToolExecution {
    private final int generationId;
    private final ModelConfig selectedModel;
    private final ToolCall toolCall;
    private final ArrayList<ToolCall> remainingCalls;
    private final int usedToolCallCount;
    private final String homePath;
    private final ModelCancellationToken cancellationToken;

    public PendingToolExecution(
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

    public int getGenerationId() {
        return generationId;
    }

    public ModelConfig getSelectedModel() {
        return selectedModel;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public ArrayList<ToolCall> getRemainingCalls() {
        return remainingCalls;
    }

    public int getUsedToolCallCount() {
        return usedToolCallCount;
    }

    public String getHomePath() {
        return homePath;
    }

    public ModelCancellationToken getCancellationToken() {
        return cancellationToken;
    }
}
