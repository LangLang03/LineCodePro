package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.Conversation;
import cn.lineai.data.api.ConversationStore;

/** SQLite-backed conversation headers, newest first. */
public final class SqliteConversationStore implements ConversationStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    public SqliteConversationStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override
    public List<Conversation> list() {
        SQLiteDatabase db = database.open();
        List<Conversation> result = new ArrayList<>();
        try (Cursor cursor = db.query("conversations", null, null, null, null, null,
                "updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(new Conversation(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("message_count"))));
            }
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "list conversations", e);
        }
        return result;
    }

    @Override
    public Conversation create(String title) {
        long now = clock.nowMs();
        Conversation conversation = new Conversation(Ids.prefixed("conv"), title, "", now, now, 0);
        ContentValues values = new ContentValues();
        values.put("id", conversation.id);
        values.put("title", conversation.title);
        values.put("project_id", conversation.projectId);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("message_count", 0);
        database.open().insertOrThrow("conversations", null, values);
        return conversation;
    }

    @Override
    public void rename(String id, String title) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("updated_at", clock.nowMs());
        database.open().update("conversations", values, "id=?", new String[] {id});
    }

    @Override
    public void delete(String id) {
        database.open().delete("conversations", "id=?", new String[] {id});
    }

    @Override
    public void touch(String id, long nowMs, int messageCount) {
        ContentValues values = new ContentValues();
        values.put("updated_at", nowMs);
        values.put("message_count", messageCount);
        database.open().update("conversations", values, "id=?", new String[] {id});
    }
}
