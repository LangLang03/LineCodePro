package cn.lineai.context;
import cn.lineai.model.tool.ToolCall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ContextManagerTest {
    @Test
    public void snapshotEstimatesPercent() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, repeat("a", 400), false));
        messages.add(new ChatMessage("m2", ChatMessage.Role.ASSISTANT, repeat("b", 400), false));

        ContextSnapshot snapshot = new ContextManager().snapshot(messages, 400);

        assertTrue(snapshot.getUsedTokens() > 0);
        assertTrue(snapshot.getPercent() > 0);
    }

    @Test
    public void selectWindowKeepsRecentMessagesWithinBudget() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, repeat("old", 1200), false));
        messages.add(new ChatMessage("m2", ChatMessage.Role.ASSISTANT, "recent answer", false));
        messages.add(new ChatMessage("m3", ChatMessage.Role.USER, "recent question", false));

        List<ChatMessage> selected = new ContextManager().selectWindow(messages, 4096, 3500);

        assertEquals("m2", selected.get(0).getId());
        assertEquals("m3", selected.get(1).getId());
        assertEquals(2, selected.size());
    }

    @Test
    public void reasoningCanBeExcludedFromContextBudget() {
        ChatMessage message = new ChatMessage(
                "m1",
                ChatMessage.Role.ASSISTANT,
                "answer",
                repeat("reasoning", 400),
                false
        );
        ContextManager manager = new ContextManager();

        assertTrue(manager.estimateTokens(message, true) > manager.estimateTokens(message, false));
    }

    @Test
    public void selectWindowKeepsAssistantToolCallsWithToolResults() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("old", ChatMessage.Role.USER, repeat("old", 1600), false));
        messages.add(new ChatMessage("assistant_tools", ChatMessage.Role.ASSISTANT, "", "", false,
                false, false,
                Arrays.asList(
                        new ToolCall("call_a", "file_read", "{}"),
                        new ToolCall("call_b", "shell_execute", "{}")
                ),
                new ArrayList<>(),
                "",
                "",
                false));
        messages.add(ChatMessage.toolResult("tool_a", "A", "call_a", "file_read", false));
        messages.add(ChatMessage.toolResult("tool_b", "B", "call_b", "shell_execute", false));
        messages.add(new ChatMessage("recent", ChatMessage.Role.USER, repeat("recent", 100), false));

        List<ChatMessage> selected = new ContextManager().selectWindow(messages, 4096, 3500);

        assertEquals("assistant_tools", selected.get(0).getId());
        assertEquals("tool_a", selected.get(1).getId());
        assertEquals("tool_b", selected.get(2).getId());
        assertEquals("recent", selected.get(3).getId());
        assertEquals(4, selected.size());
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
