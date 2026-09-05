package cn.lineai.ui.model;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationTimelineTest {
    private ChatMessage text(String id, String text) { return new ChatMessage(id, ChatMessage.Role.ASSISTANT, text, false); }
    private ChatMessage tools(String id, String text, String... names) {
        java.util.ArrayList<ToolCall> calls = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) calls.add(new ToolCall(id + i, names[i], "{}"));
        return text(id, text).withToolCalls(calls, false);
    }
    @Test public void adjacentCallsGroupAcrossMessagesAndToolTypes() {
        List<ConversationTimeline.Row> rows = ConversationTimeline.build(Arrays.asList(
                tools("a", "", "file_read", "file_edit"), tools("b", "", "shell_execute"), text("final", "Ready.")));
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).process.size());
        assertEquals(3, rows.get(0).process.get(0).operations.size());
        assertEquals("Ready.", rows.get(0).answer.getContent());
    }
    @Test public void internalReasoningDoesNotSplitConsecutiveToolsAndKeepsItsOrder() {
        ChatMessage second = tools("b", "", "file_edit").withContent("", "The file needs an edit.", false);
        ConversationTimeline.Row row = ConversationTimeline.build(Arrays.asList(tools("a", "", "file_read"), second, text("final", "Done."))).get(0);
        assertEquals(1, row.process.size());
        assertEquals(2, row.process.get(0).operations.size());
        assertEquals(3, row.process.get(0).steps.size());
        assertEquals("a0", row.process.get(0).steps.get(0).operations.get(0).call.getId());
        assertEquals("The file needs an edit.", row.process.get(0).steps.get(1).text);
        assertEquals("b0", row.process.get(0).steps.get(2).operations.get(0).call.getId());
    }
    @Test public void actualAssistantProseSplitsGroupsInChronologicalOrder() {
        ConversationTimeline.Row row = ConversationTimeline.build(Arrays.asList(
                tools("a", "Checking.", "file_read"), text("b", "Now editing."), tools("c", "", "file_edit"), text("end", "Done."))).get(0);
        assertEquals(4, row.process.size());
        assertEquals("Checking.", row.process.get(0).text);
        assertTrue(row.process.get(1).isTools());
        assertEquals("Now editing.", row.process.get(2).text);
        assertTrue(row.process.get(3).isTools());
        assertEquals("end", row.answer.getId());
    }
    @Test public void toolOnlyChainDoesNotCreateVisibleThinkingBlocks() {
        ChatMessage first=tools("a","","file_read").withContent("","First private reasoning",false);
        ChatMessage second=tools("b","","file_edit").withContent("","Second private reasoning",false);
        ConversationTimeline.Row row=ConversationTimeline.build(Arrays.asList(first,second,text("final","Done."))).get(0);
        assertEquals(1,row.process.size());
        assertTrue(row.process.get(0).isTools());
        assertEquals(2,row.process.get(0).operations.size());
        assertFalse(row.process.get(0).reasoning);
    }
    @Test public void usersAndModelSwitchesAreHardBoundaries() {
        List<ConversationTimeline.Row> rows = ConversationTimeline.build(Arrays.asList(
                tools("a", "", "file_read"), ChatMessage.modelSwitchNotice("n", "old", "new"), tools("b", "", "file_read"),
                new ChatMessage("u", ChatMessage.Role.USER, "More", false), tools("c", "", "file_read")));
        assertEquals(5, rows.size());
        assertEquals("a", rows.get(0).first.getId());
        assertEquals("b", rows.get(2).first.getId());
    }
    @Test public void retriesAndErrorsStayInOneProcessingSection() {
        ChatMessage retry = ChatMessage.retryNotice("retry", "Retry 2/3: Connection reset");
        ChatMessage failure = text("failed", "Model communication failed").withToolReview("", "", "", true, "Model communication failed");
        List<ConversationTimeline.Row> rows = ConversationTimeline.build(Arrays.asList(
                tools("a", "", "file_read"), retry, tools("b", "", "shell_execute"), failure));
        assertEquals(1, rows.size());
        assertNull(rows.get(0).answer);
        assertEquals("a", rows.get(0).first.getId());
        assertEquals(retry.getContent(), rows.get(0).process.get(1).text);
        assertEquals(failure.getContent(), rows.get(0).process.get(3).text);
        rows = ConversationTimeline.build(Arrays.asList(retry, tools("b", "", "shell_execute"), text("done", "Recovered")));
        assertEquals(1, rows.size());
        assertEquals("Recovered", rows.get(0).answer.getContent());
        assertTrue(retry.isExcludeFromContext());
        assertFalse(retry.isModelSwitchNotification());
    }
    @Test public void streamAppendKeepsFirstGroupIdentityAndFinalOutside() {
        ChatMessage first = tools("a", "", "file_read");
        String id = ConversationTimeline.build(Collections.singletonList(first)).get(0).process.get(0).id;
        ChatMessage finalStream = text("last", "The fix").withContent("The fix", "", true);
        ConversationTimeline.Row row = ConversationTimeline.build(Arrays.asList(first, tools("b", "", "file_edit"), finalStream)).get(0);
        assertEquals(id, row.process.get(0).id);
        assertEquals(2, row.process.get(0).operations.size());
        assertTrue(row.answer.isStreaming());
        assertEquals("a", row.first.getId());
    }
    @Test public void timedOutputLeavesProcessingOnlyAfterTheBoundary() {
        ChatMessage first=tools("a","","shell_execute").withProcessingTimes(1000,0);
        ChatMessage output=text("last","Answer").withContent("Answer","",true).withProcessingTimes(1000,0);
        ConversationTimeline.Row row=ConversationTimeline.build(Arrays.asList(first,output)).get(0);
        assertNull(row.answer);
        row=ConversationTimeline.build(Arrays.asList(first.withProcessingTimes(1000,32000),output.withProcessingTimes(1000,32000))).get(0);
        assertEquals("Answer",row.answer.getContent());
        assertEquals(32000,row.processingFinishedAt);
    }
    @Test public void pendingAndFailedResultsStayAttachedToTheirOwnCall() {
        ChatMessage call = tools("a", "", "shell_execute").withToolResults(Collections.singletonList(
                ToolResult.withReview("a0", "shell_execute", "", false, "", "pending", "")));
        ConversationTimeline.Row row = ConversationTimeline.build(Collections.singletonList(call)).get(0);
        assertTrue(row.pending); assertNull(row.answer);
        ChatMessage failed = call.withToolResults(Collections.singletonList(ToolResult.of("a0", "shell_execute", "Failed", true)));
        row = ConversationTimeline.build(Arrays.asList(failed, text("f", "Build failed."))).get(0);
        assertFalse(row.pending); assertFalse(row.running);
        assertTrue(row.process.get(0).operations.get(0).result.isError());
    }
    @Test public void reasoningWithoutToolsRemainsAnOrdinaryMessage() {
        ChatMessage message = new ChatMessage("r", ChatMessage.Role.ASSISTANT, "Answer", "Reasoning", false);
        ConversationTimeline.Row row = ConversationTimeline.build(Collections.singletonList(message)).get(0);
        assertFalse(row.isTurn); assertNull(row.answer);
        assertEquals("Reasoning", row.process.get(0).text); assertTrue(row.process.get(0).reasoning);
    }
    @Test public void ordinaryChatAndHiddenMessagesRemainOrdinary() {
        List<ConversationTimeline.Row> rows = ConversationTimeline.build(Arrays.asList(text("a", "Hello"),
                ChatMessage.toolResult("t", "Secret read output", "c", "file_read", false), text("b", "World")));
        assertEquals(2, rows.size()); assertFalse(rows.get(0).isTurn); assertFalse(rows.get(1).isTurn);
    }
}
