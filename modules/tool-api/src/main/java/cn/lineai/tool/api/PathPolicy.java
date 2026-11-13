package cn.lineai.tool.api;

import java.io.File;
import java.io.IOException;

/**
 * The single containment check for workspace paths.
 *
 * <p>All file tools funnel path resolution through this class so a model-supplied path can never
 * reach outside the workspace root through {@code ..} segments, absolute paths or symlinks.</p>
 */
public final class PathPolicy {

    /** Utility class; not instantiable. */
    private PathPolicy() {
    }

    /**
     * Resolves {@code relative} under {@code root}, rejecting escapes through {@code ..},
     * absolute paths and symlinks that leave the workspace.
     *
     * @param root workspace root directory; must not be {@code null}
     * @param relative tool-supplied path, always interpreted as relative to {@code root}
     * @return the canonical file inside the workspace
     * @throws IllegalArgumentException when {@code root} is {@code null}
     * @throws SecurityException when the path is absolute or escapes the workspace
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

    /**
     * Resolves without containment when the user explicitly disabled path protection.
     *
     * <p>This is the only sanctioned escape hatch; it is driven by an explicit user switch, never
     * by a tool argument.</p>
     *
     * @param root workspace root, used only to make relative paths usable
     * @param relative tool-supplied path, absolute or relative
     * @param bypass {@code true} to skip all containment checks
     * @return the resolved file, which may lie outside the workspace when bypassed
     */
    public static File resolve(File root, String relative, boolean bypass) {
        if (!bypass) return resolve(root, relative);
        File target = new File(relative == null ? "" : relative.trim());
        return target.isAbsolute() || root == null ? target : new File(root, target.getPath());
    }

    /**
     * Whether the path exists and is inside the workspace.
     *
     * @param root workspace root; may be {@code null}
     * @param target candidate path; may be {@code null}
     * @return {@code true} only when both roots resolve and the target lies within the root
     */
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
