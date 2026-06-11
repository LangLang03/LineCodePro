package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsRepository {
    private final LineCodeDatabase database;

    public SettingsRepository(Context context) {
        database = LineCodeDatabase.getInstance(context);
    }

    public synchronized String getString(String key, String fallback) {
        SQLiteDatabase db = database.getReadableDatabase();
        Cursor cursor = db.query(
                "settings",
                new String[] {"value"},
                "key = ?",
                new String[] {key},
                null,
                null,
                null
        );
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return fallback;
        } finally {
            cursor.close();
        }
    }

    public synchronized boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, fallback ? "true" : "false");
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public synchronized long getLong(String key, long fallback) {
        String value = getString(key, "");
        if (value.length() == 0) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public synchronized void setString(String key, String value) {
        upsert(key, value == null ? "" : value, "string");
    }

    public synchronized void setBoolean(String key, boolean value) {
        upsert(key, value ? "true" : "false", "boolean");
    }

    public synchronized void setLong(String key, long value) {
        upsert(key, String.valueOf(value), "long");
    }

    public synchronized void remove(String key) {
        database.getWritableDatabase().delete("settings", "key = ?", new String[] {key});
    }

    public synchronized void clearLineCodeSettings() {
        database.getWritableDatabase().delete(
                "settings",
                "key GLOB ? OR key GLOB ?",
                new String[] {"@lineai_*", "@linecode_*"}
        );
    }

    public synchronized Map<String, String> getLineCodeSettings() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        Cursor cursor = database.getReadableDatabase().query(
                "settings",
                new String[] {"key", "value"},
                "key GLOB ? OR key GLOB ?",
                new String[] {"@lineai_*", "@linecode_*"},
                null,
                null,
                "key ASC"
        );
        try {
            while (cursor.moveToNext()) {
                values.put(cursor.getString(0), cursor.getString(1));
            }
        } finally {
            cursor.close();
        }
        return values;
    }

    private void upsert(String key, String value, String type) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("type", type);
        values.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().insertWithOnConflict(
                "settings",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }
}
