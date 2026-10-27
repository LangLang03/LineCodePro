package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.List;

import cn.lineai.core.model.TerminalProviderConfig;

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
public class SqliteTerminalProviderStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteTerminalProviderStore store;
    private long now = 5L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "ipc.db"));
        store = new SqliteTerminalProviderStore(database, () -> now++);
    }

    @After public void close() {
        database.close();
    }

    private TerminalProviderConfig provider() {
        return new TerminalProviderConfig("", true, "Termux",
                "com.termux", "com.termux.IpcService", "terminal", 0, 0);
    }

    @Test public void addingAssignsIdAndDeduplicatesByService() {
        TerminalProviderConfig first = store.add(provider());
        assertFalse(first.id.isEmpty());
        TerminalProviderConfig second = store.add(provider());
        assertEquals(first.id, second.id);
        assertEquals(1, store.list().size());
        assertEquals("com.termux.IpcService", store.list().get(0).serviceClass);
    }

    @Test public void togglesAndDeletes() {
        TerminalProviderConfig saved = store.add(provider());
        store.setEnabled(saved.id, false);
        assertFalse(store.list().get(0).enabled);
        store.delete(saved.id);
        assertTrue(store.list().isEmpty());
    }
}
