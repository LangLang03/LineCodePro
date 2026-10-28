package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.model.MemoryItem;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteMemoryStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteMemoryStore memories;
    private long now = 10L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "memory.db"));
        memories = new SqliteMemoryStore(database, () -> now++);
    }

    @After public void close() { database.close(); }

    @Test public void savingANewEntryAssignsIdAndTimestamps() {
        memories.save(item("", MemoryItem.SCOPE_USER, "", "note"));
        List<MemoryItem> list = memories.list("");
        assertEquals(1, list.size());
        assertFalse(list.get(0).id.isEmpty());
        assertEquals("note", list.get(0).content);
        assertTrue(list.get(0).createdAt > 0);
    }

    @Test public void updateKeepsCreatedAtAndRefreshesContent() {
        memories.save(item("", MemoryItem.SCOPE_USER, "", "first"));
        MemoryItem stored = memories.list("").get(0);
        memories.save(new MemoryItem(stored.id, stored.scope, stored.projectId, stored.title,
                "second", stored.source, stored.confidence, stored.createdAt,
                stored.updatedAt, stored.lastUsedAt, stored.useCount));
        MemoryItem updated = memories.list("").get(0);
        assertEquals("second", updated.content);
        assertEquals(stored.createdAt, updated.createdAt);
    }

    @Test public void projectScopeOnlyAppearsForItsProject() {
        memories.save(item("", MemoryItem.SCOPE_PROJECT, "p1", "project one"));
        memories.save(item("", MemoryItem.SCOPE_PROJECT, "p2", "project two"));
        memories.save(item("", MemoryItem.SCOPE_USER, "", "global"));
        assertEquals(2, memories.list("p1").size());
        assertEquals(2, memories.list("p2").size());
        assertEquals(1, memories.list("").size());
    }

    @Test public void deletesSingleAndBatchEntries() {
        memories.save(item("", MemoryItem.SCOPE_USER, "", "a"));
        memories.save(item("", MemoryItem.SCOPE_USER, "", "b"));
        String first = memories.list("").get(0).id;
        memories.delete(first);
        assertEquals(1, memories.list("").size());
        String remaining = memories.list("").get(0).id;
        memories.deleteAll(Arrays.asList(remaining));
        assertTrue(memories.list("").isEmpty());
    }

    private static MemoryItem item(String id, String scope, String projectId, String content) {
        return new MemoryItem(id, scope, projectId, "", content, "manual", 1.0, 0, 0, 0, 0);
    }
}
