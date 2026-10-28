package cn.lineai.engine.agent.loop;

import java.util.List;

import cn.lineai.tool.api.ToolCall;

/** Pure transition function: no IO, clock or randomness. */
public final class ToolLoopDecider {

    public NextAction decide(ToolLoopState state, ToolLoopEvent event) {
        if (state.phase.terminal()) {
            return NextAction.of(state);
        }
        if (event instanceof ToolLoopEvent.Cancel) {
            return NextAction.of(state.cancelled());
        }
        if (event instanceof ToolLoopEvent.Failed) {
            return NextAction.of(state.failed(((ToolLoopEvent.Failed) event).message),
                    new NextAction.NotifyFailed());
        }
        if (event instanceof ToolLoopEvent.Start) {
            return NextAction.of(state.phase(LoopPhase.AWAITING_MODEL), new NextAction.CallModel());
        }
        if (event instanceof ToolLoopEvent.ResponseReady) {
            return responseReady(state, ((ToolLoopEvent.ResponseReady) event).calls);
        }
        if (event instanceof ToolLoopEvent.ConfirmAnswered) {
            if (!((ToolLoopEvent.ConfirmAnswered) event).allowed) {
                return NextAction.of(state.finished(FinishReason.DENIED));
            }
            return NextAction.of(state.phase(LoopPhase.EXECUTING_TOOLS),
                    new NextAction.ExecuteTools());
        }
        if (event instanceof ToolLoopEvent.ToolsFinished) {
            return NextAction.of(state.phase(LoopPhase.AWAITING_MODEL), new NextAction.CallModel());
        }
        return NextAction.of(state);
    }

    private NextAction responseReady(ToolLoopState state, List<ToolCall> calls) {
        if (calls.isEmpty()) {
            return NextAction.of(state.finished(FinishReason.STOP), new NextAction.NotifyFinished());
        }
        if (!state.budgetLeft(calls.size())) {
            return NextAction.of(state.finished(FinishReason.BUDGET),
                    new NextAction.NotifyBudgetExceeded(calls.size(), state.toolCallsUsed,
                            state.maxToolCalls));
        }
        int used = state.maxToolCalls < 0 ? state.toolCallsUsed + calls.size()
                : state.toolCallsUsed + calls.size();
        ToolLoopState awaiting = new ToolLoopState(LoopPhase.AWAITING_CONFIRMATION, used,
                state.maxToolCalls, calls, state.finishReason, state.lastError);
        return NextAction.of(awaiting, new NextAction.RequestConfirmation());
    }
}
