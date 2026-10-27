package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.HashMap;
import java.util.Map;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.data.api.SettingsStore;

/**
 * Write-through settings cache.
 *
 * <p>The first access loads every key with a single query; afterwards reads are memory-only and
 * writes update both the map and the table. A generation takes one snapshot of this map, so a tool
 * loop never hits SQL.</p>
 */
public final class SqliteSettingsStore implements SettingsStore {

    private final LineCodeDatabase database;
    private final Clock clock;
    private final Map<String, String> cache = new HashMap<>();
    private boolean loaded;

    public SqliteSettingsStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        try (Cursor cursor = database.open().query("settings", new String[] {"key", "value"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                cache.put(cursor.getString(0), cursor.getString(1));
            }
        } catch (android.database.sqlite.SQLiteException error) {
            throw new cn.lineai.core.kernel.error.DomainException(
                    cn.lineai.core.kernel.error.ErrorCode.STORAGE, "load settings", error);
        }
        loaded = true;
    }

    @Override
    public synchronized String get(String key, String fallback) {
        ensureLoaded();
        String value = cache.get(key);
        return value == null ? fallback : value;
    }

    @Override
    public synchronized int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public synchronized void put(String key, String value) {
        ensureLoaded();
        String safe = value == null ? "" : value;
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", safe);
        values.put("updated_at", clock.nowMs());
        try {
            long row = database.open().insertWithOnConflict("settings", null, values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
            if (row == -1) throw new android.database.sqlite.SQLiteException("Cannot save setting");
            cache.put(key, safe);
        } catch (android.database.sqlite.SQLiteException error) {
            throw new cn.lineai.core.kernel.error.DomainException(
                    cn.lineai.core.kernel.error.ErrorCode.STORAGE, "save setting", error);
        }
    }

    @Override
    public synchronized void remove(String key) {
        ensureLoaded();
        try {
            database.open().delete("settings", "key=?", new String[] {key});
            cache.remove(key);
        } catch (android.database.sqlite.SQLiteException error) {
            throw new cn.lineai.core.kernel.error.DomainException(
                    cn.lineai.core.kernel.error.ErrorCode.STORAGE, "remove setting", error);
        }
    }

    @Override
    public synchronized void invalidate() {
        cache.clear();
        loaded = false;
    }

    @Override
    public synchronized Map<String, String> all() {
        ensureLoaded();
        return new HashMap<>(cache);
    }
}
