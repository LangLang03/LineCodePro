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

    /**
     * Creates the composite catalog.
     *
     * @param base catalog supplying the built-in tools; may be {@code null} to register only
     *             MCP tools
     * @param servers source of the current extension list; may be {@code null}
     * @param caller transport shared by every generated tool
     */
    public McpToolCatalog(ToolCatalog base, Supplier<List<McpExtension>> servers, McpCaller caller) {
        this.base = base;
        this.servers = servers;
        this.caller = caller;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built-in names are claimed first; MCP tools get sanitized aliases derived from
     * {@code mcp_<server>_<tool>} and disambiguated with numeric suffixes, so a registry never
     * ends up with two tools under one name.</p>
     */
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

    /**
     * Produces a unique registry name: sanitized, length-capped to 60 characters and given a
     * numeric suffix when a collision remains.
     *
     * @param candidate preferred name
     * @param used names already claimed in this registry build
     * @return a name not present in {@code used}
     */
    private static String uniqueName(String candidate, Set<String> used) {
        String base = sanitize(candidate);
        if (base.length() > 60) base = base.substring(0, 60);
        if (!used.contains(base)) return base;
        int index = 2;
        while (used.contains(base + "_" + index)) index++;
        return base + "_" + index;
    }

    /**
     * Lowercases a server or tool name for inclusion in a registry alias.
     *
     * @param value server or tool name; may be {@code null}
     * @return sanitized slug, never empty
     */
    private static String slug(String value) {
        return sanitize(value == null ? "" : value);
    }

    /**
     * Reduces a name to lowercase letters, digits and single underscore separators.
     *
     * @param value raw name text
     * @return the slug, or {@code "tool"} when nothing usable remains
     */
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
