package cn.lineai.tool.ui;

import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class ToolCallAgentViewLayoutSignatureTest {

    private static ToolResult result(String content) {
        return ToolResult.withReview("1", "agent", content, false, "", "", "");
    }

    private static ToolResult result(String content, boolean error, String reviewState) {
        return ToolResult.withReview("1", "agent", content, error, "", reviewState, "");
    }

    private static String progressJson(String status, String output, String thinking,
                                       String agentId, int toolCount, JSONArray calls) {
        try {
            JSONObject object = new JSONObject();
            object.put("linecode_agent_progress", true);
            object.put("status", status);
            object.put("output", output);
            object.put("thinking", thinking);
            object.put("agent_id", agentId);
            object.put("tool_call_count", toolCount);
            if (calls != null) {
                object.put("tool_calls", calls);
            }
            return object.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ToolCall call() {
        return new ToolCall("call-1", "agent", "{}");
    }

    @Test
    public void signatureEqualWhenOnlyOutputTextGrows() {
        String shortOut = progressJson("running", "step 1", "thinking...", "ag-1", 2, null);
        String longOut = progressJson("running",
                "step 1\nstep 2\nstep 3\nstep 4\nstep 5 with much more text appended", "thinking...", "ag-1", 2, null);
        Assert.assertEquals(
                ToolCallAgentView.layoutSignature(call(), result(shortOut)),
                ToolCallAgentView.layoutSignature(call(), result(longOut)));
    }

    @Test
    public void signatureEqualWhenThinkingTextGrows() {
        String shortThink = progressJson("running", "out", "short", "ag-1", 2, null);
        String longThink = progressJson("running", "out", "short\nmuch longer thinking block", "ag-1", 2, null);
        Assert.assertEquals(
                ToolCallAgentView.layoutSignature(call(), result(shortThink)),
                ToolCallAgentView.layoutSignature(call(), result(longThink)));
    }

    @Test
    public void signatureDiffersWhenProgressStatusChanges() {
        String running = progressJson("running", "out", "t", "ag-1", 2, null);
        String done = progressJson("done", "out", "t", "ag-1", 2, null);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(running)),
                ToolCallAgentView.layoutSignature(call(), result(done)));
        String noStatus = progressJson("", "out", "t", "ag-1", 2, null);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(running)),
                ToolCallAgentView.layoutSignature(call(), result(noStatus)));
    }

    @Test
    public void signatureDiffersWhenToolCallCountChanges() {
        String two = progressJson("running", "out", "t", "ag-1", 2, null);
        String five = progressJson("running", "out", "t", "ag-1", 5, null);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(two)),
                ToolCallAgentView.layoutSignature(call(), result(five)));
    }

    @Test
    public void signatureDiffersWhenAgentIdChanges() {
        String one = progressJson("running", "out", "t", "ag-1", 2, null);
        String two = progressJson("running", "out", "t", "ag-2", 2, null);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(one)),
                ToolCallAgentView.layoutSignature(call(), result(two)));
    }

    @Test
    public void signatureDiffersWhenOutputPresenceFlips() {
        String withOutput = progressJson("running", "out", "t", "ag-1", 2, null);
        String noOutput = progressJson("running", "", "t", "ag-1", 2, null);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(withOutput)),
                ToolCallAgentView.layoutSignature(call(), result(noOutput)));
    }

    @Test
    public void signatureDiffersWhenThinkingPresenceFlips() {
        String withThinking = progressJson("running", "out", "t", "ag-1", 2, null);
        String noThinking = progressJson("running", "out", "", "ag-1", 2, null);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(withThinking)),
                ToolCallAgentView.layoutSignature(call(), result(noThinking)));
    }

    @Test
    public void signatureDiffersWhenReviewStateChanges() {
        String content = progressJson("running", "out", "t", "ag-1", 2, null);
        ToolResult plain = result(content, false, "");
        ToolResult reviewing = result(content, false, "running");
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), plain),
                ToolCallAgentView.layoutSignature(call(), reviewing));
    }

    @Test
    public void signatureDiffersWhenErrorFlips() {
        String content = progressJson("running", "out", "t", "ag-1", 2, null);
        ToolResult ok = result(content, false, "");
        ToolResult failed = result(content, true, "");
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), ok),
                ToolCallAgentView.layoutSignature(call(), failed));
    }

    @Test
    public void signatureDiffersWhenNestedToolCallCountChanges() {
        String none = progressJson("running", "out", "t", "ag-1", 0, new JSONArray());
        JSONArray two = new JSONArray();
        try {
            two.put(new JSONObject().put("name", "file_read"));
            two.put(new JSONObject().put("name", "file_write"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String withTwo = progressJson("running", "out", "t", "ag-1", 0, two);
        Assert.assertNotEquals(
                ToolCallAgentView.layoutSignature(call(), result(none)),
                ToolCallAgentView.layoutSignature(call(), result(withTwo)));
    }

    @Test
    public void signatureIsDeterministic() {
        String content = progressJson("running", "out", "t", "ag-1", 2, null);
        Assert.assertEquals(
                ToolCallAgentView.layoutSignature(call(), result(content)),
                ToolCallAgentView.layoutSignature(call(), result(content)));
    }

    @Test
    public void signatureOfNullResultIsDeterministicAndDiffersFromRealResult() {
        String a = ToolCallAgentView.layoutSignature(call(), null);
        String b = ToolCallAgentView.layoutSignature(call(), null);
        Assert.assertEquals(a, b);
        String content = progressJson("running", "out", "t", "ag-1", 2, null);
        Assert.assertNotEquals(a, ToolCallAgentView.layoutSignature(call(), result(content)));
    }

    @Test
    public void signatureIgnoresRawNonProgressContentStructure() {
        String plainText = "some plain streamed output";
        String longerText = "some plain streamed output with more text";
        Assert.assertEquals(
                ToolCallAgentView.layoutSignature(call(), result(plainText)),
                ToolCallAgentView.layoutSignature(call(), result(longerText)));
    }
}
