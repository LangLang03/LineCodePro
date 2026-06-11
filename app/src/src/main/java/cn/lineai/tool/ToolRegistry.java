package cn.lineai.tool;

import android.content.Context;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.CustomAgentExtensionTool;
import cn.lineai.tool.builtin.CustomMcpHttpTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.tool.builtin.ImageGenerationTool;
import cn.lineai.tool.builtin.ImageUnderstandingTool;
import cn.lineai.tool.builtin.ListDirectoryTool;
import cn.lineai.tool.builtin.ShellExecuteTool;
import cn.lineai.tool.builtin.WebFetchTool;
import cn.lineai.tool.builtin.WebSearchTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;

public final class ToolRegistry {
    private static final String CUSTOM_AGENT_PREFIX = "agentx_";
    private static final String CUSTOM_MCP_PREFIX = "mcpx_";

    private final Map<String, BaseTool> tools = new LinkedHashMap<>();
    private final Context context;

    public ToolRegistry() {
        this(null);
    }

    public ToolRegistry(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        register(new FileReadTool());
        register(new FileWriteTool());
        register(new FileEditTool());
        register(new FileDeleteTool());
        register(new GlobTool());
        register(new ListDirectoryTool());
        register(new HttpServerTool());
        register(new AgentTool());
        register(new AgentPipelineTool());
        register(new ShellExecuteTool(context));
        register(new ImageUnderstandingTool(context));
        register(new ImageGenerationTool(context));
        register(new WebSearchTool(context == null ? null : new WebSearchConfigRepository(context)));
        register(new WebFetchTool());
        reloadExtensions();
    }

    public void register(BaseTool tool) {
        if (tool != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public BaseTool get(String name) {
        return tools.get(name);
    }

    public List<BaseTool> getAll() {
        return new ArrayList<>(tools.values());
    }

    public void reloadExtensions() {
        removeExtensionTools();
        if (context == null) {
            return;
        }
        ExtensionRepository repository = new ExtensionRepository(context);
        for (ExtensionMcpConfig mcp : repository.getMcpExtensions()) {
            if (!mcp.isEnabled()) {
                continue;
            }
            for (McpToolSummary tool : mcp.getTools()) {
                if (tool.isEnabled()) {
                    register(new CustomMcpHttpTool(customMcpToolName(mcp, tool), mcp, tool));
                }
            }
        }
        for (ExtensionAgentConfig agent : repository.getAgentExtensions()) {
            if (agent.isEnabled()) {
                register(new CustomAgentExtensionTool(customAgentToolName(agent), agent));
            }
        }
    }

    public List<BaseTool> getByNameSet(Set<String> names) {
        ArrayList<BaseTool> selected = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            return selected;
        }
        for (BaseTool tool : tools.values()) {
            if (names.contains(tool.getName())) {
                selected.add(tool);
            }
        }
        return selected;
    }

    public static JSONArray toJsonArray(Collection<BaseTool> tools) throws org.json.JSONException {
        JSONArray array = new JSONArray();
        if (tools == null) {
            return array;
        }
        for (BaseTool tool : tools) {
            array.put(tool.toJson());
        }
        return array;
    }

    public static boolean isExtensionToolName(String name) {
        return name != null && (name.startsWith(CUSTOM_AGENT_PREFIX) || name.startsWith(CUSTOM_MCP_PREFIX));
    }

    public static boolean isCustomAgentToolName(String name) {
        return name != null && name.startsWith(CUSTOM_AGENT_PREFIX);
    }

    public static boolean isCustomMcpToolName(String name) {
        return name != null && name.startsWith(CUSTOM_MCP_PREFIX);
    }

    public static String customAgentToolName(ExtensionAgentConfig agent) {
        return CUSTOM_AGENT_PREFIX + safeToolNamePart(agent == null ? "" : agent.getSlug(), "agent", 55);
    }

    public static String customMcpToolName(ExtensionMcpConfig mcp, McpToolSummary tool) {
        String toolPart = safeToolNamePart(tool == null ? "" : tool.getName(), "tool", 42);
        String hash = shortHash(mcp == null ? "" : mcp.getId());
        String value = CUSTOM_MCP_PREFIX + hash + "_" + toolPart;
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private void removeExtensionTools() {
        ArrayList<String> names = new ArrayList<>(tools.keySet());
        for (String name : names) {
            if (isExtensionToolName(name)) {
                tools.remove(name);
            }
        }
    }

    private static String safeToolNamePart(String value, String fallback, int maxLength) {
        String raw = value == null ? "" : value.trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length() && builder.length() < maxLength; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        String clean = trimUnderscore(builder.toString());
        if (clean.length() == 0) {
            clean = fallback;
        }
        char first = clean.charAt(0);
        if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z'))) {
            clean = fallback + "_" + clean;
        }
        return clean;
    }

    private static String trimUnderscore(String value) {
        String text = value == null ? "" : value;
        while (text.contains("__")) {
            text = text.replace("__", "_");
        }
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '_') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '_') {
            end--;
        }
        return text.substring(start, end);
    }

    private static String shortHash(String value) {
        long hash = 5381L;
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) {
            hash = (hash * 33L + text.charAt(i)) % 2147483647L;
        }
        return Long.toString(Math.abs(hash), 36);
    }
}
