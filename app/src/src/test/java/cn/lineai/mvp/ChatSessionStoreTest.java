package cn.lineai.mvp;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public final class ChatSessionStoreTest {
    @Test
    public void startsConversationAndTracksGenerationState() {
        ChatSessionStore store = new ChatSessionStore();

        store.startNewConversation(42L);
        String firstMessageId = store.nextMessageId();
        int generationId = store.nextGenerationId();
        store.setStreaming(true);

        Assert.assertEquals("42", store.getCurrentConversationId());
        Assert.assertEquals(42L, store.getCurrentConversationCreatedAt());
        Assert.assertEquals("m1", firstMessageId);
        Assert.assertTrue(store.isActiveGeneration(generationId));

        store.invalidateActiveGeneration();

        Assert.assertFalse(store.isActiveGeneration(generationId));
    }

    @Test
    public void applyConversationResetsNextMessageIdAfterExistingMessages() {
        ChatSessionStore store = new ChatSessionStore();
        ConversationRecord conversation = new ConversationRecord(
                "c1",
                "title",
                "project",
                100L,
                120L,
                true,
                "",
                Arrays.asList(new MessageRecord(
                        "m7",
                        ChatMessage.Role.USER,
                        "hello",
                        "",
                        100L,
                        false,
                        false,
                        false,
                        "",
                        "",
                        false,
                        ""
                ))
        );

        store.applyConversation(conversation);

        Assert.assertEquals("c1", store.getCurrentConversationId());
        Assert.assertEquals("hello", store.messages().get(0).getContent());
        Assert.assertEquals("m8", store.nextMessageId());
    }

    @Test
    public void messageRecordRestoresAttachmentsFromRawJson() {
        MessageRecord record = new MessageRecord(
                "m1",
                ChatMessage.Role.USER,
                "已附加文件",
                "",
                100L,
                false,
                false,
                false,
                "",
                "",
                false,
                "{\"attachments\":[{\"name\":\"Main.java\",\"path\":\"/repo/Main.java\",\"source\":\"local\"}]}"
        );

        ChatMessage message = record.toChatMessage();

        Assert.assertEquals(1, message.getAttachments().size());
        Assert.assertEquals("Main.java", message.getAttachments().get(0).getName());
        Assert.assertEquals("/repo/Main.java", message.getAttachments().get(0).getPath());
        Assert.assertEquals(InputAttachment.SOURCE_LOCAL, message.getAttachments().get(0).getSource());
    }
}
