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

    /**
     * Builds the content URI addressing one workspace file.
     *
     * @param context context providing the authority package name
     * @param file absolute file path to expose
     * @return the content URI carrying the encoded absolute path
     */
    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(Uri.encode(file.getAbsolutePath()))
                .build();
    }

    /** No initialization required; the provider resolves files per request. */
    @Override public boolean onCreate() {
        return true;
    }

    /** Reports display name and size of the addressed file. */
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = file(uri);
        MatrixCursor cursor = new MatrixCursor(new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    /** Mime type guessed from the file extension; octet-stream when unknown. */
    @Override public String getType(Uri uri) {
        File file = file(uri);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String type = extension == null ? null
                : android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(extension.toLowerCase(java.util.Locale.ROOT));
        return type == null ? "application/octet-stream" : type;
    }

    /** Unsupported: the provider is read-only. */
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    /** Unsupported: the provider is read-only. */
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    /** Unsupported: the provider is read-only. */
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    /** Opens the addressed file read-only; write modes and invalid paths are rejected. */
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read-only provider");
        try {
            return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IllegalArgumentException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    /** Decodes the addressed path and verifies it names an existing file. */
    private static File file(Uri uri) {
        String encoded = uri.getLastPathSegment();
        if (encoded == null) throw new IllegalArgumentException("missing path");
        File file = new File(Uri.decode(encoded));
        if (!file.isFile()) throw new IllegalArgumentException("not a file");
        return file;
    }
}
