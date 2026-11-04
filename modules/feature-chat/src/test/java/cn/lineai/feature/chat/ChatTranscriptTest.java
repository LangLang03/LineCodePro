package cn.lineai.feature.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatTranscriptTest {

    private static final String FOOTER = "*footer*";

    @Test
    public void rendersUserAndAssistantInOrderWithLabels() {
        List<Message> messages = Arrays.asList(
                message(Role.USER, "hello", Message.Visibility.VISIBLE),
                message(Role.ASSISTANT, "hi there", Message.Visibility.VISIBLE));

        assertEquals("## Me\n\nhello\n\n---\n\n## AI\n\nhi there\n\n---\n\n" + FOOTER,
                ChatTranscript.toMarkdown(messages, "Me", "AI", FOOTER));
    }

    @Test
    public void trimsParagraphText() {
        List<Message> messages = Collections.singletonList(
                message(Role.USER, "  wrapped  ", Message.Visibility.VISIBLE));

        assertEquals("## Me\n\nwrapped\n\n---\n\n" + FOOTER,
                ChatTranscript.toMarkdown(messages, "Me", "AI", FOOTER));
    }

    @Test
    public void filtersHiddenToolSystemAndEmptyMessages() {
        List<Message> messages = Arrays.asList(
                message(Role.USER, "visible user", Message.Visibility.VISIBLE),
                message(Role.USER, "hidden user", Message.Visibility.HIDDEN),
                message(Role.ASSISTANT, "hidden assistant", Message.Visibility.HIDDEN),
                message(Role.TOOL, "tool output", Message.Visibility.VISIBLE),
                message(Role.SYSTEM, "system prompt", Message.Visibility.VISIBLE),
                message(Role.USER, "   ", Message.Visibility.VISIBLE),
                message(Role.ASSISTANT, "", Message.Visibility.VISIBLE),
                message(Role.ASSISTANT, "visible assistant", Message.Visibility.VISIBLE));

        assertEquals("## Me\n\nvisible user\n\n---\n\n## AI\n\nvisible assistant\n\n---\n\n"
                        + FOOTER,
                ChatTranscript.toMarkdown(messages, "Me", "AI", FOOTER));
    }

    @Test
    public void ignoresReasoningAndAttachmentBlocks() {
        Message reasoningOnly = Message.builder().role(Role.ASSISTANT)
                .addBlock(ContentBlock.reasoning("thinking"))
                .build();
        Message withAttachment = Message.builder().role(Role.USER)
                .addBlock(ContentBlock.text("look"))
                .addBlock(ContentBlock.file("notes.txt", "attachment body"))
                .build();

        assertEquals("## Me\n\nlook\n\n---\n\n" + FOOTER,
                ChatTranscript.toMarkdown(Arrays.asList(reasoningOnly, withAttachment),
                        "Me", "AI", FOOTER));
    }

    @Test
    public void returnsEmptyStringWhenNothingIsExportable() {
        List<Message> messages = Arrays.asList(
                message(Role.SYSTEM, "system", Message.Visibility.VISIBLE),
                message(Role.USER, "", Message.Visibility.VISIBLE),
                message(Role.ASSISTANT, "hidden", Message.Visibility.HIDDEN));

        assertEquals("", ChatTranscript.toMarkdown(messages, "Me", "AI", FOOTER));
        assertEquals("", ChatTranscript.toMarkdown(Collections.<Message>emptyList(),
                "Me", "AI", FOOTER));
        assertEquals("", ChatTranscript.toMarkdown(null, "Me", "AI", FOOTER));
    }

    @Test
    public void hasContentFollowsExportableMessages() {
        assertFalse(ChatTranscript.hasContent(Collections.<Message>emptyList()));
        assertFalse(ChatTranscript.hasContent(null));
        assertFalse(ChatTranscript.hasContent(Collections.singletonList(
                message(Role.USER, "hidden", Message.Visibility.HIDDEN))));
        assertFalse(ChatTranscript.hasContent(Collections.singletonList(
                message(Role.TOOL, "tool", Message.Visibility.VISIBLE))));
        assertTrue(ChatTranscript.hasContent(Collections.singletonList(
                message(Role.ASSISTANT, "answer", Message.Visibility.VISIBLE))));
    }

    private static Message message(Role role, String text, Message.Visibility visibility) {
        return Message.builder().role(role).visibility(visibility)
                .addBlock(ContentBlock.text(text)).build();
    }
}
