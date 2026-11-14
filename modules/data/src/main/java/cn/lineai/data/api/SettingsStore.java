package cn.lineai.data.api;

import java.util.Map;

/**
 * Key/value settings. One full read at startup, write-through afterwards.
 *
 * <p>Reads are served from memory once the initial load completed, so a generation loop never
 * touches the database. {@link #invalidate()} must be called after an archive restore so cached
 * values cannot shadow the restored table.</p>
 */
public interface SettingsStore {

    /**
     * Returns one setting value.
     *
     * @param key settings key
     * @param fallback value returned when the key is absent
     * @return stored value or the fallback
     */
    String get(String key, String fallback);

    /**
     * Returns one setting parsed as an integer.
     *
     * @param key settings key
     * @param fallback value returned when the key is absent or not parseable
     * @return stored value or the fallback
     */
    int getInt(String key, int fallback);

    /**
     * Writes one setting through to the database and the cache. A null value is stored as empty.
     *
     * @param key settings key
     * @param value value to store; null is stored as the empty string
     */
    void put(String key, String value);

    /**
     * Deletes one setting from the database and the cache.
     *
     * @param key settings key; unknown keys are ignored
     */
    void remove(String key);

    /** Drops the in-memory cache so the next read hits the database again. */
    void invalidate();

    /**
     * Returns a copy of every cached setting, primarily for diagnostics and generation snapshots.
     *
     * @return snapshot of all settings keyed by their settings key
     */
    Map<String, String> all();
}
