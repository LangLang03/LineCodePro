package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.McpHeader;
import cn.lineai.core.model.McpToolInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteMcpExtensionStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteMcpExtensionStore store;
    private long now = 10L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "mcp.db"));
        store = new SqliteMcpExtensionStore(database, () -> now++);
    }

    @After public void close() {
        database.close();
    }

    private McpExtension server() {
        return McpExtension.builder()
                .name("Company MCP")
                .url("https://example.com/mcp")
                .headers(Arrays.asList(new McpHeader("Authorization", "Bearer token"),
                        new McpHeader("X-Team", "linecode")))
                .tools(Arrays.asList(
                        new McpToolInfo("search", true, "Search docs", "{\"type\":\"object\"}"),
                        new McpToolInfo("write", false, "Write docs", "{}")))
                .build();
    }

    @Test public void savingRoundTripsHeadersAndTools() {
        McpExtension saved = store.save(server());
        assertFalse(saved.id.isEmpty());
        List<McpExtension> all = store.list();
        assertEquals(1, all.size());
        McpExtension loaded = all.get(0);
        assertEquals("https://example.com/mcp", loaded.url);
        assertEquals(2, loaded.headers.size());
        assertEquals("Authorization", loaded.headers.get(0).name);
        assertEquals("Bearer token", loaded.headers.get(0).value);
        assertEquals(2, loaded.tools.size());
        assertEquals("search", loaded.tools.get(0).name);
        assertTrue(loaded.tools.get(0).enabled);
        assertFalse(loaded.tools.get(1).enabled);
        assertTrue(loaded.tools.get(0).inputSchemaJson.contains("object"));
    }

    @Test public void updateKeepsCreatedAtAndReplacesTools() {
        McpExtension saved = store.save(server());
        long created = saved.createdAt;
        now = 99L;
        McpExtension updated = store.save(saved.toBuilder()
                .tools(Arrays.asList(new McpToolInfo("ping", true, "", "{}")))
                .build());
        assertEquals(created, updated.createdAt);
        assertEquals(99L, updated.updatedAt);
        assertEquals(1, store.list().get(0).tools.size());
        assertEquals("ping", store.list().get(0).tools.get(0).name);
    }

    @Test public void togglesAndDeletes() {
        McpExtension saved = store.save(server());
        store.setEnabled(saved.id, false);
        assertFalse(store.list().get(0).enabled);
        store.delete(saved.id);
        assertTrue(store.list().isEmpty());
    }
}
