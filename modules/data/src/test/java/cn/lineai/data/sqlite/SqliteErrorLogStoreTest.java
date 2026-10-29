package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.data.api.ErrorLog;
import java.io.File;
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
public class SqliteErrorLogStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteErrorLogStore logs;
    private long now = 1L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "logs.db"));
        logs = new SqliteErrorLogStore(database, () -> now++);
    }

    @After public void close() { database.close(); }

    @Test public void newestEntryComesFirst() {
        logs.record("first", "a", "detail-a");
        logs.record("second", "b", "detail-b");
        List<ErrorLog> list = logs.list();
        assertEquals(2, list.size());
        assertEquals("second", list.get(0).title);
        assertEquals("detail-b", list.get(0).detail);
        assertEquals(2L, list.get(0).createdAt);
        assertEquals(1L, list.get(1).createdAt);
    }

    @Test public void keepsOnlyTheNewestEntries() {
        for (int i = 0; i < SqliteErrorLogStore.MAX_ENTRIES + 5; i++) {
            logs.record("t" + i, "s", "d" + i);
        }
        List<ErrorLog> list = logs.list();
        assertEquals(SqliteErrorLogStore.MAX_ENTRIES, list.size());
        assertEquals("t" + (SqliteErrorLogStore.MAX_ENTRIES + 4), list.get(0).title);
    }

    @Test public void clearRemovesEverything() {
        logs.record("t", "s", "d");
        logs.clear();
        assertTrue(logs.list().isEmpty());
    }
}
