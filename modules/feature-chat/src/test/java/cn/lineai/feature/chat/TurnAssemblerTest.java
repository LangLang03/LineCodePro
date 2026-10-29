package cn.lineai.feature.chat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TurnAssemblerTest {

    @Test
    public void foldsOneUserTurnIntoUserAndAssistantRows() {
        Message user = message("u1", Role.USER, Message.Status.COMPLETE, 1L, "Fix the build");
        Message assistant = assistant("a1", Message.Status.COMPLETE, 2L,
                blocks("Let me look", "checking the logs"), tool("call-1", "read_file"),
                tool("call-2", "shell"));
        Message result1 = toolResult("t1", "call-1", Message.Status.COMPLETE, "file body");
        Message result2 = toolResult("t2", "call-2", Message.Status.FAILED, "boom");
        Message finalAnswer = message("a2", Role.ASSISTANT, Message.Status.COMPLETE, 9L, "Done.");

        List<TurnAssembler.Row> rows = TurnAssembler.assemble(
                Arrays.asList(user, assistant, result1, result2, finalAnswer),
                Collections.<String>emptySet(), false);

        assertEquals(2, rows.size());
        assertTrue(rows.get(0).user);
        assertEquals("Fix the build", rows.get(0).userMessage.plainText());

        TurnAssembler.Row turn = rows.get(1);
        assertFalse(turn.user);
        assertEquals("Done.", turn.answer.plainText());
        assertEquals(3, turn.process.size());
        assertEquals(TurnAssembler.ITEM_REASONING, turn.process.get(0).kind);
        assertEquals("checking the logs", turn.process.get(0).text);
        assertEquals(TurnAssembler.ITEM_MARKDOWN, turn.process.get(1).kind);
        assertEquals("Let me look", turn.process.get(1).text);
        assertEquals(TurnAssembler.ITEM_TOOLS, turn.process.get(2).kind);
        assertEquals(2, turn.process.get(2).tools.size());
        assertEquals("file body", turn.process.get(2).tools.get(0).result);
        assertTrue(turn.process.get(2).tools.get(1).failed);
        assertFalse(turn.live);
        assertTrue(turn.processingFinishedAt > turn.processingStartedAt);
    }

    @Test
    public void consecutiveToolRoundsFoldIntoOneGroupAndHideIntermediateThinking() {
        Message user = message("u1", Role.USER, Message.Status.COMPLETE, 1L, "go");
        Message round1 = assistant("a1", Message.Status.COMPLETE, 2L,
                blocks("round one", "thinking one"), tool("call-1", "read_file"));
        Message tool1 = toolResult("t1", "call-1", Message.Status.COMPLETE, "ok");
        // Only thinking between tool calls: it is folded into the group, never rendered.
        Message round2 = assistant("a2", Message.Status.COMPLETE, 4L,
                Arrays.asList(ContentBlock.reasoning("thinking two")), tool("call-2", "shell"));
        Message tool2 = toolResult("t2", "call-2", Message.Status.COMPLETE, "ok");
        Message round3 = assistant("a3", Message.Status.COMPLETE, 6L,
                Arrays.asList(ContentBlock.reasoning("thinking three")), tool("call-3", "glob"));
        Message tool3 = toolResult("t3", "call-3", Message.Status.COMPLETE, "*.java");
        Message answer = message("a4", Role.ASSISTANT, Message.Status.COMPLETE, 9L, "done");

        TurnAssembler.Row turn = TurnAssembler.assemble(
                Arrays.asList(user, round1, tool1, round2, tool2, round3, tool3, answer),
                Collections.<String>emptySet(), false).get(1);

        // Same shape as the legacy ConversationTimeline: [thinking][prose][one tool group].
        assertEquals(3, turn.process.size());
        assertEquals(TurnAssembler.ITEM_REASONING, turn.process.get(0).kind);
        assertEquals("thinking one", turn.process.get(0).text);
        assertEquals(TurnAssembler.ITEM_MARKDOWN, turn.process.get(1).kind);
        assertEquals(TurnAssembler.ITEM_TOOLS, turn.process.get(2).kind);
        assertEquals(3, turn.process.get(2).tools.size());
        assertEquals("done", turn.answer.plainText());
    }

    @Test
    public void marksUnpairedLiveCallsRunningAndPendingOnesAwaiting() {
        Message user = message("u1", Role.USER, Message.Status.COMPLETE, 1L, "go");
        Message assistant = assistant("a1", Message.Status.COMPLETE, 2L, null,
                tool("call-1", "write_file"));
        List<TurnAssembler.Row> rows = TurnAssembler.assemble(
                Arrays.asList(user, assistant), Collections.singleton("call-1"), true);

        TurnAssembler.Row turn = rows.get(1);
        assertTrue(turn.live);
        assertTrue(turn.running);
        assertTrue(turn.pending);
        assertEquals(1, turn.process.get(0).tools.size());
        assertTrue(turn.process.get(0).tools.get(0).running);
        assertNull(turn.answer);
    }

    @Test
    public void pairsPersistedDiffIdAndReviewStateWithTheCall() {
        Message user = message("u1", Role.USER, Message.Status.COMPLETE, 1L, "edit it");
        Message assistant = assistant("a1", Message.Status.COMPLETE, 2L, null,
                tool("call-1", "edit_file"));
        Message result = Message.builder().id("t1").role(Role.TOOL)
                .status(Message.Status.COMPLETE).seq(1L).tool("call-1", "edit_file")
                .addBlock(ContentBlock.text("edited"))
                .addBlock(ContentBlock.diff("diff-9"))
                .reviewState("pending").times(3L, 3L).build();

        TurnAssembler.Row turn = TurnAssembler.assemble(
                Arrays.asList(user, assistant, result),
                Collections.<String>emptySet(), false).get(1);
        TurnAssembler.Item tools = turn.process.get(0);
        assertEquals("diff-9", tools.tools.get(0).diffId);
        assertEquals("pending", tools.tools.get(0).reviewState);
    }

    @Test
    public void failedAnswerBecomesProcessProseInsteadOfAnAnswer() {
        Message user = message("u1", Role.USER, Message.Status.COMPLETE, 1L, "go");
        Message failed = message("a1", Role.ASSISTANT, Message.Status.FAILED, 2L,
                "Generation failed: timeout");
        List<TurnAssembler.Row> rows = TurnAssembler.assemble(
                Arrays.asList(user, failed), Collections.<String>emptySet(), false);

        TurnAssembler.Row turn = rows.get(1);
        assertNull(turn.answer);
        assertEquals(1, turn.process.size());
        assertEquals(TurnAssembler.ITEM_MARKDOWN, turn.process.get(0).kind);
        assertEquals("Generation failed: timeout", turn.process.get(0).text);
    }

    @Test
    public void skipsToolAndHiddenMessages() {
        Message user = message("u1", Role.USER, Message.Status.COMPLETE, 1L, "go");
        Message hidden = message("h1", Role.ASSISTANT, Message.Status.COMPLETE, 2L, "summary");
        Message tool = toolResult("t1", "call-1", Message.Status.COMPLETE, "body");
        List<TurnAssembler.Row> rows = TurnAssembler.assemble(
                Arrays.asList(user, hidden.toBuilder().visibility(Message.Visibility.HIDDEN).build(),
                        tool),
                Collections.<String>emptySet(), false);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).user);
    }

    private static Message message(String id, Role role, Message.Status status, long time,
                                   String text) {
        return Message.builder().id(id).role(role).status(status).seq(1L)
                .addBlock(ContentBlock.text(text)).times(time, time).build();
    }

    private static Message assistant(String id, Message.Status status, long time,
                                     List<ContentBlock> blocks, ToolCallRecord... calls) {
        Message.Builder builder = Message.builder().id(id).role(Role.ASSISTANT).status(status)
                .seq(1L).times(time, time).toolCalls(Arrays.asList(calls));
        if (blocks != null) {
            for (ContentBlock block : blocks) {
                builder.addBlock(block);
            }
        }
        return builder.build();
    }

    private static Message toolResult(String id, String callId, Message.Status status,
                                      String text) {
        return Message.builder().id(id).role(Role.TOOL).status(status).seq(1L)
                .tool(callId, "read_file").addBlock(ContentBlock.text(text))
                .times(3L, 3L).build();
    }

    private static List<ContentBlock> blocks(String text, String reasoning) {
        return Arrays.asList(ContentBlock.reasoning(reasoning), ContentBlock.text(text));
    }

    private static ToolCallRecord tool(String callId, String name) {
        return new ToolCallRecord(callId, name, "{\"path\":\"a.txt\"}");
    }
}
