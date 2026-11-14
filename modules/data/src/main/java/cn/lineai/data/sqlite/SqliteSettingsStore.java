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

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param clock clock used to timestamp written settings
     */
    public SqliteSettingsStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /** Loads the whole settings table once; later calls are no-ops. */
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

    /**
     * Returns one cached value, loading the table first when necessary.
     *
     * @param key settings key
     * @param fallback value returned when the key is absent
     * @return stored value or the fallback
     */
    @Override
    public synchronized String get(String key, String fallback) {
        ensureLoaded();
        String value = cache.get(key);
        return value == null ? fallback : value;
    }

    /**
     * Returns one cached value parsed as an integer; unparseable values fall back instead of
     * failing the caller.
     *
     * @param key settings key
     * @param fallback value returned when the key is absent or not parseable
     * @return stored value or the fallback
     */
    @Override
    public synchronized int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Writes the value through to the table and the cache in that order, so a failed write never
     * leaves the cache ahead of the database. A null value is stored as empty.
     *
     * @param key settings key
     * @param value value to store; null is stored as the empty string
     * @throws DomainException with {@code ErrorCode.STORAGE} when the write fails
     */
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

    /**
     * Deletes the row and the cached entry together.
     *
     * @param key settings key; unknown keys are ignored
     * @throws DomainException with {@code ErrorCode.STORAGE} when the delete fails
     */
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

    /**
     * Drops the cache so the next read reloads the table; required after an archive restore so
     * stale values cannot shadow the restored rows.
     */
    @Override
    public synchronized void invalidate() {
        cache.clear();
        loaded = false;
    }

    /**
     * Returns a defensive copy of the whole cache, suitable for one-shot generation snapshots.
     *
     * @return snapshot of all settings keyed by their settings key
     */
    @Override
    public synchronized Map<String, String> all() {
        ensureLoaded();
        return new HashMap<>(cache);
    }
}
