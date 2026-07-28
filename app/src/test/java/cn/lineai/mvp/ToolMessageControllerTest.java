package cn.lineai.mvp;

import cn.lineai.model.ChatMessage;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public final class ToolMessageControllerTest {
    @Test
    public void terminationAddsToolResultsForAllUnfinishedToolCalls() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("a1", ChatMessage.Role.ASSISTANT, "", "", false,
                false, false,
                Arrays.asList(
                        new ToolCall("call_shell", "shell_execute", "{}"),
                        new ToolCall("call_read", "file_read", "{}")
                ),
                new ArrayList<>(),
                "",
                "",
                false));
        ToolMessageController controller = new ToolMessageController(messages, new IncrementingIdProvider());

        controller.addTerminatedResultsForUnfinishedToolCalls("用户已停止生成。");

        Assert.assertEquals(3, messages.size());
        ChatMessage shellResult = toolMessage(messages, "call_shell");
        ChatMessage readResult = toolMessage(messages, "call_read");
        Assert.assertNotNull(shellResult);
        Assert.assertNotNull(readResult);
        Assert.assertTrue(shellResult.isError());
        Assert.assertTrue(readResult.isError());
        Assert.assertEquals("用户已停止生成。", shellResult.getContent());
        Assert.assertEquals("用户已停止生成。", readResult.getContent());
    }

    @Test
    public void terminationDoesNotReplaceCompletedToolResult() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("a1", ChatMessage.Role.ASSISTANT, "", "", false,
                false, false,
                Arrays.asList(new ToolCall("call_read", "file_read", "{}")),
                new ArrayList<>(),
                "",
                "",
                false));
        messages.add(ChatMessage.toolResult("t1", "done", "call_read", "file_read", false));
        ToolMessageController controller = new ToolMessageController(messages, new IncrementingIdProvider());

        controller.addTerminatedResultsForUnfinishedToolCalls("用户已停止生成。");

        Assert.assertEquals(2, messages.size());
        ChatMessage result = toolMessage(messages, "call_read");
        Assert.assertFalse(result.isError());
        Assert.assertEquals("done", result.getContent());
    }

    @Test
    public void terminationReplacesRunningToolResult() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("a1", ChatMessage.Role.ASSISTANT, "", "", false,
                false, false,
                Arrays.asList(new ToolCall("call_shell", "shell_execute", "{}")),
                new ArrayList<>(),
                "",
                "",
                false));
        messages.add(ChatMessage.toolResult("t1", "partial", "call_shell", "shell_execute", false, "", "running", ""));
        ToolMessageController controller = new ToolMessageController(messages, new IncrementingIdProvider());

        controller.addTerminatedResultsForUnfinishedToolCalls("用户已停止生成。");

        Assert.assertEquals(2, messages.size());
        ChatMessage result = toolMessage(messages, "call_shell");
        Assert.assertTrue(result.isError());
        Assert.assertEquals("用户已停止生成。", result.getContent());
        Assert.assertEquals("", result.getReviewState());
    }

    @Test
    public void rejectReviewMarksToolResultAsErrorAndRewritesContent() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.toolResult(
                "t1",
                "Successfully edited demo.txt (1 match(es) replaced)",
                "call_edit",
                "file_edit",
                false,
                "diff_1",
                "",
                ""
        ));
        ToolMessageController controller = new ToolMessageController(messages, new IncrementingIdProvider());

        controller.updateToolReview("call_edit", "diff_1", "rejected", "Reverted change to demo.txt");

        ChatMessage result = toolMessage(messages, "call_edit");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isError());
        Assert.assertEquals("rejected", result.getReviewState());
        Assert.assertEquals("diff_1", result.getDiffId());
        Assert.assertTrue(result.getContent().contains("User rejected this change"));
        Assert.assertTrue(result.getContent().contains("Reverted change to demo.txt"));
        Assert.assertFalse(result.getContent().contains("Successfully edited"));
    }

    @Test
    public void acceptReviewKeepsSuccessContent() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.toolResult(
                "t1",
                "Successfully edited demo.txt (1 match(es) replaced)",
                "call_edit",
                "file_edit",
                false,
                "diff_1",
                "",
                ""
        ));
        ToolMessageController controller = new ToolMessageController(messages, new IncrementingIdProvider());

        controller.updateToolReview("call_edit", "diff_1", "accepted", "");

        ChatMessage result = toolMessage(messages, "call_edit");
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isError());
        Assert.assertEquals("accepted", result.getReviewState());
        Assert.assertEquals("Successfully edited demo.txt (1 match(es) replaced)", result.getContent());
    }

    private static ChatMessage toolMessage(ArrayList<ChatMessage> messages, String toolCallId) {
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                return message;
            }
        }
        return null;
    }

    private static final class IncrementingIdProvider implements ToolMessageController.IdProvider {
        private int value;

        @Override
        public String nextId() {
            value++;
            return "tool_" + value;
        }
    }
}
