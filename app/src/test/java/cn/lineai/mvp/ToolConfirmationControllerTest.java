package cn.lineai.mvp;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.mvp.agent.PendingToolExecution;
import cn.lineai.tool.ToolNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolConfirmationControllerTest {
    private ToolCall shell() { return new ToolCall("call", ToolNames.SHELL_EXECUTE, "{\"command\":\"pwd\"}"); }
    private PendingToolExecution pending(ToolCall call) { return new PendingToolExecution(1, null, call, new ArrayList<>(), 1, "/project", null); }
    @Test public void allowOnceExecutesWithoutSavingAGrantAndDismissesTheRequest() {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        controller.setPendingToolExecution(pending(shell()));
        assertNotNull(controller.pendingToolApproval());
        controller.handleToolReview("accepted");
        assertEquals(1, host.executed); assertTrue(host.grants.isEmpty()); assertNull(controller.pendingToolApproval());
    }
    @Test public void permanentGrantIsSavedBeforeExecution() {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        controller.setPendingToolExecution(pending(shell())); controller.handleToolReview("permanent");
        assertEquals(1, host.executed); assertTrue(host.grantedAtExecution);
        assertTrue(controller.isSessionAutoConfirmed(shell()));
        host.scope = "other-target:/project"; assertFalse(controller.isSessionAutoConfirmed(shell()));
    }
    @Test public void rejectingNeverExecutesOrSavesAndContinuesTheBatch() {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        controller.setPendingToolExecution(pending(shell())); controller.handleToolReview("rejected");
        assertEquals(0, host.executed); assertEquals(1, host.continued); assertTrue(host.grants.isEmpty());
        assertEquals("rejected", host.result.getReviewState());
    }
    @Test public void changingTheTargetWhileAwaitingApprovalRejectsTheStaleRequest() {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        controller.setPendingToolExecution(pending(shell())); host.scope = "different";
        controller.handleToolReview("permanent");
        assertEquals(0, host.executed); assertTrue(host.grants.isEmpty()); assertEquals("rejected", host.result.getReviewState());
    }
    @Test public void cancellingAndInactiveGenerationsCannotLeaveAnApprovalCard() {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        controller.setPendingToolExecution(pending(shell())); controller.cancelPendingReviews();
        assertNull(controller.pendingToolApproval()); controller.handleToolReview("accepted"); assertEquals(0, host.executed);
        controller.setPendingToolExecution(pending(shell())); host.active = false;
        assertNull(controller.pendingToolApproval()); controller.handleToolReview("permanent"); assertTrue(host.grants.isEmpty());
    }
    @Test public void nonShellToolsHaveNoPermanentOption() {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        controller.setPendingToolExecution(pending(new ToolCall("file", ToolNames.FILE_DELETE, "{}")));
        assertFalse(controller.pendingToolApproval().canAllowPermanently());
        controller.handleToolReview("permanent"); assertTrue(host.grants.isEmpty());
    }
    @Test public void agentApprovalCanArriveBeforeTheWorkerStartsWaiting() throws Exception {
        Host host = new Host(); ToolConfirmationController controller = new ToolConfirmationController(host);
        ToolCall call = shell();
        controller.putPendingAgentToolRequest("agent-call", new ToolConfirmationController.PendingAgentToolRequest(call,
                ToolResult.withReview(call.getId(), call.getName(), "", false, "", "pending", "")));
        assertEquals("agent-call", controller.pendingToolApproval().getReviewId());
        controller.acceptAgentToolReview("agent-call", "permanent");
        assertEquals("accepted", controller.awaitAgentToolReview("agent-call", call, null));
        assertTrue(controller.isSessionAutoConfirmed(call)); assertNull(controller.pendingToolApproval());
    }
    private static final class Host implements ToolConfirmationController.Callback {
        String scope = "ssh:host:/project"; boolean active = true; boolean grantedAtExecution;
        int executed, continued; ToolResult result; final Set<String> grants = new HashSet<>();
        public boolean isActiveGeneration(int generation) { return active; }
        public void addOrReplaceToolResult(ToolResult result) { this.result = result; }
        public void persistCurrentConversation() {}
        public void render() {}
        public void continueToolExecution(int id, ModelConfig model, List<ToolCall> calls, int count, String path, ModelCancellationToken token) { continued++; }
        public void executeAcceptedPendingTool(PendingToolExecution pending) { executed++; grantedAtExecution = isPermanentlyAllowed(scope, pending.getToolCall()); }
        public String currentConversationId() { return "conversation"; }
        public String executionPermissionScope() { return scope; }
        public boolean isPermanentlyAllowed(String scope, ToolCall call) { return grants.contains(scope + call.getArguments()); }
        public void rememberPermanentAllowance(String scope, ToolCall call) { grants.add(scope + call.getArguments()); }
    }
}
