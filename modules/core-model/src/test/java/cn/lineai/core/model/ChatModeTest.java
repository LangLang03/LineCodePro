package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChatModeTest {

    @Test public void normalizesChat() {
        assertEquals(ChatMode.CHAT, ChatMode.normalize("chat"));
    }

    @Test public void normalizesPlan() {
        assertEquals(ChatMode.PLAN, ChatMode.normalize("plan"));
    }

    @Test public void normalizesControl() {
        assertEquals(ChatMode.CONTROL, ChatMode.normalize("control"));
    }

    @Test public void unknownFallsBackToAgent() {
        assertEquals(ChatMode.AGENT, ChatMode.normalize("unknown"));
        assertEquals(ChatMode.AGENT, ChatMode.normalize(null));
    }
}
