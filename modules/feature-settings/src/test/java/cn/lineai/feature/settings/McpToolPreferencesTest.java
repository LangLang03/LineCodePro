package cn.lineai.feature.settings;

import cn.lineai.core.model.WebSearchConfig;
import java.util.HashMap;
import java.util.Map;
import cn.lineai.data.api.SettingsStore;
import org.junit.Test;
import static org.junit.Assert.*;

public class McpToolPreferencesTest {

    @Test public void defaultsToLocalAndGroupDefaults() {
        Memory settings = new Memory();
        McpPreferences mcp = new McpPreferences(settings);
        assertEquals(McpPreferences.MODE_LOCAL, mcp.mode());
        assertTrue(mcp.enabled("file_ops", true));
        assertFalse(mcp.enabled("file_ops", false));
    }

    @Test public void switchesAreScopedPerExecutionMode() {
        Memory settings = new Memory();
        McpPreferences mcp = new McpPreferences(settings);
        mcp.setEnabled("file_ops", false);
        mcp.setMode(McpPreferences.MODE_SSH);
        mcp.setEnabled("shell", false);
        assertTrue(mcp.enabled("file_ops", true));
        assertFalse(mcp.enabled("shell", true));
        mcp.setMode(McpPreferences.MODE_LOCAL);
        assertFalse(mcp.enabled("file_ops", true));
        assertTrue(mcp.enabled("shell", true));
        assertEquals("local", settings.get("mcp.executionMode", ""));
    }

    @Test public void invalidModeFallsBackToLocal() {
        Memory settings = new Memory();
        McpPreferences mcp = new McpPreferences(settings);
        mcp.setMode("bogus");
        assertEquals(McpPreferences.MODE_LOCAL, mcp.mode());
    }

    @Test public void webSearchDefaultsAndRoundTrip() {
        Memory settings = new Memory();
        ToolPreferences tools = new ToolPreferences(settings);
        assertEquals(WebSearchConfig.defaultConfig().baseUrl, tools.webSearch().baseUrl);
        assertEquals("", tools.imageUnderstandingModelId());
        tools.setImageUnderstandingModelId("m1");
        assertEquals("m1", tools.imageUnderstandingModelId());
        WebSearchConfig custom = new WebSearchConfig(WebSearchConfig.PROVIDER_TAVILY,
                "https://example.com/search", "key", "model", "query", "Authorization", "");
        tools.saveWebSearch(custom);
        WebSearchConfig loaded = new ToolPreferences(settings).webSearch();
        assertEquals(WebSearchConfig.PROVIDER_TAVILY, loaded.provider);
        assertEquals("https://example.com/search", loaded.baseUrl);
        assertEquals("key", loaded.apiKey);
    }

    static final class Memory implements SettingsStore {
        final Map<String, String> values = new HashMap<>();
        public String get(String key, String fallback) { return values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) {
            try { return Integer.parseInt(get(key, String.valueOf(fallback))); }
            catch (NumberFormatException error) { return fallback; }
        }
        public void put(String key, String value) { values.put(key, value); }
        public void remove(String key) { values.remove(key); }
        public void invalidate() { }
        public Map<String, String> all() { return new HashMap<>(values); }
    }
}
