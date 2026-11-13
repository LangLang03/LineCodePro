package cn.lineai.feature.chat;

import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

/**
 * Builds the Markdown transcript behind the chat "Export chat" action.
 *
 * <p>Pure domain logic: the caller supplies already-localized role labels and footer so this
 * class stays free of Android imports and can be unit tested on the JVM.</p>
 */
public final class ChatTranscript {

    /** Static utility; not instantiable. */
    private ChatTranscript() {
    }

    /** Returns whether at least one message would appear in the exported transcript. */
    public static boolean hasContent(List<Message> messages) {
        if (messages == null) {
            return false;
        }
        for (Message message : messages) {
            if (isExportable(message)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders the exportable messages as Markdown: one {@code ## label} section per message,
     * separated by horizontal rules, with the footer last. Returns an empty string when there is
     * nothing to export.
     */
    public static String toMarkdown(List<Message> messages, String userLabel,
                                    String assistantLabel, String footer) {
        if (messages == null) {
            return "";
        }
        String user = label(userLabel);
        String assistant = label(assistantLabel);
        StringBuilder markdown = new StringBuilder();
        boolean wroteMessage = false;
        for (Message message : messages) {
            if (!isExportable(message)) {
                continue;
            }
            markdown.append("## ")
                    .append(message.role == Role.USER ? user : assistant)
                    .append("\n\n")
                    .append(message.plainText().trim())
                    .append("\n\n---\n\n");
            wroteMessage = true;
        }
        if (!wroteMessage) {
            return "";
        }
        if (footer != null) {
            markdown.append(footer);
        }
        return markdown.toString();
    }

    /** Only visible user or assistant messages with non-empty text are exported. */
    private static boolean isExportable(Message message) {
        if (message == null || message.visibility != Message.Visibility.VISIBLE) {
            return false;
        }
        if (message.role != Role.USER && message.role != Role.ASSISTANT) {
            return false;
        }
        return !message.plainText().trim().isEmpty();
    }

    /** Null-safe role label. */
    private static String label(String value) {
        return value == null ? "" : value;
    }
}
