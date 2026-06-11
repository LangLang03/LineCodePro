package cn.lineai.workspace;

import android.content.Context;
import java.io.File;

public final class WorkspacePaths {
    public static final String DEFAULT_PROJECT_ID = "default";
    public static final String SOURCE_DEFAULT = "default";
    public static final String SOURCE_MANAGED = "managed";
    public static final String SOURCE_EXTERNAL = "external";
    public static final String SOURCE_SSH = "ssh";

    private final File linecodeRoot;
    private final File homeRoot;
    private final File projectRoot;
    private final File skillsRoot;

    public WorkspacePaths(Context context) {
        File filesDir = context.getApplicationContext().getFilesDir();
        linecodeRoot = new File(filesDir, ".linecode");
        homeRoot = new File(linecodeRoot, "home");
        projectRoot = new File(linecodeRoot, "project");
        skillsRoot = new File(linecodeRoot, "skills");
    }

    public File getLinecodeRoot() {
        return linecodeRoot;
    }

    public File getHomeRoot() {
        return homeRoot;
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public File getSkillsRoot() {
        return skillsRoot;
    }

    public void ensurePrivateRoots() {
        mkdir(linecodeRoot);
        mkdir(homeRoot);
        mkdir(projectRoot);
        mkdir(skillsRoot);
    }

    public File resolvePrivatePath(String path) {
        if (path == null || path.trim().length() == 0) {
            return homeRoot;
        }
        String value = trimFileScheme(path.trim());
        if (value.startsWith("/")) {
            return new File(value);
        }
        if (value.equals(".linecode")) {
            return linecodeRoot;
        }
        if (value.startsWith(".linecode/")) {
            return new File(linecodeRoot.getParentFile(), value);
        }
        return new File(homeRoot, value);
    }

    public static String displayPath(String path) {
        return trimFileScheme(path == null ? "" : path);
    }

    public static String basename(String path) {
        String value = trimTrailingSlash(displayPath(path));
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    public static String join(String rootPath, String childPath) {
        String root = trimTrailingSlash(displayPath(rootPath));
        if (childPath == null || childPath.length() == 0) {
            return root;
        }
        String child = displayPath(childPath);
        if (child.startsWith("/") || child.startsWith("content://")) {
            return child;
        }
        return root + "/" + trimLeadingSlash(child);
    }

    public static boolean isContentUri(String path) {
        return path != null && path.startsWith("content://");
    }

    private static String trimFileScheme(String path) {
        return path.startsWith("file://") ? path.substring("file://".length()) : path;
    }

    private static String trimLeadingSlash(String path) {
        String value = path == null ? "" : path;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String trimTrailingSlash(String path) {
        String value = path == null ? "" : path;
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void mkdir(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
