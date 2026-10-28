package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.MemoryItem;
import cn.lineai.data.api.MemoryStore;

/** SQLite-backed long-term memory. */
public final class SqliteMemoryStore implements MemoryStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    public SqliteMemoryStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override
    public List<MemoryItem> list(String projectId) {
        List<MemoryItem> memories = new ArrayList<>();
        try (Cursor cursor = database.open().query("memories", null,
                "scope<>? OR project_id=?", new String[] {MemoryItem.SCOPE_PROJECT, safe(projectId)},
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) memories.add(read(cursor));
        }
        return memories;
    }

    @Override
    public void save(MemoryItem memory) {
        long now = clock.nowMs();
        boolean insert = memory.id.isEmpty();
        String id = insert ? Ids.prefixed("mem") : memory.id;
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("scope", memory.scope);
        values.put("project_id", memory.projectId);
        values.put("title", memory.title);
        values.put("content", memory.content);
        values.put("source", memory.source.isEmpty() ? "manual" : memory.source);
        values.put("confidence", memory.confidence);
        values.put("created_at", insert ? now : memory.createdAt);
        values.put("updated_at", now);
        values.put("last_used_at", memory.lastUsedAt);
        values.put("use_count", memory.useCount);
        database.open().insertWithOnConflict("memories", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void delete(String id) {
        database.open().delete("memories", "id=?", new String[] {id});
    }

    @Override
    public void deleteAll(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            for (String id : ids) db.delete("memories", "id=?", new String[] {id});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static MemoryItem read(Cursor cursor) {
        return new MemoryItem(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("scope")),
                cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")),
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("last_used_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("use_count")));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
