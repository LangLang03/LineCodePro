package cn.lineai.workspace;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * Exposes the private {@code files/.linecode} workspace tree to other apps via content:// URIs
 * (similar to a limited "file provider" / document tree for internal app storage).
 */
public final class WorkspaceFileProvider extends ContentProvider {
    private static final int CODE_ROOT = 1;
    private static final int CODE_FILE = 2;
    private static final String[] COLUMNS = new String[] {
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            "_id",
            "_data"
    };

    private UriMatcher matcher;
    private File linecodeRoot;

    public static String authority(Context context) {
        return context.getPackageName() + ".workspace";
    }

    public static Uri rootUri(Context context) {
        return new Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath("root")
                .build();
    }

    public static Uri fileUri(Context context, String relativePath) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath("file");
        if (relativePath != null && relativePath.length() > 0) {
            for (String part : relativePath.split("/")) {
                if (part.length() > 0) {
                    builder.appendPath(part);
                }
            }
        }
        return builder.build();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        WorkspacePaths paths = new WorkspacePaths(context);
        paths.ensurePrivateRoots();
        linecodeRoot = paths.getLinecodeRoot();
        matcher = new UriMatcher(UriMatcher.NO_MATCH);
        String auth = authority(context);
        matcher.addURI(auth, "root", CODE_ROOT);
        matcher.addURI(auth, "file", CODE_FILE);
        matcher.addURI(auth, "file/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*/*/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*/*/*/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*/*/*/*/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*/*/*/*/*/*", CODE_FILE);
        matcher.addURI(auth, "file/*/*/*/*/*/*/*/*", CODE_FILE);
        return true;
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
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = resolveFile(uri);
        if (file == null || !file.exists()) {
            return null;
        }
        String[] cols = projection == null ? COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            String col = cols[i];
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                row[i] = file.isFile() ? file.length() : 0L;
            } else if ("_id".equals(col)) {
                row[i] = 1;
            } else if ("_data".equals(col)) {
                row[i] = file.getAbsolutePath();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolveFile(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("Not found");
        }
        int flags = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && mode.contains("w")) {
            flags = ParcelFileDescriptor.MODE_READ_WRITE;
        }
        return ParcelFileDescriptor.open(file, flags);
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
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File resolveFile(Uri uri) {
        if (uri == null || linecodeRoot == null) {
            return null;
        }
        try {
            int match = matcher == null ? -1 : matcher.match(uri);
            if (match == CODE_ROOT) {
                return linecodeRoot;
            }
            java.util.List<String> segments = uri.getPathSegments();
            if (segments == null || segments.isEmpty() || !"file".equals(segments.get(0))) {
                return linecodeRoot;
            }
            File current = linecodeRoot;
            for (int i = 1; i < segments.size(); i++) {
                current = new File(current, segments.get(i));
            }
            String rootPath = linecodeRoot.getCanonicalPath();
            String filePath = current.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }
}
