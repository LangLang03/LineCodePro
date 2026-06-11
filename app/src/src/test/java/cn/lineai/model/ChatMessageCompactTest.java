package cn.lineai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public final class ChatMessageCompactTest {
    @Test
    public void compactProgressMessageIsHiddenFromContext() {
        ChatMessage message = ChatMessage.compactProgress("compact_1", ChatMessage.COMPACT_STATUS_RUNNING);

        assertEquals(ChatMessage.Role.ASSISTANT, message.getRole());
        assertTrue(message.isCompactBlock());
        assertTrue(message.isStreaming());
        assertTrue(message.isExcludeFromContext());
        assertEquals(ChatMessage.COMPACT_STATUS_RUNNING, message.getCompactStatus());
    }

    @Test
    public void responseInputItemSurvivesMessageCopies() {
        ChatMessage message = new ChatMessage("m1", ChatMessage.Role.USER, "fallback", false)
                .withResponseInputItemJson("{\"type\":\"compaction\",\"id\":\"cmp_1\"}");

        ChatMessage copied = message.withExcludeFromContext(true)
                .withContent("next fallback", "", false);

        assertEquals("{\"type\":\"compaction\",\"id\":\"cmp_1\"}", copied.getResponseInputItemJson());
        assertTrue(copied.isExcludeFromContext());
        assertFalse(copied.isCompactBlock());
    }

    @Test
    public void attachmentsSurviveMessageCopies() {
        InputAttachment attachment = new InputAttachment("Main.java", "/repo/Main.java", InputAttachment.SOURCE_LOCAL);
        ChatMessage message = new ChatMessage("m1", ChatMessage.Role.USER, "hello", false,
                Collections.singletonList(attachment));

        ChatMessage copied = message.withContent("next", "", false)
                .withExcludeFromContext(true);

        assertEquals(1, copied.getAttachments().size());
        assertEquals("/repo/Main.java", copied.getAttachments().get(0).getPath());
        assertTrue(copied.isExcludeFromContext());
    }
}
