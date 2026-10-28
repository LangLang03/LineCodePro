package cn.lineai.tool.mcp;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.McpToolInfo;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCatalog;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class McpToolCatalogTest {

    private McpExtension server(boolean enabled, boolean toolEnabled) {
        return McpExtension.builder()
                .name("Company MCP")
                .url("https://example.com/mcp")
                .enabled(enabled)
                .tools(Arrays.asList(new McpToolInfo("Search Docs", toolEnabled,
                        "Search docs", "{\"type\":\"object\"}")))
                .build();
    }

    @Test public void exposesOnlyEnabledServersAndTools() {
        List<McpExtension> servers = new ArrayList<>();
        servers.add(server(true, true));
        servers.add(server(false, true));
        McpToolCatalog catalog = new McpToolCatalog(() -> new ToolRegistry(), () -> servers,
                (url, headers, name, args) -> "ok");
        ToolRegistry registry = catalog.registry();
        assertEquals(1, registry.all().size());
        Tool tool = registry.find("mcp_company_mcp_search_docs");
        assertTrue(tool != null);
        assertTrue(tool.mutating());
        assertEquals("{\"type\":\"object\"}", tool.parametersJson());

        servers.set(0, server(true, false));
        assertTrue(catalog.registry().all().isEmpty());
    }

    @Test public void executesThroughTheInjectedCaller() {
        McpToolCatalog catalog = new McpToolCatalog(() -> new ToolRegistry(),
                () -> Arrays.asList(server(true, true)),
                (url, headers, name, args) -> "result:" + name + ":" + args);
        Tool tool = catalog.registry().find("mcp_company_mcp_search_docs");
        ToolResult result = tool.execute(null, new JSONObject("{\"q\":\"x\"}"));
        assertFalse(result.error);
        assertEquals("result:Search Docs:{\"q\":\"x\"}", result.content);
    }

    @Test public void keepsBuiltInToolsAndAvoidsNameCollisions() {
        ToolRegistry builtIn = new ToolRegistry().register(new Tool() {
            public String name() { return "mcp_company_mcp_search_docs"; }
            public String description() { return ""; }
            public String parametersJson() { return "{}"; }
            public boolean mutating() { return false; }
            public ToolResult execute(cn.lineai.tool.api.ToolContext context, JSONObject args) {
                return ToolResult.success("", "");
            }
        });
        McpToolCatalog catalog = new McpToolCatalog(() -> builtIn,
                () -> Arrays.asList(server(true, true)),
                (url, headers, name, args) -> "ok");
        ToolRegistry registry = catalog.registry();
        assertEquals(2, registry.all().size());
        assertFalse("", registry.find("mcp_company_mcp_search_docs").mutating());
        assertTrue(registry.find("mcp_company_mcp_search_docs_2") != null);
        assertNull(registry.find("missing"));
    }
}
