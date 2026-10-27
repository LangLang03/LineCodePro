package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import java.io.File;
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
public class SqliteSettingsStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteSettingsStore settings;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "settings.db"));
        settings = new SqliteSettingsStore(database, Clock.fixed(1));
    }

    @After public void close() { database.close(); }

    @Test public void failedWriteDoesNotPoisonCacheOrDisk() {
        settings.put("setting", "before");
        database.open().execSQL("CREATE TRIGGER reject_settings BEFORE INSERT ON settings BEGIN SELECT RAISE(ABORT, 'test rejection'); END");
        DomainException error = assertThrows(DomainException.class, () -> settings.put("setting", "after"));
        assertEquals(ErrorCode.STORAGE, error.code());
        assertEquals("before", settings.get("setting", "missing"));
        assertEquals("before", new SqliteSettingsStore(database, Clock.fixed(1)).get("setting", "missing"));
    }

    @Test public void successfulWriteSurvivesDatabaseReopen() {
        settings.put("setting", "value");
        database.close();
        assertEquals("value", new SqliteSettingsStore(database, Clock.fixed(2)).get("setting", "missing"));
    }

    @Test public void readsUseSnapshotUntilAWriteThroughUpdate() {
        settings.put("setting", "cached");
        database.open().execSQL("UPDATE settings SET value='direct' WHERE key='setting'");
        for (int i = 0; i < 100; i++) assertEquals("cached", settings.get("setting", "missing"));
        settings.put("setting", "latest");
        assertEquals("latest", settings.get("setting", "missing"));
    }

    @Test public void returnedMapsCannotMutateCache() {
        settings.put("setting", "value");
        settings.all().clear();
        assertEquals("value", settings.get("setting", "missing"));
    }

    @Test public void missingSettingsTableIsReportedAsDomainError() {
        database.open().execSQL("DROP TABLE settings");
        assertEquals(ErrorCode.STORAGE, assertThrows(DomainException.class, settings::all).code());
    }
}
