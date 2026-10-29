package cn.lineai.feature.settings;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

import cn.lineai.data.api.ErrorLog;
import cn.lineai.ui.i18n.R;

/**
 * Turns one recorded failure into a text file and hands it to another app.
 *
 * <p>The file lives under {@code files/error-logs/} and is exposed read-only through the
 * {@code content} provider whose authority is {@code <applicationId>.errorlogs}. Nothing is
 * persisted in the database: the file is derived from the stored entry every time it is opened.</p>
 */
public final class ErrorLogFiles {

    /** Suffix appended to the application id to form the provider authority. */
    public static final String AUTHORITY_SUFFIX = ".errorlogs";

    private static final String DIRECTORY = "error-logs";
    private static final String EXTENSION = ".txt";
    private static final int MAXIMUM_STEM = 96;

    private ErrorLogFiles() {
    }

    public static File directory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    /** Resolves a provider path segment to a file inside the log directory, or {@code null}. */
    public static File resolve(Context context, String name) {
        if (name == null || name.isEmpty() || EXTENSION.equals(name)
                || ".".equals(name) || "..".equals(name)) {
            return null;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return null;
        if (!name.endsWith(EXTENSION)) return null;
        File dir = directory(context);
        File file = new File(dir, name);
        if (!file.isFile()) return null;
        try {
            String root = dir.getCanonicalPath();
            if (!file.getCanonicalPath().startsWith(root + File.separator)) return null;
        } catch (IOException error) {
            return null;
        }
        return file;
    }

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(file.getName())
                .build();
    }

    /** Locale-aware date line; falls back to the recorded subtitle when no time was stored. */
    public static String dateLine(Context context, ErrorLog entry) {
        if (entry == null) return "";
        if (entry.createdAt <= 0L) return entry.subtitle;
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return format.format(new Date(entry.createdAt));
    }

    public static String body(Context context, ErrorLog entry) {
        StringBuilder out = new StringBuilder();
        if (entry == null) return out.toString();
        out.append(entry.title).append('\n');
        String date = dateLine(context, entry);
        if (!date.isEmpty()) out.append(date).append('\n');
        if (!entry.subtitle.isEmpty()) out.append(entry.subtitle).append('\n');
        if (!entry.detail.isEmpty()) out.append('\n').append(entry.detail);
        if (out.length() == 0 || out.charAt(out.length() - 1) != '\n') out.append('\n');
        return out.toString();
    }

    public static File write(Context context, ErrorLog entry) throws IOException {
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("cannot create " + DIRECTORY);
        }
        File file = new File(dir, fileName(entry));
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(body(context, entry).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    static String fileName(ErrorLog entry) {
        StringBuilder stem = new StringBuilder();
        String title = entry == null ? "" : entry.title;
        for (int index = 0; index < title.length() && stem.length() < MAXIMUM_STEM; index++) {
            char value = title.charAt(index);
            boolean safe = (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '.' || value == '_' || value == '-';
            stem.append(safe ? value : '_');
        }
        while (stem.length() > 0 && stem.charAt(0) == '.') stem.deleteCharAt(0);
        if (stem.length() == 0) stem.append("linecode-error");
        if (entry != null && entry.createdAt > 0L) stem.append('-').append(entry.createdAt);
        if (!stem.toString().endsWith(EXTENSION)) stem.append(EXTENSION);
        return stem.toString();
    }

    /** Writes the entry and opens it in another app; shows {@code open_failed} on any failure. */
    public static void open(Context context, ErrorLog entry) {
        try {
            File file = write(context, entry);
            Uri uri = uriFor(context, file);
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.setClipData(ClipData.newRawUri("", uri));
            context.startActivity(Intent.createChooser(view,
                    context.getString(R.string.screen_error_logs_open_with)));
        } catch (IOException | RuntimeException error) {
            Toast.makeText(context, R.string.screen_error_logs_open_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
