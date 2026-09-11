package cn.lineai.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Exposes only files/.linecode through canonical, application-owned content URIs. */
public final class WorkspaceDirectoryProvider extends ContentProvider {
    private static final String[] DEFAULT_COLUMNS = new String[] {
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            "_id",
            "_data"
    };

    private File linecodeRoot;

    static String authority(Context context) {
        return context.getPackageName() + ".workspace";
    }

    static Uri rootUri(Context context) {
        return new Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath("root")
                .build();
    }

    static Uri fileUri(Context context, String relativePath) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath("file");
        if (relativePath != null && !relativePath.isEmpty()) {
            for (String segment : relativePath.split("/")) {
                if (!segment.isEmpty()) {
                    builder.appendPath(segment);
                }
            }
        }
        return builder.build();
    }

    static File homeDirectory(Context context) {
        return new File(linecodeDirectory(context), "home");
    }

    private static File linecodeDirectory(Context context) {
        return new File(context.getFilesDir(), ".linecode");
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        linecodeRoot = linecodeDirectory(context);
        if (!linecodeRoot.isDirectory() && !linecodeRoot.mkdirs()) {
            return false;
        }
        File home = homeDirectory(context);
        return home.isDirectory() || home.mkdirs();
    }

    @Override
    public String getType(Uri uri) {
        File file = resolveFile(uri);
        if (file == null) {
            return null;
        }
        if (file.isDirectory()) {
            return "vnd.android.document/directory";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < name.length()) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        File file = resolveFile(uri);
        if (file == null || !file.exists()) {
            return null;
        }
        String[] columns = projection == null ? DEFAULT_COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            String column = columns[index];
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row[index] = file.getName();
            } else if (OpenableColumns.SIZE.equals(column)) {
                row[index] = file.isFile() ? file.length() : 0L;
            } else if ("_id".equals(column)) {
                row[index] = 1;
            } else if ("_data".equals(column)) {
                row[index] = file.getAbsolutePath();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File file = resolveFile(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("Workspace file not found");
        }
        int access = mode != null && mode.contains("w")
                ? ParcelFileDescriptor.MODE_READ_WRITE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(file, access);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File resolveFile(Uri uri) {
        if (uri == null || linecodeRoot == null
                || !authority(contextOrThrow()).equals(uri.getAuthority())) {
            return null;
        }
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        File requested;
        if (segments.size() == 1 && "root".equals(segments.get(0))) {
            requested = linecodeRoot;
        } else if ("file".equals(segments.get(0))) {
            requested = linecodeRoot;
            for (int index = 1; index < segments.size(); index++) {
                requested = new File(requested, segments.get(index));
            }
        } else {
            return null;
        }
        try {
            String rootPath = linecodeRoot.getCanonicalPath();
            String requestedPath = requested.getCanonicalPath();
            if (!requestedPath.equals(rootPath)
                    && !requestedPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return requested;
        } catch (IOException error) {
            return null;
        }
    }

    private Context contextOrThrow() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Workspace provider has no Context");
        }
        return context;
    }
}
