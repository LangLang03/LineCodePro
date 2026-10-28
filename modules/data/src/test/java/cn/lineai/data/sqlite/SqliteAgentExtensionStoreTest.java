package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.AgentExtension;

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
public class SqliteAgentExtensionStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteAgentExtensionStore store;
    private long now = 100L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "extensions.db"));
        store = new SqliteAgentExtensionStore(database, () -> now++);
    }

    @After public void close() {
        database.close();
    }

    private AgentExtension agent(String name, String slug, List<String> tools) {
        return AgentExtension.builder()
                .name(name)
                .slug(slug)
                .prompt("prompt for " + name)
                .trigger("when " + name)
                .toolNames(tools)
                .build();
    }

    @Test public void savingAssignsIdAndNormalizesSlug() {
        AgentExtension saved = store.save(agent("Test Fixer", "Test Fixer", Arrays.asList(
                "read_file", "edit_file")));
        assertFalse(saved.id.isEmpty());
        assertEquals("test-fixer", saved.slug);
        assertTrue(saved.createdAt > 0);
        List<AgentExtension> all = store.list();
        assertEquals(1, all.size());
        assertEquals("Test Fixer", all.get(0).name);
        assertEquals(Arrays.asList("read_file", "edit_file"), all.get(0).toolNames);
    }

    @Test public void updateKeepsCreatedAtAndReplacesFields() {
        AgentExtension saved = store.save(agent("First", "first", Arrays.asList("read_file")));
        long created = saved.createdAt;
        now = 500L;
        AgentExtension updated = store.save(saved.toBuilder()
                .name("Second")
                .toolNames(Arrays.asList("glob"))
                .build());
        assertEquals(created, updated.createdAt);
        assertEquals(500L, updated.updatedAt);
        assertEquals("Second", store.list().get(0).name);
        assertEquals(Arrays.asList("glob"), store.list().get(0).toolNames);
    }

    @Test public void togglesAndDeletes() {
        AgentExtension saved = store.save(agent("Agent", "agent", Arrays.asList()));
        store.setEnabled(saved.id, false);
        assertFalse(store.list().get(0).enabled);
        store.delete(saved.id);
        assertTrue(store.list().isEmpty());
    }
}
