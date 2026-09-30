package cn.lineai.platform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Maps document IDs to files without following links outside the workspace. */
final class WorkspaceDocumentPaths {
    static final String ROOT_ID = "workspace";

    private final File root;
    private final String rootPath;

    WorkspaceDocumentPaths(File root) throws IOException {
        this.root = root.getCanonicalFile();
        this.rootPath = this.root.getPath();
    }

    File root() {
        return root;
    }

    File resolve(String documentId) throws FileNotFoundException {
        if (ROOT_ID.equals(documentId)) {
            return root;
        }
        if (documentId == null || !documentId.startsWith(ROOT_ID + "/")) {
            throw new FileNotFoundException("Invalid workspace document ID");
        }
        String relative = documentId.substring(ROOT_ID.length() + 1);
        for (String segment : relative.split("/", -1)) {
            validateName(segment);
        }
        File candidate = new File(root, relative);
        try {
            File canonical = candidate.getCanonicalFile();
            if (!canonical.equals(candidate.getAbsoluteFile())
                    || !canonical.getPath().startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("Workspace document leaves the root");
            }
            return canonical;
        } catch (IOException error) {
            throw fileError(error);
        }
    }

    String documentIdFor(File file) throws FileNotFoundException {
        try {
            File canonical = file.getCanonicalFile();
            if (!canonical.equals(file.getAbsoluteFile())) {
                throw new FileNotFoundException("Symbolic links are not workspace documents");
            }
            String path = canonical.getPath();
            if (path.equals(rootPath)) {
                return ROOT_ID;
            }
            if (!path.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("Workspace document leaves the root");
            }
            String relative = path.substring(rootPath.length() + 1)
                    .replace(File.separatorChar, '/');
            for (String segment : relative.split("/", -1)) {
                validateName(segment);
            }
            return ROOT_ID + "/" + relative;
        } catch (IOException error) {
            throw fileError(error);
        }
    }

    boolean isChild(String parentId, String childId) {
        return childId.startsWith(parentId + "/");
    }

    static void validateName(String name) throws FileNotFoundException {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new FileNotFoundException("Invalid workspace document name");
        }
    }

    private static FileNotFoundException fileError(IOException cause) {
        FileNotFoundException error = new FileNotFoundException("Invalid workspace path");
        error.initCause(cause);
        return error;
    }
}
