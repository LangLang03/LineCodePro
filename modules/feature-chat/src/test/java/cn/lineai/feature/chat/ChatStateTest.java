package cn.lineai.feature.chat;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Message order is a rendering invariant: sequence wins over arrival order. */
public class ChatStateTest {

    @Test
    public void sortsMessagesBySequence() {
        ChatState state = ChatState.builder()
                .add(message("late", 9))
                .add(message("early", 2))
                .add(message("middle", 5))
                .build();

        assertEquals(3, state.messages.size());
        assertEquals("early", state.messages.get(0).id);
        assertEquals("middle", state.messages.get(1).id);
        assertEquals("late", state.messages.get(2).id);
    }

    @Test
    public void keepsInsertionOrderForEqualSequences() {
        ChatState state = ChatState.builder()
                .add(message("first", 1))
                .add(message("second", 1))
                .build();

        assertEquals("first", state.messages.get(0).id);
        assertEquals("second", state.messages.get(1).id);
    }

    private static Message message(String id, long seq) {
        return Message.builder().id(id).seq(seq).role(Role.ASSISTANT)
                .addBlock(ContentBlock.text(id)).times(seq, seq).build();
    }
}
