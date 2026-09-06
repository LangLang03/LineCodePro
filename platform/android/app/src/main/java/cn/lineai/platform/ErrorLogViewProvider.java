package cn.lineai.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Android component boundary for C++-prepared, already-redacted log views.
 * Business logic and file creation remain in C++; this provider only grants a
 * single read-only descriptor to the external ACTION_VIEW handler.
 */
public final class ErrorLogViewProvider extends ContentProvider {
    private static final String DIRECTORY = "error_log_views";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read only");
        }
        File file = resolve(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException ignored) {
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

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri.getPathSegments().size() != 1) {
            throw new FileNotFoundException("Log not found");
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty() || !name.endsWith(".log")) {
            throw new FileNotFoundException("Log not found");
        }
        try {
            File root = new File(getContext().getCacheDir(), DIRECTORY)
                    .getCanonicalFile();
            File file = new File(root, name).getCanonicalFile();
            if (!root.equals(file.getParentFile()) || !file.isFile()) {
                throw new FileNotFoundException("Log not found");
            }
            return file;
        } catch (IOException error) {
            throw new FileNotFoundException("Log not found");
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Read only");
    }
}
