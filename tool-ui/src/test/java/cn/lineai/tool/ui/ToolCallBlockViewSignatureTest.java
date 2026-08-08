package cn.lineai.tool.ui;

import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import org.junit.Assert;
import org.junit.Test;

public final class ToolCallBlockViewSignatureTest {

    private static ToolResult result(String content) {
        return ToolResult.withReview("1", "read", content, false, "", "", "");
    }

    @Test
    public void contentSignatureChangesWhenOnlyContentChanges() {
        String a = ToolCallBlockView.contentSignature(result("AAA"));
        String b = ToolCallBlockView.contentSignature(result("BBB"));
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void contentSignatureEqualsResultContent() {
        ToolResult r = result("some streamed text");
        Assert.assertEquals(r.getContent(), ToolCallBlockView.contentSignature(r));
    }

    @Test
    public void contentSignatureOfNullIsEmpty() {
        Assert.assertEquals("", ToolCallBlockView.contentSignature(null));
    }

    @Test
    public void structureSignatureEqualWhenOnlyContentChanges() {
        ToolCall call = new ToolCall("call-1", "read", "{\"path\":\"/a.txt\"}");
        String a = ToolCallBlockView.structureSignature("proj", call, result("AAA"));
        String b = ToolCallBlockView.structureSignature("proj", call, result("BBB"));
        Assert.assertEquals(a, b);
    }

    @Test
    public void structureSignatureDiffersWhenErrorFlagFlips() {
        ToolCall call = new ToolCall("call-1", "read", "{}");
        ToolResult ok = ToolResult.withReview("1", "read", "out", false, "", "", "");
        ToolResult err = ToolResult.withReview("1", "read", "out", true, "", "", "");
        Assert.assertNotEquals(
                ToolCallBlockView.structureSignature("p", call, ok),
                ToolCallBlockView.structureSignature("p", call, err));
    }

    @Test
    public void structureSignatureDiffersWhenReviewStateChanges() {
        ToolCall call = new ToolCall("call-1", "read", "{}");
        ToolResult pending = ToolResult.withReview("1", "read", "out", false, "", "", "");
        ToolResult running = ToolResult.withReview("1", "read", "out", false, "", "running", "");
        Assert.assertNotEquals(
                ToolCallBlockView.structureSignature("p", call, pending),
                ToolCallBlockView.structureSignature("p", call, running));
    }

    @Test
    public void structureSignatureDiffersWhenToolCallIdChanges() {
        ToolResult r = result("out");
        String a = ToolCallBlockView.structureSignature("p", new ToolCall("id-1", "read", "{}"), r);
        String b = ToolCallBlockView.structureSignature("p", new ToolCall("id-2", "read", "{}"), r);
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void structureSignatureDiffersWhenToolCallNameChanges() {
        ToolResult r = result("out");
        String a = ToolCallBlockView.structureSignature("p", new ToolCall("id-1", "read", "{}"), r);
        String b = ToolCallBlockView.structureSignature("p", new ToolCall("id-1", "write", "{}"), r);
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void structureSignatureDiffersWhenToolCallArgumentsChange() {
        ToolResult r = result("out");
        String a = ToolCallBlockView.structureSignature("p", new ToolCall("id-1", "read", "{}"), r);
        String b = ToolCallBlockView.structureSignature("p", new ToolCall("id-1", "read", "{\"x\":1}"), r);
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void structureSignatureDiffersWhenDiffIdChanges() {
        ToolCall call = new ToolCall("id-1", "write", "{}");
        ToolResult noDiff = ToolResult.withReview("1", "write", "out", false, "", "", "");
        ToolResult withDiff = ToolResult.withReview("1", "write", "out", false, "diff-9", "", "");
        Assert.assertNotEquals(
                ToolCallBlockView.structureSignature("p", call, noDiff),
                ToolCallBlockView.structureSignature("p", call, withDiff));
    }

    @Test
    public void structureSignatureDiffersWhenReviewMessageChanges() {
        ToolCall call = new ToolCall("id-1", "write", "{}");
        ToolResult none = ToolResult.withReview("1", "write", "out", false, "", "", "");
        ToolResult withMsg = ToolResult.withReview("1", "write", "out", false, "", "", "reviewed");
        Assert.assertNotEquals(
                ToolCallBlockView.structureSignature("p", call, none),
                ToolCallBlockView.structureSignature("p", call, withMsg));
    }

    @Test
    public void structureSignatureDiffersWhenProjectPathChanges() {
        ToolCall call = new ToolCall("id-1", "read", "{}");
        ToolResult r = result("out");
        String a = ToolCallBlockView.structureSignature("/proj/a", call, r);
        String b = ToolCallBlockView.structureSignature("/proj/b", call, r);
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void structureSignatureIsDeterministic() {
        ToolCall call = new ToolCall("id-1", "read", "{\"path\":\"/a.txt\"}");
        ToolResult r = result("out");
        Assert.assertEquals(
                ToolCallBlockView.structureSignature("p", call, r),
                ToolCallBlockView.structureSignature("p", call, r));
    }
}
