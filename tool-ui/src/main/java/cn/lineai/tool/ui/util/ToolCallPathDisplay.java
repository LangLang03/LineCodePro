package cn.lineai.tool.ui;

import java.io.File;

/**
 * 工具调用路径展示工具：将绝对路径转换为相对于工作区的展示路径，
 * 并处理 file:// 前缀、反斜杠、尾部斜杠等规范化需求。
 */
final class ToolCallPathDisplay {
    private ToolCallPathDisplay() {
    }

    static String workspaceDisplayPath(String workspacePath, String path) {
        String value = normalizePath(path);
        if (value.length() == 0) {
            return "";
        }
        if (!isAbsolutePath(value)) {
            return stripLeadingCurrentDir(value);
        }
        String root = normalizePath(workspacePath);
        if (root.length() == 0 || !isAbsolutePath(root)) {
            return value;
        }
        if (value.equals(root)) {
            return ".";
        }
        String prefix = root.endsWith("/") ? root : root + "/";
        if (value.startsWith(prefix)) {
            return stripLeadingCurrentDir(value.substring(prefix.length()));
        }
        return value;
    }

    private static String normalizePath(String path) {
        if (path == null || path.trim().length() == 0) {
            return "";
        }
        String value = trimFileScheme(path.trim()).replace('\\', '/');
        if (!isAbsolutePath(value)) {
            return trimTrailingSlash(value);
        }
        if (value.startsWith("/")) {
            return trimTrailingSlash(value);
        }
        try {
            return trimTrailingSlash(new File(value).getCanonicalPath().replace('\\', '/'));
        } catch (Exception ignored) {
            return trimTrailingSlash(value);
        }
    }

    private static boolean isAbsolutePath(String path) {
        return path.startsWith("/") || (path.length() > 2 && path.charAt(1) == ':');
    }

    private static String stripLeadingCurrentDir(String path) {
        String value = path == null ? "" : path.replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        return value;
    }

    private static String trimFileScheme(String path) {
        return path.startsWith("file://") ? path.substring("file://".length()) : path;
    }

    private static String trimTrailingSlash(String path) {
        String value = path == null ? "" : path;
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
