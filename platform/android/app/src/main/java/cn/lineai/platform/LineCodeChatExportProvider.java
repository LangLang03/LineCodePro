package cn.lineai.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only, cache-scoped content provider for native chat export files. */
public final class LineCodeChatExportProvider extends ContentProvider {
    private static final Map<String, String> MIME_TYPES;

    static {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("md", "text/markdown");
        types.put("pdf", "application/pdf");
        types.put("png", "image/png");
        MIME_TYPES = Collections.unmodifiableMap(types);
    }

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".chat-exports")
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        File directory = exportDirectory(context);
        return directory.isDirectory() || directory.mkdirs();
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) {
            return "application/octet-stream";
        }
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : MIME_TYPES.get(name.substring(dot + 1));
        return type == null ? "application/octet-stream" : type;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (mode != null && mode.contains("w")) {
            throw new FileNotFoundException("Chat exports are read only");
        }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            return null;
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(file.getName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Chat exports are read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        String name = uri.getLastPathSegment();
        if (context == null || name == null || name.isEmpty()) {
            throw new FileNotFoundException("Missing chat export file");
        }
        try {
            File directory = exportDirectory(context).getCanonicalFile();
            File file = new File(directory, name).getCanonicalFile();
            String prefix = directory.getPath() + File.separator;
            if (!file.getPath().startsWith(prefix) || !file.isFile()) {
                throw new FileNotFoundException("Chat export file was not found");
            }
            return file;
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    private static File exportDirectory(Context context) {
        return new File(context.getCacheDir(), "chat_exports");
    }
}
