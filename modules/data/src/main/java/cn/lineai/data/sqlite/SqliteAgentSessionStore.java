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
import cn.lineai.core.model.AgentSessionRecord;
import cn.lineai.core.model.AgentSessionStatus;
import cn.lineai.data.api.AgentSessionStore;

/** SQLite-backed sub agent session records, oldest first. */
public final class SqliteAgentSessionStore implements AgentSessionStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    /**
     * Creates the store.
     *
     * @param database shared connection holder
     * @param clock timestamp source reserved for future record bookkeeping
     */
    public SqliteAgentSessionStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override public void save(AgentSessionRecord record) {
        ContentValues values = new ContentValues();
        values.put("id", record.id);
        values.put("name", record.name);
        values.put("parent_conversation_id", record.parentConversationId);
        values.put("conversation_id", record.conversationId);
        values.put("agent_definition_id", record.agentDefinitionId);
        values.put("workspace_binding", record.workspaceBinding);
        values.put("status", record.status.name());
        values.put("persistent", record.persistent ? 1 : 0);
        values.put("created_at", record.createdAt);
        values.put("updated_at", record.updatedAt);
        values.put("last_turn_id", record.lastTurnId);
        database.open().insertWithOnConflict("agent_sessions", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override public List<AgentSessionRecord> list() {
        List<AgentSessionRecord> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("agent_sessions", null, null, null,
                null, null, "created_at ASC")) {
            while (cursor.moveToNext()) {
                result.add(record(cursor));
            }
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "list agent sessions", e);
        }
        return result;
    }

    @Override public AgentSessionRecord find(String id) {
        try (Cursor cursor = database.open().query("agent_sessions", null, "id=?",
                new String[] {id}, null, null, null)) {
            return cursor.moveToFirst() ? record(cursor) : null;
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "find agent session", e);
        }
    }

    @Override public void delete(String id) {
        database.open().delete("agent_sessions", "id=?", new String[] {id});
    }

    /** Builds one record from the current cursor row. */
    private static AgentSessionRecord record(Cursor cursor) {
        return new AgentSessionRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("parent_conversation_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("agent_definition_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("workspace_binding")),
                AgentSessionStatus.parse(
                        cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                cursor.getInt(cursor.getColumnIndexOrThrow("persistent")) != 0,
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                cursor.getString(cursor.getColumnIndexOrThrow("last_turn_id")));
    }
}
