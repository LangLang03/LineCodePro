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

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param clock clock used to timestamp created and renamed conversations
     */
    public SqliteConversationStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Reads every user conversation header (agent conversations excluded), most recently
     * updated first.
     *
     * @return the user conversation headers ordered by update time, descending
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public List<Conversation> list() {
        return listByKind(Conversation.KIND_USER);
    }

    /**
     * Reads every conversation header of one kind, most recently updated first.
     *
     * @param kind the conversation kind to match ({@link Conversation#KIND_USER} and the
     *             agent kinds are the known values)
     * @return the matching conversation headers ordered by update time, descending
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public List<Conversation> listByKind(String kind) {
        SQLiteDatabase db = database.open();
        List<Conversation> result = new ArrayList<>();
        try (Cursor cursor = db.query("conversations", null, "kind=?", new String[] {kind},
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(conversation(cursor));
            }
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "list conversations", e);
        }
        return result;
    }

    /** Builds one header from the current cursor row. */
    private Conversation conversation(Cursor cursor) {
        return new Conversation(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("message_count")),
                cursor.getString(cursor.getColumnIndexOrThrow("kind")),
                cursor.getString(cursor.getColumnIndexOrThrow("parent_id")));
    }

    /**
     * Inserts a new user conversation with a generated identifier, current timestamps and a
     * zero message count.
     *
     * @param title initial display title
     * @return the created conversation
     */
    @Override
    public Conversation create(String title) {
        return create(title, Conversation.KIND_USER, "");
    }

    /**
     * Inserts a new conversation of the given kind with a generated identifier, current
     * timestamps and a zero message count.
     *
     * @param title initial display title
     * @param kind conversation kind ({@link Conversation#KIND_USER} or an agent kind)
     * @param parentId conversation the agent was spawned from; empty for user conversations
     * @return the created conversation
     */
    @Override
    public Conversation create(String title, String kind, String parentId) {
        long now = clock.nowMs();
        Conversation conversation = new Conversation(Ids.prefixed("conv"), title, "", now, now,
                0, kind, parentId);
        ContentValues values = new ContentValues();
        values.put("id", conversation.id);
        values.put("title", conversation.title);
        values.put("project_id", conversation.projectId);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("message_count", 0);
        values.put("kind", conversation.kind);
        values.put("parent_id", conversation.parentId);
        database.open().insertOrThrow("conversations", null, values);
        return conversation;
    }

    /**
     * Renames a conversation and refreshes its update timestamp so it sorts back to the top.
     *
     * @param id conversation identifier; unknown identifiers are ignored
     * @param title new display title
     */
    @Override
    public void rename(String id, String title) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("updated_at", clock.nowMs());
        database.open().update("conversations", values, "id=?", new String[] {id});
    }

    /**
     * Deletes a conversation; dependent messages, blocks and tool calls are removed by foreign
     * keys.
     *
     * @param id conversation identifier; unknown identifiers are ignored
     */
    @Override
    public void delete(String id) {
        database.open().delete("conversations", "id=?", new String[] {id});
    }

    /**
     * Updates only the sort timestamp and the denormalized message counter, leaving title and
     * project binding untouched.
     *
     * @param id conversation identifier; unknown identifiers are ignored
     * @param nowMs new update timestamp in epoch milliseconds
     * @param messageCount number of messages now held by the conversation
     */
    @Override
    public void touch(String id, long nowMs, int messageCount) {
        ContentValues values = new ContentValues();
        values.put("updated_at", nowMs);
        values.put("message_count", messageCount);
        database.open().update("conversations", values, "id=?", new String[] {id});
    }
}
