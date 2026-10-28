package cn.lineai.engine.agent.loop;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import cn.lineai.tool.api.ToolCall;
import static org.junit.Assert.*;

public class ToolLoopDeciderTest {
    private final ToolLoopDecider decider = new ToolLoopDecider();

    @Test public void startCallsModel() {
        NextAction action = decider.decide(ToolLoopState.idle(10), new ToolLoopEvent.Start());
        assertEquals(LoopPhase.AWAITING_MODEL, action.state.phase);
        assertTrue(action.has(NextAction.CallModel.class));
    }

    @Test public void responseWithoutCallsFinishes() {
        NextAction action = decider.decide(ToolLoopState.idle(10).phase(LoopPhase.STREAMING),
                new ToolLoopEvent.ResponseReady(Collections.emptyList()));
        assertEquals(LoopPhase.COMPLETED, action.state.phase);
        assertEquals(FinishReason.STOP, action.state.finishReason);
        assertTrue(action.has(NextAction.NotifyFinished.class));
    }

    @Test public void responseWithCallsWaitsForConfirmationAndCountsThem() {
        NextAction action = decider.decide(ToolLoopState.idle(10).phase(LoopPhase.STREAMING),
                new ToolLoopEvent.ResponseReady(calls(2)));
        assertEquals(LoopPhase.AWAITING_CONFIRMATION, action.state.phase);
        assertEquals(2, action.state.toolCallsUsed);
        assertTrue(action.has(NextAction.RequestConfirmation.class));
    }

    @Test public void confirmationExecutesOrFinishes() {
        ToolLoopState awaiting = ToolLoopState.idle(10)
                .pending(calls(1), LoopPhase.AWAITING_CONFIRMATION).used(1);
        NextAction yes = decider.decide(awaiting, new ToolLoopEvent.ConfirmAnswered(true));
        assertEquals(LoopPhase.EXECUTING_TOOLS, yes.state.phase);
        assertTrue(yes.has(NextAction.ExecuteTools.class));
        NextAction no = decider.decide(awaiting, new ToolLoopEvent.ConfirmAnswered(false));
        assertEquals(LoopPhase.COMPLETED, no.state.phase);
    }

    @Test public void finishedToolsCallTheModelAgain() {
        NextAction action = decider.decide(ToolLoopState.idle(10).phase(LoopPhase.EXECUTING_TOOLS),
                new ToolLoopEvent.ToolsFinished());
        assertEquals(LoopPhase.AWAITING_MODEL, action.state.phase);
        assertTrue(action.has(NextAction.CallModel.class));
    }

    @Test public void budgetExceededFinishesWithNotice() {
        ToolLoopState state = ToolLoopState.idle(1).phase(LoopPhase.STREAMING).used(1);
        NextAction action = decider.decide(state, new ToolLoopEvent.ResponseReady(calls(1)));
        assertEquals(LoopPhase.COMPLETED, action.state.phase);
        assertEquals(FinishReason.BUDGET, action.state.finishReason);
        assertTrue(action.has(NextAction.NotifyBudgetExceeded.class));
    }

    @Test public void cancelAndFailureAreTerminal() {
        assertEquals(LoopPhase.CANCELLED, decider.decide(ToolLoopState.idle(5),
                new ToolLoopEvent.Cancel()).state.phase);
        assertEquals(LoopPhase.FAILED, decider.decide(ToolLoopState.idle(5),
                new ToolLoopEvent.Failed("boom")).state.phase);
        assertEquals(LoopPhase.COMPLETED, decider.decide(
                ToolLoopState.idle(5).finished(FinishReason.STOP), new ToolLoopEvent.Start()).state.phase);
    }

    private static List<ToolCall> calls(int count) {
        java.util.List<ToolCall> calls = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) calls.add(new ToolCall("c" + i, "tool", "{}"));
        return calls;
    }
}
