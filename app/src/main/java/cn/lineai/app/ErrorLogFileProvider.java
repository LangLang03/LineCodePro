package cn.lineai.app;

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

import cn.lineai.feature.settings.ErrorLogFiles;

/**
 * Read-only provider that exposes the derived error-log text files to an external viewer.
 *
 * <p>Only plain file names inside {@code files/error-logs/} resolve; the shared
 * {@link ErrorLogFiles#resolve(Context, String)} performs the canonical-path check.</p>
 */
public final class ErrorLogFileProvider extends ContentProvider {

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = requireFile(uri);
        MatrixCursor cursor = new MatrixCursor(new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    @Override public String getType(Uri uri) {
        return "text/plain";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read-only provider");
        return ParcelFileDescriptor.open(requireFile(uri),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File requireFile(Uri uri) {
        Context context = getContext();
        String name = uri == null ? null : uri.getLastPathSegment();
        File file = context == null ? null : ErrorLogFiles.resolve(context, name);
        if (file == null) throw new IllegalArgumentException("not an error log file");
        return file;
    }
}
