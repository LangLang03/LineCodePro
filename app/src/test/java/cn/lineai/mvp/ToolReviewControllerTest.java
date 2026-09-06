package cn.lineai.mvp;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ToolReviewControllerTest {
    @Test
    public void acceptingAFileEditKeepsConversationMessagesImmutable() {
        ArrayList<ChatMessage> messages = messagesWithDiff("diff-1");
        FakeDiffStore diffs = new FakeDiffStore();
        diffs.record = new DiffRecord("diff-1", "/workspace/a.txt", "a", "b", true, 1L, false);
        FakeHost host = new FakeHost();
        ToolMessageController toolMessages = new ToolMessageController(messages, () -> "next");
        ToolReviewController controller = new ToolReviewController(
                diffs, toolMessages, new BackgroundTaskRunner(),
                new MainThreadDispatcher(null, true), host);

        controller.review("edit-1", "accepted", "diff-1");

        assertEquals("", messages.get(1).getReviewState());
        assertEquals("accepted", diffs.state);
        assertEquals(1, host.rendered);
        List<ChatMessage> display = controller.applyLocalReviews(messages);
        assertEquals("accepted", display.get(1).getReviewState());
        assertNotSame(messages.get(1), display.get(1));
    }

    @Test
    public void nestedAgentReviewIsAppliedOnlyToDisplayCopy() {
        String nested = "{\"tool_calls\":[{\"id\":\"edit-1\",\"result\":{\"diff_id\":\"diff-1\",\"review_state\":\"\"}}]}";
        ChatMessage outer = ChatMessage.toolResult("tool", nested, "agent-1", "agent", false, "", "", "");
        FakeDiffStore diffs = new FakeDiffStore();
        diffs.record = new DiffRecord("diff-1", "/workspace/a.txt", "a", "b", true, 1L, false, "accepted", "");
        ToolReviewController controller = new ToolReviewController(
                diffs, new ToolMessageController(new ArrayList<>(), () -> "next"),
                new BackgroundTaskRunner(), new MainThreadDispatcher(null, true), new FakeHost());

        List<ChatMessage> display = controller.applyLocalReviews(Collections.singletonList(outer));

        assertFalse(outer.getContent().contains("accepted"));
        assertTrue(display.get(0).getContent().contains("accepted"));
    }

    private static ArrayList<ChatMessage> messagesWithDiff(String diffId) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("assistant", ChatMessage.Role.ASSISTANT, "", false)
                .withToolCalls(Collections.singletonList(new ToolCall("edit-1", "file_edit", "{}")), false));
        messages.add(ChatMessage.toolResult("tool", "done", "edit-1", "file_edit", false, diffId, "", ""));
        return messages;
    }

    private static final class FakeHost implements ToolReviewController.Host {
        int rendered;
        @Override public void refreshFileTreeAfterRevert(String filePath) {}
        @Override public void render() { rendered++; }
    }

    private static final class FakeDiffStore implements DiffStore {
        DiffRecord record;
        String state = "";
        String message = "";
        @Override public DiffRecord recordDiff(String filePath, String oldContent, String newContent, boolean oldExists) { return record; }
        @Override public DiffRecord getDiff(String diffId) {
            if (record == null || !record.getId().equals(diffId)) return null;
            return state.length() == 0 && message.length() == 0 ? record : new DiffRecord(
                    record.getId(), record.getFilePath(), record.getOldContent(), record.getNewContent(),
                    record.isOldExists(), record.getTimestamp(), record.isReverted(), state, message);
        }
        @Override public List<DiffRecord> getDiffChain(String filePath) { return Collections.emptyList(); }
        @Override public DiffRepository.RevertResult revertDiff(String diffId) { return null; }
        @Override public void markReverted(String diffId) {}
        @Override public void setReview(String diffId, String state, String message) {
            this.state = state == null ? "" : state;
            this.message = message == null ? "" : message;
        }
    }
}
