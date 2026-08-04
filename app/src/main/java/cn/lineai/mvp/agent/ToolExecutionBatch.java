package cn.lineai.mvp.agent;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import java.util.ArrayList;

public final class ToolExecutionBatch {
    private final ArrayList<ToolResult> completedResults;
    private final ToolCall pendingCall;
    private final ArrayList<ToolCall> remainingCalls;

    public ToolExecutionBatch(ArrayList<ToolResult> completedResults, ToolCall pendingCall, ArrayList<ToolCall> remainingCalls) {
        this.completedResults = completedResults == null ? new ArrayList<>() : completedResults;
        this.pendingCall = pendingCall;
        this.remainingCalls = remainingCalls == null ? new ArrayList<>() : remainingCalls;
    }

    public ArrayList<ToolResult> getCompletedResults() {
        return completedResults;
    }

    public ToolCall getPendingCall() {
        return pendingCall;
    }

    public ArrayList<ToolCall> getRemainingCalls() {
        return remainingCalls;
    }
}
