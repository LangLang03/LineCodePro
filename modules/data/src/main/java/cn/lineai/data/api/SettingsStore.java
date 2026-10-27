package cn.lineai.data.api;

import java.util.Map;

/** Key/value settings. One full read at startup, write-through afterwards. */
public interface SettingsStore {

    String get(String key, String fallback);

    int getInt(String key, int fallback);

    void put(String key, String value);

    void remove(String key);

    /** Drops the in-memory cache so the next read hits the database again. */
    void invalidate();

    Map<String, String> all();
}
