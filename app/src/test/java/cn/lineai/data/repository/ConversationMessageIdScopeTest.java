package cn.lineai.data.repository;

import org.junit.Assert;
import org.junit.Test;

public final class ConversationMessageIdScopeTest {
    @Test
    public void bareLegacyIdsAreScopedToConversation() {
        Assert.assertEquals("convA:m1", ConversationRepository.scopedMessageId("convA", "m1", 0));
        Assert.assertEquals("convB:m1", ConversationRepository.scopedMessageId("convB", "m1", 0));
    }

    @Test
    public void alreadyScopedIdsAreUnchanged() {
        Assert.assertEquals("convA_m1", ConversationRepository.scopedMessageId("convA", "convA_m1", 0));
        Assert.assertEquals("convA:m1", ConversationRepository.scopedMessageId("convA", "convA:m1", 0));
    }

    @Test
    public void emptyIdUsesConversationOrderFallback() {
        Assert.assertEquals("convA:3", ConversationRepository.scopedMessageId("convA", "", 3));
        Assert.assertEquals("convA:3", ConversationRepository.scopedMessageId("convA", null, 3));
    }

    @Test
    public void nonLegacyIdsAreUnchanged() {
        Assert.assertEquals("recovered_tool_x", ConversationRepository.scopedMessageId("convA", "recovered_tool_x", 0));
        Assert.assertEquals("uuid-style-id", ConversationRepository.scopedMessageId("convA", "uuid-style-id", 0));
    }
}
