package cn.lineai.tool.mcp;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.McpToolInfo;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCatalog;
import cn.lineai.tool.api.ToolRegistry;

/**
 * Adds the enabled tools of every enabled MCP server to the built-in catalog.
 *
 * <p>Rebuilt on every read, so toggling an extension or re-querying its tools takes effect on the
 * next request without restarting the app.</p>
 */
public final class McpToolCatalog implements ToolCatalog {

    private final ToolCatalog base;
    private final Supplier<List<McpExtension>> servers;
    private final McpCaller caller;

    public McpToolCatalog(ToolCatalog base, Supplier<List<McpExtension>> servers, McpCaller caller) {
        this.base = base;
        this.servers = servers;
        this.caller = caller;
    }

    @Override public ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry();
        Set<String> used = new HashSet<>();
        if (base != null) {
            for (Tool tool : base.registry().all()) {
                registry.register(tool);
                used.add(tool.name());
            }
        }
        List<McpExtension> current = servers == null ? null : servers.get();
        if (current == null) return registry;
        for (McpExtension server : current) {
            if (!server.enabled) continue;
            for (McpToolInfo remote : server.tools) {
                if (!remote.enabled) continue;
                String name = uniqueName("mcp_" + slug(server.name) + "_" + slug(remote.name), used);
                used.add(name);
                registry.register(new McpTool(server, remote, name, caller));
            }
        }
        return registry;
    }

    private static String uniqueName(String candidate, Set<String> used) {
        String base = sanitize(candidate);
        if (base.length() > 60) base = base.substring(0, 60);
        if (!used.contains(base)) return base;
        int index = 2;
        while (used.contains(base + "_" + index)) index++;
        return base + "_" + index;
    }

    private static String slug(String value) {
        return sanitize(value == null ? "" : value);
    }

    private static String sanitize(String value) {
        StringBuilder out = new StringBuilder();
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if (ch == '_' || ch == '-' || Character.isWhitespace(ch)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '_') out.append('_');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
            out.setLength(out.length() - 1);
        }
        return out.length() == 0 ? "tool" : out.toString();
    }
}
