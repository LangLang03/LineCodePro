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
        Assert.assertEquals("42_m1", firstMessageId);
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
        Assert.assertEquals("c1_m8", store.nextMessageId());
    }

    @Test
    public void messageIdsAreUniqueAcrossConversations() {
        ChatSessionStore store = new ChatSessionStore();
        store.startNewConversation(100L);
        String first = store.nextMessageId();
        store.startNewConversation(200L);
        String second = store.nextMessageId();

        Assert.assertEquals("100_m1", first);
        Assert.assertEquals("200_m1", second);
        Assert.assertNotEquals(first, second);
    }

    @Test
    public void sequenceFromMessageIdParsesScopedAndLegacyForms() {
        Assert.assertEquals(7, ChatSessionStore.sequenceFromMessageId("m7"));
        Assert.assertEquals(8, ChatSessionStore.sequenceFromMessageId("c1_m8"));
        Assert.assertEquals(9, ChatSessionStore.sequenceFromMessageId("c1:m9"));
        Assert.assertEquals(3, ChatSessionStore.sequenceFromMessageId("42_m3_extra"));
        Assert.assertEquals(0, ChatSessionStore.sequenceFromMessageId(""));
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
