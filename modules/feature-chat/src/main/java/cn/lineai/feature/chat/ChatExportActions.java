package cn.lineai.feature.chat;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.Message;
import cn.lineai.feature.chat.ChatTranscript;
import cn.lineai.ui.i18n.R;

/** SAF export of the current chat conversation as a Markdown file. */
public final class ChatExportActions {

    /** Request code identifying this module's create-document activity result. */
    public static final int REQUEST_CHAT_EXPORT = 7409;

    /** Static utility; not instantiable. */
    private ChatExportActions() {
    }

    /** Opens the system create-document picker for a Markdown transcript of the current chat. */
    public static void request(ChatHost host) {
        if (!ChatTranscript.hasContent(host.chat().state().messages)) {
            Toast.makeText(host.activity(), R.string.toast_chat_empty_export,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/markdown");
        intent.putExtra(Intent.EXTRA_TITLE,
                "linecode-chat-" + System.currentTimeMillis() + ".md");
        try {
            host.activity().startActivityForResult(intent, REQUEST_CHAT_EXPORT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(host.activity(), R.string.chat_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Handles the create-document result; returns whether the request code is ours. */
    public static boolean handleResult(ChatHost host, int requestCode, Uri uri) {
        if (requestCode != REQUEST_CHAT_EXPORT) {
            return false;
        }
        if (uri == null) {
            Toast.makeText(host.activity(), R.string.chat_export_failed,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        List<Message> messages = new ArrayList<>(host.chat().state().messages);
        String userLabel = host.activity().getString(R.string.chat_export_role_user);
        String assistantLabel = host.activity().getString(R.string.chat_export_role_assistant);
        String footer = host.activity().getString(R.string.chat_export_footer);
        Scheduler scheduler = host.scheduler();
        scheduler.io(() -> {
            String markdown = ChatTranscript.toMarkdown(messages, userLabel, assistantLabel,
                    footer);
            try (OutputStream output = host.activity().getContentResolver()
                    .openOutputStream(uri)) {
                if (output == null) {
                    throw new java.io.IOException("Cannot open the chat export destination");
                }
                output.write(markdown.getBytes(StandardCharsets.UTF_8));
                scheduler.main(() -> Toast.makeText(host.activity(), R.string.chat_exported,
                        Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                scheduler.main(() -> Toast.makeText(host.activity(),
                        R.string.chat_export_failed, Toast.LENGTH_LONG).show());
            }
        });
        return true;
    }
}
