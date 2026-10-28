package cn.lineai.tool.api;

import java.io.File;
import java.io.IOException;

/** The single containment check for workspace paths. */
public final class PathPolicy {

    private PathPolicy() {
    }

    /**
     * Resolves {@code relative} under {@code root}, rejecting escapes through {@code ..},
     * absolute paths and symlinks that leave the workspace.
     */
    public static File resolve(File root, String relative) {
        if (root == null) throw new IllegalArgumentException("root == null");
        String value = relative == null ? "" : relative.trim();
        if (value.startsWith("/") || value.startsWith("\\")) {
            throw new SecurityException("Absolute paths are not allowed: " + value);
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File target = new File(canonicalRoot, value).getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String targetPath = target.getPath();
            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                throw new SecurityException("Path escapes the workspace: " + value);
            }
            return target;
        } catch (IOException error) {
            throw new SecurityException("Cannot resolve path: " + value, error);
        }
    }

    /** Resolves without containment when the user explicitly disabled path protection. */
    public static File resolve(File root, String relative, boolean bypass) {
        if (!bypass) return resolve(root, relative);
        File target = new File(relative == null ? "" : relative.trim());
        return target.isAbsolute() || root == null ? target : new File(root, target.getPath());
    }

    /** Whether the path exists and is inside the workspace. */
    public static boolean contains(File root, File target) {
        if (root == null || target == null) return false;
        try {
            String rootPath = root.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
        } catch (IOException error) {
            return false;
        }
    }
}
