package cn.lineai.engine.agent.loop;

import java.util.List;

import cn.lineai.tool.api.ToolCall;

/** Immutable loop snapshot. The tool-call counter lives here, never in call arguments. */
public final class ToolLoopState {

    public final LoopPhase phase;
    public final int toolCallsUsed;
    public final int maxToolCalls;
    public final List<ToolCall> pending;
    public final FinishReason finishReason;
    public final String lastError;

    public ToolLoopState(LoopPhase phase, int toolCallsUsed, int maxToolCalls,
                         List<ToolCall> pending, FinishReason finishReason, String lastError) {
        this.phase = phase;
        this.toolCallsUsed = toolCallsUsed;
        this.maxToolCalls = maxToolCalls;
        this.pending = pending == null ? java.util.Collections.<ToolCall>emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(pending));
        this.finishReason = finishReason;
        this.lastError = lastError == null ? "" : lastError;
    }

    public static ToolLoopState idle(int maxToolCalls) {
        return new ToolLoopState(LoopPhase.IDLE, 0, maxToolCalls, null, null, "");
    }

    public ToolLoopState phase(LoopPhase next) {
        return new ToolLoopState(next, toolCallsUsed, maxToolCalls, pending, finishReason, lastError);
    }

    public ToolLoopState pending(List<ToolCall> calls, LoopPhase next) {
        return new ToolLoopState(next, toolCallsUsed, maxToolCalls, calls, finishReason, lastError);
    }

    public ToolLoopState used(int count) {
        return new ToolLoopState(phase, count, maxToolCalls, pending, finishReason, lastError);
    }

    public ToolLoopState finished(FinishReason reason) {
        return new ToolLoopState(LoopPhase.COMPLETED, toolCallsUsed, maxToolCalls, null, reason, "");
    }

    public ToolLoopState failed(String reason) {
        return new ToolLoopState(LoopPhase.FAILED, toolCallsUsed, maxToolCalls, null,
                FinishReason.ERROR, reason);
    }

    public ToolLoopState cancelled() {
        return new ToolLoopState(LoopPhase.CANCELLED, toolCallsUsed, maxToolCalls, null, FinishReason.CANCELLED, lastError);
    }

    public boolean budgetLeft(int requested) {
        return maxToolCalls < 0 || toolCallsUsed + requested <= maxToolCalls;
    }
}
