package cn.lineai.tool.ui;
import cn.lineai.model.tool.ToolCall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONObject;

/**
 * 工具调用工具集合的门面：聚合 {@link ToolCallInputParser}、
 * {@link ToolCallPathDisplay}、{@link ToolCallJsonFormatter} 的静态方法，
 * 调用方只需 import 这一个类。
 */
public final class ToolCallUtils {
    private ToolCallUtils() {
    }

    public static ToolDisplayCategory getDisplayCategory(String name) {
        cn.lineai.tool.ui.ToolInfoResolver resolver = cn.lineai.tool.ui.ToolInfoResolverProvider.getDefault();
        if (resolver != null) {
            return resolver.getDisplayCategory(name);
        }
        return ToolDisplayCategory.fallbackDisplayCategory(name);
    }

    static JSONObject parseInput(ToolCall toolCall) {
        return ToolCallInputParser.parseInput(toolCall);
    }

    static String inputLabel(String name, JSONObject input) {
        return ToolCallInputParser.inputLabel(name, input);
    }

    static String displayInputLabel(Context context, String name, JSONObject input, String workspacePath) {
        return ToolCallInputParser.displayInputLabel(context, name, input, workspacePath);
    }

    static String workspaceDisplayPath(String workspacePath, String path) {
        return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, path);
    }

    static String prettyJson(JSONObject input) {
        return ToolCallJsonFormatter.prettyJson(input);
    }

    static boolean isImageGenerationTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.IMAGE_GENERATION;
    }

    static boolean isReadTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.READ;
    }

    static boolean isWriteTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.WRITE;
    }

    static boolean isDeleteTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.DELETE;
    }

    static boolean isCustomMcpTool(String name) {
        return name != null && name.startsWith("mcpx_");
    }

    static boolean isCustomAgentTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.AGENT && name != null && name.startsWith("agentx_");
    }

    static boolean isShellTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.SHELL;
    }

    static boolean isAgentTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.AGENT && (name == null || !name.startsWith("agentx_"));
    }

    static boolean isAgentPipelineTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.AGENT_PIPELINE;
    }

    static boolean isTodoTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.TODO;
    }

    static boolean isPhoneControlTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.PHONE_CONTROL;
    }
}
