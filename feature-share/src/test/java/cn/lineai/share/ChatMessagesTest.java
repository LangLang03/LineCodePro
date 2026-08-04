package cn.lineai.share;

import cn.lineai.model.ChatMessage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatMessagesTest {

    @Test
    public void toMarkdownContainsRoleLabel() {
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("1", ChatMessage.Role.USER, "Hello", false),
            new ChatMessage("2", ChatMessage.Role.ASSISTANT, "Hi there", false)
        );
        String md = ChatMessages.toMarkdown(messages);
        assertTrue(md.contains("## Me"));
        assertTrue(md.contains("Hello"));
        assertTrue(md.contains("## AI"));
        assertTrue(md.contains("Hi there"));
        assertTrue(md.contains(ChatMessages.FOOTER_MD));
    }

    @Test
    public void toPlainTextContainsRoleLabel() {
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("1", ChatMessage.Role.USER, "Test message", false)
        );
        String text = ChatMessages.toPlainText(messages);
        assertTrue(text.contains("【Me】"));
        assertTrue(text.contains("Test message"));
        assertTrue(text.contains(ChatMessages.FOOTER_PLAIN));
    }

    @Test
    public void toMarkdownHandlesEmptyList() {
        String md = ChatMessages.toPlainText(java.util.Collections.emptyList());
        assertTrue(md.contains(ChatMessages.FOOTER_PLAIN));
    }
}
