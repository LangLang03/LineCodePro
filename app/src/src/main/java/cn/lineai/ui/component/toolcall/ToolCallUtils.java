package cn.lineai.ui.component.toolcall;

import cn.lineai.tool.ToolCall;
import java.io.File;
import org.json.JSONObject;

final class ToolCallUtils {
    private ToolCallUtils() {
    }

    static JSONObject parseInput(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments().trim().length() == 0) {
            return new JSONObject();
        }
        try {
            return new JSONObject(toolCall.getArguments());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    static String inputLabel(String name, JSONObject input) {
        if (input == null) {
            return name;
        }
        String[] keys = new String[] {"file_path", "pattern", "query", "url", "path", "command"};
        for (String key : keys) {
            String value = input.optString(key);
            if (value.length() > 0) {
                return value;
            }
        }
        return name == null ? "" : name;
    }

    static String displayInputLabel(String name, JSONObject input, String workspacePath) {
        if (input == null) {
            return name == null ? "" : name;
        }
        if ("file_read".equals(name)) {
            String filePath = input.optString("file_path");
            if (filePath.length() > 0) {
                return workspaceDisplayPath(workspacePath, filePath);
            }
        }
        if ("list_dir".equals(name)) {
            String path = input.optString("path");
            if (path.length() > 0) {
                return workspaceDisplayPath(workspacePath, path);
            }
        }
        return inputLabel(name, input);
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

    static String prettyJson(JSONObject input) {
        if (input == null) {
            return "{}";
        }
        try {
            return input.toString(2);
        } catch (Exception ignored) {
            return input.toString();
        }
    }

    static boolean isReadTool(String name) {
        return "file_read".equals(name) || "glob".equals(name) || "list_dir".equals(name)
                || "web_search".equals(name) || "web_fetch".equals(name)
                || "image_understanding".equals(name)
                || "image_generation".equals(name);
    }

    static boolean isWriteTool(String name) {
        return "file_write".equals(name) || "file_edit".equals(name);
    }

    static boolean isDeleteTool(String name) {
        return "file_delete".equals(name);
    }

    static boolean isHttpTool(String name) {
        return "http_server".equals(name);
    }

    static boolean isCustomMcpTool(String name) {
        return name != null && name.startsWith("mcpx_");
    }

    static boolean isShellTool(String name) {
        return "shell_execute".equals(name);
    }

    static boolean isAgentTool(String name) {
        return "agent".equals(name);
    }

    static boolean isAgentPipelineTool(String name) {
        return "agent_pipeline".equals(name);
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
