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

/** Read-only content provider used to hand workspace files to the user's chosen viewer. */
public final class WorkspaceFileProvider extends ContentProvider {

    private static final String AUTHORITY_SUFFIX = ".workspace.files";

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(Uri.encode(file.getAbsolutePath()))
                .build();
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = file(uri);
        MatrixCursor cursor = new MatrixCursor(new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    @Override public String getType(Uri uri) {
        File file = file(uri);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String type = extension == null ? null
                : android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(extension.toLowerCase(java.util.Locale.ROOT));
        return type == null ? "application/octet-stream" : type;
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
        try {
            return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IllegalArgumentException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    private static File file(Uri uri) {
        String encoded = uri.getLastPathSegment();
        if (encoded == null) throw new IllegalArgumentException("missing path");
        File file = new File(Uri.decode(encoded));
        if (!file.isFile()) throw new IllegalArgumentException("not a file");
        return file;
    }
}
