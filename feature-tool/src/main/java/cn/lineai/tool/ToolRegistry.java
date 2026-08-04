package cn.lineai.tool;

import android.content.Context;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.Strings;
import cn.lineai.tool.builtin.CustomAgentExtensionTool;
import cn.lineai.tool.builtin.CustomMcpHttpTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;

public final class ToolRegistry {
    private static final String CUSTOM_AGENT_PREFIX = "agentx_";
    private static final String CUSTOM_MCP_PREFIX = "mcpx_";

    private final Map<String, BaseTool> tools = new LinkedHashMap<>();
    private final Map<String, ToolDisplayCategory> displayCategoryCache = new LinkedHashMap<>();
    private final Context context;
    private ExtensionStore extensionStore;

    public ToolRegistry() {
        this(null);
    }

    public ToolRegistry(Context context) {
        this(context, null);
    }

    public ToolRegistry(Context context, cn.lineai.ipc.IpcProviderManager ipcProviderManager) {
        this.context = context == null ? null : context.getApplicationContext();
        for (BuiltInToolProvider provider : BuiltInToolProviders.defaults()) {
            BaseTool tool = provider.create(this.context, ipcProviderManager);
            if (tool != null) {
                register(tool);
            }
        }
        reloadExtensions();
    }

    public void setExtensionStore(ExtensionStore extensionStore) {
        this.extensionStore = extensionStore;
    }

    public void register(BaseTool tool) {
        if (tool != null) {
            tools.put(tool.getName(), tool);
            displayCategoryCache.put(tool.getName(), tool.getDisplayCategory());
        }
    }

    public BaseTool get(String name) {
        return tools.get(name);
    }

    public ToolDisplayCategory getCachedDisplayCategory(String name) {
        ToolDisplayCategory category = displayCategoryCache.get(name);
        return category != null ? category : ToolDisplayCategory.GENERIC;
    }

    public List<BaseTool> getAll() {
        return new ArrayList<>(tools.values());
    }

    public void reloadExtensions() {
        removeExtensionTools();
        if (extensionStore == null) {
            return;
        }
        for (ExtensionMcpConfig mcp : extensionStore.getMcpExtensions()) {
            if (!mcp.isEnabled()) {
                continue;
            }
            for (McpToolSummary tool : mcp.getTools()) {
                if (tool.isEnabled()) {
                    register(new CustomMcpHttpTool(customMcpToolName(mcp, tool), mcp, tool));
                }
            }
        }
        for (ExtensionAgentConfig agent : extensionStore.getAgentExtensions()) {
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

    /** 返回 ToolInfo 视图，供 AI 模块使用而不依赖 BaseTool 具体类型 */
    public List<ToolInfo> getToolInfoByNameSet(Set<String> names) {
        ArrayList<ToolInfo> selected = new ArrayList<>();
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

    /** ToolInfo 版本的序列化，供 AI 模块使用 */
    public static JSONArray toToolInfoJsonArray(Collection<ToolInfo> tools) throws org.json.JSONException {
        JSONArray array = new JSONArray();
        if (tools == null) {
            return array;
        }
        for (ToolInfo tool : tools) {
            array.put(tool.toJson());
        }
        return array;
    }

    public static boolean isExtensionToolName(String name) {
        return ToolNames.isExtensionToolName(name);
    }

    public static boolean isCustomAgentToolName(String name) {
        return ToolNames.isCustomAgentToolName(name);
    }

    public static boolean isCustomMcpToolName(String name) {
        return ToolNames.isCustomMcpToolName(name);
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

    public Set<String> mcpToolNamesForIds(List<String> mcpIds) {
        HashSet<String> names = new HashSet<>();
        if (mcpIds == null || mcpIds.isEmpty() || extensionStore == null) {
            return names;
        }
        for (ExtensionMcpConfig mcp : extensionStore.getMcpExtensions()) {
            if (!mcpIds.contains(mcp.getId())) {
                continue;
            }
            for (McpToolSummary tool : mcp.getTools()) {
                if (tool.isEnabled()) {
                    names.add(customMcpToolName(mcp, tool));
                }
            }
        }
        return names;
    }

    private void removeExtensionTools() {
        ArrayList<String> names = new ArrayList<>(tools.keySet());
        for (String name : names) {
            if (isExtensionToolName(name)) {
                tools.remove(name);
                displayCategoryCache.remove(name);
            }
        }
    }

    private static String safeToolNamePart(String value, String fallback, int maxLength) {
        String raw = Strings.nullToEmpty(value).trim();
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
        String text = Strings.nullToEmpty(value);
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
        String text = Strings.nullToEmpty(value);
        for (int i = 0; i < text.length(); i++) {
            hash = (hash * 33L + text.charAt(i)) % 2147483647L;
        }
        return Long.toString(Math.abs(hash), 36);
    }
}
