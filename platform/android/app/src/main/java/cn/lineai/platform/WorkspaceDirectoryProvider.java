package cn.lineai.platform;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import cn.lineai.R;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/** Exposes the application-owned .linecode tree through the system document picker. */
public final class WorkspaceDirectoryProvider extends DocumentsProvider {
    private static final String ROOT_ID = WorkspaceDocumentPaths.ROOT_ID;
    private static final String[] ROOT_COLUMNS = {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };
    private static final String[] DOCUMENT_COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private WorkspaceDocumentPaths paths;

    static String authority(Context context) {
        return context.getPackageName() + ".workspace";
    }

    static Uri rootDocumentUri(Context context) {
        return DocumentsContract.buildDocumentUri(authority(context), ROOT_ID);
    }

    static File workspaceDirectory(Context context) {
        return new File(context.getFilesDir(), ".linecode");
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        File root = workspaceDirectory(context);
        if (!root.isDirectory() && !root.mkdirs()) {
            return false;
        }
        File home = new File(root, "home");
        if (!home.isDirectory() && !home.mkdirs()) {
            return false;
        }
        try {
            paths = new WorkspaceDocumentPaths(root);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        String[] columns = projection == null ? ROOT_COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (DocumentsContract.Root.COLUMN_ROOT_ID.equals(column)
                    || DocumentsContract.Root.COLUMN_DOCUMENT_ID.equals(column)) {
                row.add(ROOT_ID);
            } else if (DocumentsContract.Root.COLUMN_TITLE.equals(column)) {
                row.add(getContext().getString(R.string.workspace_provider_open_title));
            } else if (DocumentsContract.Root.COLUMN_FLAGS.equals(column)) {
                row.add(DocumentsContract.Root.FLAG_LOCAL_ONLY
                        | DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                        | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
            } else if (DocumentsContract.Root.COLUMN_ICON.equals(column)) {
                row.add(R.mipmap.ic_launcher);
            } else if (DocumentsContract.Root.COLUMN_AVAILABLE_BYTES.equals(column)) {
                row.add(paths.root().getUsableSpace());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        File file = existingFile(documentId);
        MatrixCursor cursor = new MatrixCursor(
                projection == null ? DOCUMENT_COLUMNS : projection, 1);
        addDocument(cursor, documentId, file);
        cursor.setNotificationUri(getContext().getContentResolver(), documentUri(documentId));
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            String sortOrder) throws FileNotFoundException {
        File parent = existingDirectory(parentDocumentId);
        File[] children = parent.listFiles();
        if (children == null) {
            throw new FileNotFoundException("Unable to list workspace directory");
        }
        MatrixCursor cursor = new MatrixCursor(
                projection == null ? DOCUMENT_COLUMNS : projection, children.length);
        for (File child : children) {
            try {
                addDocument(cursor, paths.documentIdFor(child), child);
            } catch (FileNotFoundException ignored) {
                // Symbolic links are not part of the exposed document tree.
            }
        }
        cursor.setNotificationUri(getContext().getContentResolver(),
                childDocumentsUri(parentDocumentId));
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            Bundle queryArgs) throws FileNotFoundException {
        return queryChildDocuments(parentDocumentId, projection, (String) null);
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        File file = existingFile(documentId);
        if (!file.isFile()) {
            throw new FileNotFoundException("Workspace document is not a file");
        }
        int access = ParcelFileDescriptor.parseMode(mode);
        if (mode.contains("w")) {
            try {
                return ParcelFileDescriptor.open(file, access,
                        new Handler(Looper.getMainLooper()), error -> {
                            getContext().getContentResolver().notifyChange(
                                    documentUri(documentId), null);
                            notifyChildren(parentId(documentId));
                        });
            } catch (IOException error) {
                throw fileError("Unable to open workspace document", error);
            }
        }
        return ParcelFileDescriptor.open(file, access);
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType,
            String displayName) throws FileNotFoundException {
        existingDirectory(parentDocumentId);
        WorkspaceDocumentPaths.validateName(displayName);
        File file = paths.resolve(parentDocumentId + "/" + displayName);
        if (file.exists()) {
            throw new FileNotFoundException("Workspace document already exists");
        }
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!file.mkdir()) {
                    throw new IOException("Unable to create workspace directory");
                }
            } else if (mimeType != null && !mimeType.isEmpty()) {
                if (!file.createNewFile()) {
                    throw new IOException("Unable to create workspace file");
                }
            } else {
                throw new FileNotFoundException("Document MIME type is required");
            }
        } catch (IOException error) {
            throw fileError("Unable to create workspace document", error);
        }
        notifyChildren(parentDocumentId);
        return paths.documentIdFor(file);
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        if (ROOT_ID.equals(documentId)) {
            throw new FileNotFoundException("Workspace root cannot be deleted");
        }
        File file = existingFile(documentId);
        validateTree(file);
        deleteTree(file);
        notifyChildren(parentId(documentId));
        getContext().getContentResolver().notifyChange(documentUri(documentId), null);
        revokeDocumentPermission(documentId);
    }

    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        if (ROOT_ID.equals(documentId)) {
            throw new FileNotFoundException("Workspace root cannot be renamed");
        }
        WorkspaceDocumentPaths.validateName(displayName);
        File source = existingFile(documentId);
        String parentId = parentId(documentId);
        File target = paths.resolve(parentId + "/" + displayName);
        if (source.equals(target)) {
            return documentId;
        }
        if (target.exists() || !source.renameTo(target)) {
            throw new FileNotFoundException("Unable to rename workspace document");
        }
        String renamedId = paths.documentIdFor(target);
        notifyChildren(parentId);
        getContext().getContentResolver().notifyChange(documentUri(documentId), null);
        revokeDocumentPermission(documentId);
        return renamedId;
    }

    @Override
    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        if (ROOT_ID.equals(sourceDocumentId)) {
            throw new FileNotFoundException("Workspace root cannot be copied");
        }
        File source = existingFile(sourceDocumentId);
        existingDirectory(targetParentDocumentId);
        if (paths.isChild(sourceDocumentId, targetParentDocumentId)) {
            throw new FileNotFoundException("Cannot copy a directory into itself");
        }
        File target = paths.resolve(targetParentDocumentId + "/" + source.getName());
        if (target.exists()) {
            throw new FileNotFoundException("Workspace document already exists");
        }
        validateTree(source);
        try {
            copyTree(source, target);
        } catch (IOException error) {
            if (target.exists()) {
                try {
                    deleteTree(target);
                } catch (FileNotFoundException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw fileError("Unable to copy workspace document", error);
        }
        notifyChildren(targetParentDocumentId);
        return paths.documentIdFor(target);
    }

    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
            String targetParentDocumentId) throws FileNotFoundException {
        if (ROOT_ID.equals(sourceDocumentId)
                || !parentId(sourceDocumentId).equals(sourceParentDocumentId)) {
            throw new FileNotFoundException("Invalid workspace move source");
        }
        File source = existingFile(sourceDocumentId);
        existingDirectory(targetParentDocumentId);
        if (paths.isChild(sourceDocumentId, targetParentDocumentId)) {
            throw new FileNotFoundException("Cannot move a directory into itself");
        }
        File target = paths.resolve(targetParentDocumentId + "/" + source.getName());
        if (source.equals(target)) {
            return sourceDocumentId;
        }
        if (target.exists() || !source.renameTo(target)) {
            throw new FileNotFoundException("Unable to move workspace document");
        }
        String movedId = paths.documentIdFor(target);
        notifyChildren(sourceParentDocumentId);
        notifyChildren(targetParentDocumentId);
        revokeDocumentPermission(sourceDocumentId);
        return movedId;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            return existingFile(parentDocumentId).isDirectory()
                    && existingFile(documentId).exists()
                    && paths.isChild(parentDocumentId, documentId);
        } catch (FileNotFoundException error) {
            return false;
        }
    }

    private File existingFile(String documentId) throws FileNotFoundException {
        File file = paths.resolve(documentId);
        if (!file.exists()) {
            throw new FileNotFoundException("Workspace document not found");
        }
        return file;
    }

    private File existingDirectory(String documentId) throws FileNotFoundException {
        File file = existingFile(documentId);
        if (!file.isDirectory()) {
            throw new FileNotFoundException("Workspace document is not a directory");
        }
        return file;
    }

    private void addDocument(MatrixCursor cursor, String documentId, File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        int flags = 0;
        if (file.isDirectory() && file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (file.isFile() && file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
        }
        if (!ROOT_ID.equals(documentId) && file.getParentFile().canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                    | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    | DocumentsContract.Document.FLAG_SUPPORTS_COPY
                    | DocumentsContract.Document.FLAG_SUPPORTS_MOVE;
        }
        for (String column : cursor.getColumnNames()) {
            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) {
                row.add(documentId);
            } else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) {
                row.add(ROOT_ID.equals(documentId)
                        ? getContext().getString(R.string.workspace_provider_open_title)
                        : file.getName());
            } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) {
                row.add(mimeType(file));
            } else if (DocumentsContract.Document.COLUMN_FLAGS.equals(column)) {
                row.add(flags);
            } else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) {
                row.add(file.isFile() ? file.length() : 0L);
            } else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column)) {
                row.add(file.lastModified());
            } else {
                row.add(null);
            }
        }
    }

    private static String mimeType(File file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
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

    private static String parentId(String documentId) {
        return documentId.substring(0, documentId.lastIndexOf('/'));
    }

    private Uri documentUri(String documentId) {
        return DocumentsContract.buildDocumentUri(authority(getContext()), documentId);
    }

    private Uri childDocumentsUri(String documentId) {
        return DocumentsContract.buildChildDocumentsUri(authority(getContext()), documentId);
    }

    private void notifyChildren(String documentId) {
        getContext().getContentResolver().notifyChange(childDocumentsUri(documentId), null);
    }

    private void validateTree(File file) throws FileNotFoundException {
        paths.documentIdFor(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new FileNotFoundException("Unable to list workspace directory");
            }
            for (File child : children) {
                validateTree(child);
            }
        }
    }

    private void deleteTree(File file) throws FileNotFoundException {
        paths.documentIdFor(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new FileNotFoundException("Unable to list workspace directory");
            }
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            throw new FileNotFoundException("Unable to delete workspace document");
        }
    }

    private void copyTree(File source, File target) throws IOException {
        paths.documentIdFor(source);
        paths.documentIdFor(target);
        if (source.isDirectory()) {
            if (!target.mkdir()) {
                throw new IOException("Unable to create copied workspace directory");
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Unable to list workspace directory");
            }
            for (File child : children) {
                copyTree(child, new File(target, child.getName()));
            }
        } else {
            try (FileInputStream input = new FileInputStream(source);
                    FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private static FileNotFoundException fileError(String message, IOException cause) {
        FileNotFoundException error = new FileNotFoundException(message);
        error.initCause(cause);
        return error;
    }
}
