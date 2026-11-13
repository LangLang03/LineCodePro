package cn.lineai.feature.chat;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.lineai.ui.i18n.R;

/**
 * Copies SAF/photo-picker media into the private attachments directory and hands the path to
 * the composer. The request-code dispatch lives with the host; this class owns the copying.
 */
public final class ChatAttachments {

    private ChatAttachments() {
    }

    /** Copies a picked image and attaches it. */
    public static void importImage(ChatHost host, Uri uri) {
        String mime = host.activity().getContentResolver().getType(uri);
        String extension = "jpg";
        if (mime != null && mime.contains("/")) {
            String suffix = mime.substring(mime.indexOf('/') + 1)
                    .toLowerCase(java.util.Locale.ROOT);
            if (suffix.equals("jpeg")) suffix = "jpg";
            if (suffix.matches("[a-z0-9]{2,5}")) extension = suffix;
        }
        final String fileName = "image-" + System.currentTimeMillis() + "." + extension;
        copyAndAttach(host, uri, fileName, false);
    }

    /** Copies a picked file (or video) under its display name and attaches it. */
    public static void importFile(ChatHost host, Uri uri) {
        final String fileName = displayName(host.activity(), uri);
        copyAndAttach(host, uri, System.currentTimeMillis() + "-" + fileName, true);
    }

    private static void copyAndAttach(ChatHost host, Uri uri, String fileName, boolean file) {
        host.scheduler().io(() -> {
            File target = new File(attachmentsDir(host.activity()), fileName);
            try (InputStream input = host.activity().getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("no stream");
                copy(input, output);
                host.scheduler().main(() -> {
                    if (file) host.addChatFile(target.getAbsolutePath());
                    else host.addChatImage(target.getAbsolutePath());
                });
            } catch (IOException error) {
                target.delete();
                host.scheduler().main(() -> Toast.makeText(host.activity(),
                        R.string.screen_model_add_local_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Photo Picker URIs are opaque media ids, so the last path segment has no extension. Prefer
     * the provider's DISPLAY_NAME and fall back to the MIME suffix.
     */
    private static String displayName(Context context, Uri uri) {
        String name = "";
        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (RuntimeException error) {
            // Some providers reject DISPLAY_NAME; the path/MIME fallback below still works.
            Log.w("ChatAttachments", "display name unavailable", error);
        }
        if (name == null) {
            name = "";
        }
        name = name.trim();
        if (name.isEmpty()) {
            String last = uri.getLastPathSegment();
            if (last != null) {
                int slash = last.lastIndexOf('/');
                name = slash >= 0 ? last.substring(slash + 1) : last;
            }
        }
        if (!name.contains(".")) {
            String extension = extension(context.getContentResolver().getType(uri));
            if (!extension.isEmpty()) {
                name = name + "." + extension;
            }
        }
        return name.isEmpty() ? "attachment.bin" : name;
    }

    private static String extension(String mime) {
        if (mime == null || !mime.contains("/")) {
            return "";
        }
        String suffix = mime.substring(mime.indexOf('/') + 1)
                .toLowerCase(java.util.Locale.ROOT);
        if (suffix.equals("jpeg")) return "jpg";
        if (suffix.equals("quicktime")) return "mov";
        if (suffix.equals("mpeg")) return "mpg";
        if (suffix.equals("x-matroska")) return "mkv";
        return suffix.matches("[a-z0-9]{2,5}") ? suffix : "";
    }

    private static File attachmentsDir(Context context) {
        File dir = new File(context.getFilesDir(), "attachments");
        dir.mkdirs();
        return dir;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
    }
}
