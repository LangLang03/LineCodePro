package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.data.api.ErrorLog;
import cn.lineai.data.api.ErrorLogStore;

/** SQLite-backed failure log, keeping the newest {@link #MAX_ENTRIES} rows. */
public final class SqliteErrorLogStore implements ErrorLogStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param clock clock used to timestamp new entries
     */
    public SqliteErrorLogStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Reads every retained entry, newest first.
     *
     * @return all entries in reverse creation order
     */
    @Override
    public List<ErrorLog> list() {
        List<ErrorLog> logs = new ArrayList<>();
        try (Cursor cursor = database.open().query("error_logs", null, null, null,
                null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                logs.add(new ErrorLog(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        cursor.getString(cursor.getColumnIndexOrThrow("subtitle")),
                        cursor.getString(cursor.getColumnIndexOrThrow("detail")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))));
            }
        }
        return logs;
    }

    /**
     * Appends one entry with a fresh identifier, then trims the table back to
     * {@link #MAX_ENTRIES} rows so the log cannot grow without bound.
     *
     * @param title short headline; null is stored as empty
     * @param subtitle optional secondary line; null is stored as empty
     * @param detail full technical description; null is stored as empty
     */
    @Override
    public void record(String title, String subtitle, String detail) {
        SQLiteDatabase db = database.open();
        ContentValues values = new ContentValues();
        values.put("id", Ids.prefixed("log"));
        values.put("title", title == null ? "" : title);
        values.put("subtitle", subtitle == null ? "" : subtitle);
        values.put("detail", detail == null ? "" : detail);
        values.put("created_at", clock.nowMs());
        db.insert("error_logs", null, values);
        db.execSQL("DELETE FROM error_logs WHERE id NOT IN "
                + "(SELECT id FROM error_logs ORDER BY created_at DESC LIMIT " + MAX_ENTRIES + ")");
    }

    /** Deletes every recorded entry. */
    @Override
    public void clear() {
        database.open().delete("error_logs", null, null);
    }
}
