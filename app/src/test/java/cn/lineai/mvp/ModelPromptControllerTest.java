package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public final class ModelPromptControllerTest {
    @Test
    public void requestMessagesAddInterruptedResultForMissingToolCallOutput() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(assistantWithToolCalls(new ToolCall("call_missing", "shell_execute", "{}")));
        messages.add(new ChatMessage("u1", ChatMessage.Role.USER, "继续", false));

        ArrayList<ChatMessage> repaired = ModelPromptController.completeToolCallPairsForRequest(
                messages,
                "上次生成已中断。"
        );

        Assert.assertEquals(3, repaired.size());
        Assert.assertEquals(ChatMessage.Role.ASSISTANT, repaired.get(0).getRole());
        Assert.assertEquals(ChatMessage.Role.TOOL, repaired.get(1).getRole());
        Assert.assertEquals("call_missing", repaired.get(1).getToolCallId());
        Assert.assertEquals("shell_execute", repaired.get(1).getToolName());
        Assert.assertEquals("上次生成已中断。", repaired.get(1).getContent());
        Assert.assertTrue(repaired.get(1).isError());
        Assert.assertEquals(ChatMessage.Role.USER, repaired.get(2).getRole());
    }

    @Test
    public void requestMessagesMoveExistingToolResultNextToAssistantToolCall() {
        ChatMessage assistant = assistantWithToolCalls(new ToolCall("call_read", "file_read", "{}"));
        ChatMessage user = new ChatMessage("u1", ChatMessage.Role.USER, "继续", false);
        ChatMessage result = ChatMessage.toolResult("t1", "文件内容", "call_read", "file_read", false);

        ArrayList<ChatMessage> repaired = ModelPromptController.completeToolCallPairsForRequest(
                Arrays.asList(assistant, user, result),
                "上次生成已中断。"
        );

        Assert.assertEquals(3, repaired.size());
        Assert.assertSame(assistant, repaired.get(0));
        Assert.assertSame(result, repaired.get(1));
        Assert.assertSame(user, repaired.get(2));
    }

    @Test
    public void requestMessagesDropOrphanToolResultWithoutMatchingAssistantCall() {
        ChatMessage user = new ChatMessage("u1", ChatMessage.Role.USER, "继续", false);
        ChatMessage orphan = ChatMessage.toolResult("t1", "迟到结果", "call_orphan", "file_read", false);

        ArrayList<ChatMessage> repaired = ModelPromptController.completeToolCallPairsForRequest(
                Arrays.asList(user, orphan),
                "上次生成已中断。"
        );

        Assert.assertEquals(1, repaired.size());
        Assert.assertSame(user, repaired.get(0));
    }

    private static ChatMessage assistantWithToolCalls(ToolCall... calls) {
        return new ChatMessage("a1", ChatMessage.Role.ASSISTANT, "", "", false,
                false, false,
                Arrays.asList(calls),
                Collections.emptyList(),
                "",
                "",
                false);
    }
}
