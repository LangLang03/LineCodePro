package cn.lineai.ui.model;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** 增量时间线必须与全量构建结果一致，并且只重建真正变化的分组。 */
public final class ConversationTimelineBuilderTest {
    private static ChatMessage user(String id, String text) {
        return new ChatMessage(id, ChatMessage.Role.USER, text, false);
    }

    private static ChatMessage text(String id, String text) {
        return new ChatMessage(id, ChatMessage.Role.ASSISTANT, text, false);
    }

    private static ChatMessage tools(String id, String text, String... names) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < names.length; i++) calls.add(new ToolCall(id + i, names[i], "{}"));
        return text(id, text).withToolCalls(calls, false);
    }

    private static String describe(ConversationTimeline.Row row) {
        StringBuilder builder = new StringBuilder();
        builder.append(row.first.getId()).append('|').append(row.messages.size()).append('|')
                .append(row.isTurn).append('|').append(row.running).append('|').append(row.pending).append('|')
                .append(row.answer == null ? "-" : row.answer.getId()).append('|')
                .append(row.processingStartedAt).append('-').append(row.processingFinishedAt);
        for (ConversationTimeline.Block block : row.process) {
            builder.append(',').append(block.id).append(':').append(block.operations.size())
                    .append(':').append(block.text.length());
        }
        return builder.toString();
    }

    private static List<String> describeAll(List<ConversationTimeline.Row> rows) {
        ArrayList<String> described = new ArrayList<>();
        for (ConversationTimeline.Row row : rows) described.add(describe(row));
        return described;
    }

    @Test
    public void incrementalBuildMatchesFreshBuildAcrossMutations() {
        ConversationTimeline.Builder builder = new ConversationTimeline.Builder();
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(user("u1", "start"));
        messages.add(tools("a", "reading", "file_read"));
        messages.add(text("t", "Working."));

        assertFreshMatches(builder, messages);

        // 流式追加尾部消息内容
        messages.set(2, text("t", "Working on it.").withContent("Working on it.", "thinking", true));
        assertFreshMatches(builder, messages);

        // 新增一轮工具调用
        messages.add(tools("b", "", "file_edit"));
        assertFreshMatches(builder, messages);
        messages.set(3, tools("b", "", "file_edit").withToolResults(Collections.singletonList(
                ToolResult.of("b0", "file_edit", "ok", false))));
        assertFreshMatches(builder, messages);

        // 用户消息是硬边界
        messages.add(user("u2", "more"));
        assertFreshMatches(builder, messages);

        // 中间插入（恢复历史/重试通知）
        messages.add(1, ChatMessage.retryNotice("retry", "Retry 2/3: connection reset"));
        assertFreshMatches(builder, messages);

        // 压缩导致的批量删除
        messages.remove(0);
        messages.remove(0);
        assertFreshMatches(builder, messages);

        messages.clear();
        assertFreshMatches(builder, messages);
    }

    private void assertFreshMatches(ConversationTimeline.Builder builder, List<ChatMessage> messages) {
        List<ConversationTimeline.Row> incremental = new ArrayList<>(builder.build(messages));
        List<ConversationTimeline.Row> fresh = ConversationTimeline.build(messages);
        assertEquals(describeAll(fresh), describeAll(incremental));
    }

    @Test
    public void unchangedGroupsReuseRowInstances() {
        ConversationTimeline.Builder builder = new ConversationTimeline.Builder();
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(user("u1", "start"));
        messages.add(user("u2", "next"));
        messages.add(text("t", "partial"));

        List<ConversationTimeline.Row> first = new ArrayList<>(builder.build(messages));
        assertEquals(3, first.size());

        // 只有尾部消息换了实例：前面的行必须原样复用，长对话滑动/流式时不再重建全部行。
        messages.set(2, text("t", "partial answer grown"));
        List<ConversationTimeline.Row> second = new ArrayList<>(builder.build(messages));

        assertSame(first.get(0), second.get(0));
        assertSame(first.get(1), second.get(1));
        assertNotSame(first.get(2), second.get(2));
    }

    @Test
    public void completedTurnRowsAreReusedWhenTheNextTurnStarts() {
        ConversationTimeline.Builder builder = new ConversationTimeline.Builder();
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(user("u1", "start"));
        messages.add(tools("a", "", "file_read"));
        messages.add(text("t", "done").withProcessingTimes(1000, 2000));

        List<ConversationTimeline.Row> first = new ArrayList<>(builder.build(messages));
        assertEquals(2, first.size());
        assertTrue(first.get(1).isTurn);

        messages.add(user("u2", "again"));
        messages.add(tools("b", "", "shell_execute"));
        messages.add(text("z", "running").withContent("running", "", true));
        List<ConversationTimeline.Row> second = new ArrayList<>(builder.build(messages));

        assertSame(first.get(0), second.get(0));
        assertSame(first.get(1), second.get(1));
        assertEquals(4, second.size());
    }

    @Test
    public void reasoningOwnerAndDiffFilesAreDerivedFromTheRow() {
        ChatMessage thought = text("a", "").withContent("", "checking the repo", false)
                .withToolCalls(Collections.singletonList(new ToolCall("call-1", "file_edit", "{}")), false);
        ChatMessage withDiff = thought.withToolResults(Collections.singletonList(
                ToolResult.withReview("call-1", "file_edit", "ok", false, "diff-1", "", "")));
        ConversationTimeline.Row row = ConversationTimeline.build(Arrays.asList(withDiff, text("z", "done"))).get(0);

        ConversationTimeline.Block tools = row.process.get(0);
        assertTrue(tools.isTools());
        assertEquals(1, row.diffOperations().size());
        assertEquals("diff-1", row.diffOperations().get(0).result.getDiffId());
        assertSame(row.diffOperations(), row.diffOperations());
        assertEquals(1, row.changedFilePaths().size());
        assertSame(row.changedFilePaths(), row.changedFilePaths());

        ChatMessage reasoning = text("r", "answer").withContent("answer", "because", false)
                .withToolCalls(Collections.singletonList(new ToolCall("call-2", "file_read", "{}")), false);
        ConversationTimeline.Row reasoningRow = ConversationTimeline
                .build(Arrays.asList(reasoning, text("z", "done"))).get(0);
        ConversationTimeline.Block reasoningBlock = null;
        for (ConversationTimeline.Block block : reasoningRow.process) {
            for (ConversationTimeline.Block step : block.steps) {
                if (step.reasoning) reasoningBlock = step;
            }
        }
        if (reasoningBlock == null) {
            for (ConversationTimeline.Block block : reasoningRow.process) {
                if (block.reasoning) reasoningBlock = block;
            }
        }
        assertEquals(reasoning, reasoningRow.ownerOfReasoning(reasoningBlock.id));
        assertNull(reasoningRow.ownerOfReasoning("missing"));
    }
}
