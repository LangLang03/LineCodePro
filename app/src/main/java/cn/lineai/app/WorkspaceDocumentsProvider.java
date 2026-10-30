package cn.lineai.app;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Exposes the app-private {@code files/workspace} tree as a browsable document provider.
 *
 * <p>Other apps cannot read {@code filesDir} directly. Declaring this provider lets them browse
 * and edit the same projects through the system file picker ("LineCode workspace" root) without
 * copying files or granting all-files access. The provider itself is protected by
 * {@code MANAGE_DOCUMENTS}, so only the system picker can bind to it; each consumer receives a
 * per-URI read/write grant.</p>
 */
public final class WorkspaceDocumentsProvider extends DocumentsProvider {

    private static final String ROOT_ID = "linecode-workspace";
    private static final String ROOT_DOCUMENT_ID = "/";

    private static final String[] DEFAULT_ROOT_PROJECTION = {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS
    };

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(DEFAULT_ROOT_PROJECTION);
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID);
        row.add(Root.COLUMN_TITLE, getContext().getString(
                cn.lineai.ui.i18n.R.string.workspace_provider_open_title));
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE
                | Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(Root.COLUMN_ICON, getContext().getApplicationInfo().icon);
        row.add(Root.COLUMN_AVAILABLE_BYTES, workspaceRoot().getUsableSpace());
        return cursor;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(DEFAULT_DOCUMENT_PROJECTION);
        addDocumentRow(cursor, documentFor(documentId));
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                                String sortOrder) throws FileNotFoundException {
        File parent = documentFor(parentDocumentId);
        if (!parent.isDirectory()) {
            throw new FileNotFoundException(parentDocumentId);
        }
        MatrixCursor cursor = new MatrixCursor(DEFAULT_DOCUMENT_PROJECTION);
        File[] children = parent.listFiles();
        if (children != null) {
            Arrays.sort(children, (left, right) -> {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            });
            for (File child : children) {
                addDocumentRow(cursor, child);
            }
        }
        return cursor;
    }

    @Override public Cursor querySearchDocuments(String rootId, String query, String[] projection)
            throws FileNotFoundException {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<File> matches = new ArrayList<>();
        collect(workspaceRoot(), needle, matches, 100);
        MatrixCursor cursor = new MatrixCursor(DEFAULT_DOCUMENT_PROJECTION);
        for (File match : matches) {
            addDocumentRow(cursor, match);
        }
        return cursor;
    }

    @Override public String getDocumentType(String documentId) throws FileNotFoundException {
        return mimeType(documentFor(documentId));
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode,
                                                       android.os.CancellationSignal signal)
            throws FileNotFoundException {
        File file = documentFor(documentId);
        if (!file.isFile()) {
            throw new FileNotFoundException(documentId);
        }
        int flags = ParcelFileDescriptor.MODE_READ_ONLY;
        String safeMode = mode == null ? "r" : mode;
        if (safeMode.contains("a")) {
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND;
        } else if (safeMode.contains("w")) {
            flags = safeMode.contains("r")
                    ? ParcelFileDescriptor.MODE_READ_WRITE
                    : ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override public String createDocument(String parentDocumentId, String mimeType,
                                           String displayName) throws FileNotFoundException {
        File parent = documentFor(parentDocumentId);
        if (!parent.isDirectory()) {
            throw new FileNotFoundException(parentDocumentId);
        }
        String name = safeName(displayName);
        File target = unique(parent, name, mimeType);
        try {
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!target.mkdirs()) {
                    throw new FileNotFoundException("cannot create " + target);
                }
            } else if (!target.createNewFile()) {
                throw new FileNotFoundException("cannot create " + target);
            }
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
        return documentId(target);
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        if (documentId == null || documentId.isEmpty()
                || ROOT_DOCUMENT_ID.equals(documentId)) {
            throw new FileNotFoundException("cannot delete the workspace root");
        }
        File file = documentFor(documentId);
        deleteRecursively(file);
    }

    @Override public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        File file = documentFor(documentId);
        File parent = file.getParentFile();
        if (parent == null || ROOT_DOCUMENT_ID.equals(documentId)) {
            throw new FileNotFoundException(documentId);
        }
        File target = unique(parent, safeName(displayName), mimeType(file));
        if (!file.renameTo(target)) {
            throw new FileNotFoundException("cannot rename " + documentId);
        }
        return documentId(target);
    }

    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            File parent = documentFor(parentDocumentId);
            File child = documentFor(documentId);
            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.equals(parentPath)
                    || childPath.startsWith(parentPath + File.separator);
        } catch (IOException error) {
            return false;
        }
    }

    private void addDocumentRow(MatrixCursor cursor, File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId(file));
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(Document.COLUMN_MIME_TYPE, mimeType(file));
        row.add(Document.COLUMN_SIZE, file.isFile() ? file.length() : 0L);
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        int flags = Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME;
        if (file.isDirectory()) {
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        } else {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }
        row.add(Document.COLUMN_FLAGS, flags);
    }

    private void collect(File directory, String needle, List<File> matches, int limit) {
        if (matches.size() >= limit) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (matches.size() >= limit) {
                return;
            }
            if (child.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(child);
            }
            if (child.isDirectory()) {
                collect(child, needle, matches, limit);
            }
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private File workspaceRoot() {
        File root = new File(getContext().getFilesDir(), "workspace");
        if (!root.isDirectory()) {
            root.mkdirs();
        }
        return root;
    }

    private File documentFor(String documentId) throws FileNotFoundException {
        File root = workspaceRoot();
        if (documentId == null || documentId.isEmpty()
                || ROOT_DOCUMENT_ID.equals(documentId)) {
            return root;
        }
        try {
            String rootPath = root.getCanonicalPath();
            File file = new File(root, documentId);
            String filePath = file.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("outside workspace");
            }
            if (!file.exists()) {
                throw new FileNotFoundException(documentId);
            }
            return file;
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    private String documentId(File file) {
        File root = workspaceRoot();
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.equals(rootPath)) {
                return ROOT_DOCUMENT_ID;
            }
            return filePath.substring(rootPath.length() + 1)
                    .replace(File.separatorChar, '/');
        } catch (IOException error) {
            return file.getName();
        }
    }

    private static File unique(File parent, String name, String mimeType) {
        String candidate = name;
        if (!Document.MIME_TYPE_DIR.equals(mimeType) && !candidate.contains(".")) {
            String extension = extensionFor(mimeType);
            if (!extension.isEmpty()) {
                candidate = candidate + "." + extension;
            }
        }
        File target = new File(parent, candidate);
        int index = 2;
        while (target.exists()) {
            target = new File(parent, candidate + "-" + index);
            index++;
        }
        return target;
    }

    private static String safeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        name = name.replace('/', '-').replace('\\', '-');
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "untitled";
        }
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null || !mimeType.contains("/")) {
            return "";
        }
        String extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType);
        return extension == null ? "" : extension;
    }

    private String mimeType(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
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
}
