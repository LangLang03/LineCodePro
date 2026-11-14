package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;
import cn.lineai.data.api.MessageStore;

/** SQLite-backed message store. One row per message plus one row per content block. */
public final class SqliteMessageStore implements MessageStore {

    private final LineCodeDatabase database;

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     */
    public SqliteMessageStore(LineCodeDatabase database) {
        this.database = database;
    }

    /**
     * Loads one conversation as an ordered aggregate: message rows in sequence order with blocks
     * and tool call records attached in their stored order.
     *
     * @param conversationId conversation whose messages are loaded
     * @return messages ordered by sequence number, ascending
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public List<Message> load(String conversationId) {
        SQLiteDatabase db = database.open();
        Map<String, List<ContentBlock>> blocks = loadBlocks(db, conversationId);
        Map<String, List<ToolCallRecord>> calls = loadToolCalls(db, conversationId);
        List<Message> messages = new ArrayList<>();
        try (Cursor cursor = db.query("messages", null, "conversation_id=?",
                new String[] {conversationId}, null, null, "seq ASC")) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                Message.Builder builder = Message.builder()
                        .id(id)
                        .conversationId(conversationId)
                        .seq(cursor.getLong(cursor.getColumnIndexOrThrow("seq")))
                        .role(Role.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("role"))))
                        .status(Message.Status.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow("status"))))
                        .visibility(Message.Visibility.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow("visibility"))))
                        .model(cursor.getString(cursor.getColumnIndexOrThrow("model_id")),
                                cursor.getString(cursor.getColumnIndexOrThrow("provider_id")))
                        .tool(cursor.getString(cursor.getColumnIndexOrThrow("tool_call_id")),
                                cursor.getString(cursor.getColumnIndexOrThrow("tool_name")))
                        .toolCalls(calls.get(id))
                        .error(cursor.getString(cursor.getColumnIndexOrThrow("error_code")))
                        .finishReason(cursor.getString(cursor.getColumnIndexOrThrow("finish_reason")))
                        .reviewState(cursor.getString(cursor.getColumnIndexOrThrow("review_state")))
                        .tokens(
                                cursor.getInt(cursor.getColumnIndexOrThrow("input_tokens")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("output_tokens")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("cached_tokens")))
                        .times(
                                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                        .blocks(blocks.get(id));
                messages.add(builder.build());
            }
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "load messages", e);
        }
        return messages;
    }

    /** Loads the content blocks of one conversation, grouped by message id in block order. */
    private Map<String, List<ContentBlock>> loadBlocks(SQLiteDatabase db, String conversationId) {
        Map<String, List<ContentBlock>> result = new HashMap<>();
        String sql = "SELECT b.message_id, b.type, b.text, b.ref_id, b.status "
                + "FROM message_blocks b JOIN messages m ON m.id = b.message_id "
                + "WHERE m.conversation_id = ? ORDER BY b.message_id, b.idx ASC";
        try (Cursor cursor = db.rawQuery(sql, new String[] {conversationId})) {
            while (cursor.moveToNext()) {
                String messageId = cursor.getString(0);
                List<ContentBlock> list = result.computeIfAbsent(messageId, key -> new ArrayList<>());
                list.add(block(cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4)));
            }
        }
        return result;
    }

    /** Loads the tool call records of one conversation, grouped by message id in call order. */
    private Map<String, List<ToolCallRecord>> loadToolCalls(SQLiteDatabase db, String conversationId) {
        Map<String, List<ToolCallRecord>> result = new HashMap<>();
        String sql = "SELECT c.message_id, c.call_id, c.name, c.arguments "
                + "FROM message_tool_calls c JOIN messages m ON m.id = c.message_id "
                + "WHERE m.conversation_id = ? ORDER BY c.message_id, c.idx ASC";
        try (Cursor cursor = db.rawQuery(sql, new String[] {conversationId})) {
            while (cursor.moveToNext()) {
                String messageId = cursor.getString(0);
                List<ToolCallRecord> list = result.computeIfAbsent(messageId, key -> new ArrayList<>());
                list.add(new ToolCallRecord(cursor.getString(1), cursor.getString(2), cursor.getString(3)));
            }
        }
        return result;
    }

    /** Rebuilds the block subtype matching the stored type tag. */
    private static ContentBlock block(String type, String text, String refId, String status) {
        ContentBlock.Type kind = ContentBlock.Type.valueOf(type);
        switch (kind) {
            case TEXT:
                return ContentBlock.text(text);
            case REASONING:
                return ContentBlock.reasoning(text);
            case IMAGE:
                return ContentBlock.image(refId);
            case FILE:
                return ContentBlock.file(refId, text);
            case TOOL_CALL:
                return ContentBlock.toolCall(refId, status);
            case TOOL_RESULT:
                return ContentBlock.toolResult(refId);
            case DIFF:
            default:
                return ContentBlock.diff(refId);
        }
    }

    /**
     * Inserts the message row, its blocks and its tool calls inside one transaction, so a failure
     * cannot leave a message without its blocks.
     *
     * @param message message to insert; the identifier must not collide with an existing row
     * @throws DomainException with {@code ErrorCode.STORAGE} when any insert fails
     */
    @Override
    public void append(Message message) {
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            db.insertOrThrow("messages", null, values(message));
            insertBlocks(db, message);
            insertToolCalls(db, message);
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "append message", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Replaces the message row and rewrites all blocks and tool calls inside one transaction, so
     * stale children from the previous version cannot survive.
     *
     * @param message message to store; must carry the identifier of an existing row
     * @throws DomainException with {@code ErrorCode.STORAGE} when any write fails
     */
    @Override
    public void update(Message message) {
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            db.update("messages", values(message), "id=?", new String[] {message.id});
            db.delete("message_blocks", "message_id=?", new String[] {message.id});
            insertBlocks(db, message);
            db.delete("message_tool_calls", "message_id=?", new String[] {message.id});
            insertToolCalls(db, message);
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "update message", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deletes one message; dependent blocks and tool calls are removed by foreign keys.
     *
     * @param messageId message identifier; unknown identifiers are ignored
     */
    @Override
    public void delete(String messageId) {
        database.open().delete("messages", "id=?", new String[] {messageId});
    }

    /**
     * Counts the messages of one conversation.
     *
     * @param conversationId conversation to count
     * @return number of stored messages for the conversation
     */
    @Override
    public int count(String conversationId) {
        try (Cursor cursor = database.open().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE conversation_id=?", new String[] {conversationId})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Builds the message row values; blocks and tool calls get their own tables. */
    private static ContentValues values(Message message) {
        ContentValues values = new ContentValues();
        values.put("id", message.id);
        values.put("conversation_id", message.conversationId);
        values.put("seq", message.seq);
        values.put("role", message.role.name());
        values.put("status", message.status.name());
        values.put("visibility", message.visibility.name());
        values.put("model_id", message.modelId);
        values.put("provider_id", message.providerId);
        values.put("tool_call_id", message.toolCallId);
        values.put("tool_name", message.toolName);
        values.put("error_code", message.errorCode);
        values.put("finish_reason", message.finishReason);
        values.put("review_state", message.reviewState);
        values.put("input_tokens", message.inputTokens);
        values.put("output_tokens", message.outputTokens);
        values.put("cached_tokens", message.cachedTokens);
        values.put("created_at", message.createdAt);
        values.put("updated_at", message.updatedAt);
        return values;
    }

    /** Inserts one block row per content block, keeping the list order as the block index. */
    private static void insertBlocks(SQLiteDatabase db, Message message) {
        for (int i = 0; i < message.blocks.size(); i++) {
            ContentBlock block = message.blocks.get(i);
            ContentValues values = new ContentValues();
            values.put("message_id", message.id);
            values.put("idx", i);
            values.put("type", block.type.name());
            values.put("text", block.text);
            values.put("ref_id", block.refId);
            values.put("status", block.status);
            db.insertOrThrow("message_blocks", null, values);
        }
    }

    /** Inserts one row per tool call record, keeping the list order as the call index. */
    private static void insertToolCalls(SQLiteDatabase db, Message message) {
        for (int i = 0; i < message.toolCalls.size(); i++) {
            ToolCallRecord call = message.toolCalls.get(i);
            ContentValues values = new ContentValues();
            values.put("message_id", message.id);
            values.put("idx", i);
            values.put("call_id", call.id);
            values.put("name", call.name);
            values.put("arguments", call.arguments);
            db.insertOrThrow("message_tool_calls", null, values);
        }
    }
}
