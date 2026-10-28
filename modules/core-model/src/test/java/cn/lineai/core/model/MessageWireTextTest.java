package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageWireTextTest {

    private Message message() {
        return Message.builder().id("m").conversationId("c").seq(1).role(Role.USER)
                .addBlock(ContentBlock.text("please review"))
                .addBlock(ContentBlock.file("/tmp/notes.txt", "file body"))
                .build();
    }

    @Test public void wireTextIncludesAttachmentNameAndContent() {
        String wire = message().wireText();
        assertTrue(wire.contains("please review"));
        assertTrue(wire.contains("Attachment: notes.txt"));
        assertTrue(wire.contains("file body"));
    }

    @Test public void plainTextStaysFreeOfAttachmentContent() {
        String plain = message().plainText();
        assertEquals("please review", plain);
        assertFalse(plain.contains("file body"));
    }
}
