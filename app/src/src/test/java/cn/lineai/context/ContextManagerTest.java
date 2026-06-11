package cn.lineai.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
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

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
