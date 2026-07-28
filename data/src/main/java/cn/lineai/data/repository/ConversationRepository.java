package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public final class ConversationRepository extends BaseRepository implements ConversationStore {
    private static final String[] CONVERSATION_COLUMNS = new String[] {
            "id",
            "title",
            "project_id",
            "created_at",
            "updated_at",
            "current",
            "raw_json"
    };
    private static final String[] MESSAGE_META_COLUMNS = new String[] {
            "id",
            "role",
            "timestamp",
            "streaming",
            "hidden",
            "exclude_from_context",
            "tool_call_id",
            "tool_name",
            "is_error"
    };

    private final MessageTextChunkStore textChunks;

    public ConversationRepository(LineCodeDatabase database) {
        super(database);
        textChunks = new MessageTextChunkStore(database);
    }

    public synchronized List<ConversationRecord> getConversationMetas() {
        ArrayList<ConversationRecord> records = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query(
                "conversations",
                CONVERSATION_COLUMNS,
                null,
                null,
                null,
                null,
                "updated_at DESC"
        );
        try {
            while (cursor.moveToNext()) {
                ConversationRecord conversation = readConversation(cursor, new ArrayList<>());
                if (hasVisibleMessages(conversation.getId())) {
                    records.add(conversation);
                }
            }
        } finally {
            cursor.close();
        }
        return records;
    }

    public synchronized List<ConversationRecord> getAllConversations() {
        ArrayList<ConversationRecord> records = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query(
                "conversations",
                CONVERSATION_COLUMNS,
                null,
                null,
                null,
                null,
                "updated_at DESC"
        );
        try {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                records.add(readConversation(cursor, getMessages(id)));
            }
        } finally {
            cursor.close();
        }
        return records;
    }

    public synchronized ConversationRecord getConversation(String id) {
        Cursor cursor = database.getReadableDatabase().query(
                "conversations",
                CONVERSATION_COLUMNS,
                "id = ?",
                new String[] {id},
                null,
                null,
                null,
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readConversation(cursor, getMessages(id));
        } finally {
            cursor.close();
        }
    }

    public synchronized String getCurrentConversationId() {
        Cursor cursor = database.getReadableDatabase().query(
                "conversations",
                new String[] {"id"},
                "current = 1",
                null,
                null,
                null,
                "updated_at DESC",
                "1"
        );
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        } finally {
            cursor.close();
        }
    }

    public synchronized ConversationRecord getCurrentConversation() {
        Cursor cursor = database.getReadableDatabase().query(
                "conversations",
                new String[] {"id"},
                "current = 1",
                null,
                null,
                null,
                "updated_at DESC",
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                ConversationRecord current = getConversation(cursor.getString(0));
                if (current != null && hasVisibleMessages(current.getId())) {
                    return current;
                }
            }
        } finally {
            cursor.close();
        }
        List<ConversationRecord> metas = getConversationMetas();
        if (!metas.isEmpty()) {
            return getConversation(metas.get(0).getId());
        }
        return null;
    }

    public synchronized void saveConversation(ConversationRecord conversation) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            saveConversationOnly(db, conversation);
            db.delete("messages", "conversation_id = ?", new String[] {conversation.getId()});
            List<MessageRecord> messages = conversation.getMessages();
            for (int i = 0; i < messages.size(); i++) {
                saveMessage(db, conversation.getId(), messages.get(i), i);
            }
            if (conversation.isCurrent()) {
                setCurrentInsideTransaction(db, conversation.getId());
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // 事务成功落库后，后台触发一次自动备份，防止后续损坏导致聊天记录丢失。
        database.backupAsync();
    }

    public synchronized void setCurrentConversationId(String id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            setCurrentInsideTransaction(db, id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void deleteConversation(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("conversation_index", "conversation_id = ?", new String[] {id});
            try {
                db.delete("conversation_index_fts", "conversation_id = ?", new String[] {id});
            } catch (RuntimeException ignored) {
            }
            db.delete("conversations", "id = ?", new String[] {id});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("conversation_index", null, null);
            try {
                db.delete("conversation_index_fts", null, null);
            } catch (RuntimeException ignored) {
            }
            db.delete("conversations", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void saveConversationOnly(SQLiteDatabase db, ConversationRecord conversation) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", conversation.getId());
        values.put("title", conversation.getTitle());
        values.put("project_id", conversation.getProjectId());
        values.put("created_at", conversation.getCreatedAt() > 0 ? conversation.getCreatedAt() : now);
        values.put("updated_at", conversation.getUpdatedAt() > 0 ? conversation.getUpdatedAt() : now);
        values.put("current", conversation.isCurrent() ? 1 : 0);
        values.put("raw_json", conversation.getRawJson());
        db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void saveMessage(SQLiteDatabase db, String conversationId, MessageRecord message, int order) {
        long timestamp = message.getTimestamp() > 0 ? message.getTimestamp() : System.currentTimeMillis();
        String messageId = scopedMessageId(conversationId, message.getId(), order);
        ContentValues values = new ContentValues();
        values.put("id", messageId);
        values.put("conversation_id", conversationId);
        values.put("local_order", order);
        values.put("role", message.getRole().getProtocolName());
        values.put("content", "");
        values.put("reasoning_content", "");
        values.put("timestamp", timestamp);
        values.put("streaming", message.isStreaming() ? 1 : 0);
        values.put("hidden", message.isHidden() ? 1 : 0);
        values.put("exclude_from_context", message.isExcludeFromContext() ? 1 : 0);
        values.put("tool_call_id", message.getToolCallId());
        values.put("tool_name", message.getToolName());
        values.put("is_error", message.isError() ? 1 : 0);
        values.put("raw_json", "");
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        textChunks.save(db, messageId, "content", message.getContent());
        textChunks.save(db, messageId, "reasoning_content", message.getReasoningContent());
        textChunks.save(db, messageId, "raw_json", message.getRawJson());
    }

    private void setCurrentInsideTransaction(SQLiteDatabase db, String id) {
        ContentValues clear = new ContentValues();
        clear.put("current", 0);
        db.update("conversations", clear, null, null);
        if (id != null && id.length() > 0) {
            ContentValues selected = new ContentValues();
            selected.put("current", 1);
            db.update("conversations", selected, "id = ?", new String[] {id});
        }
    }

    private List<MessageRecord> getMessages(String conversationId) {
        ArrayList<MessageRecord> messages = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query(
                "messages",
                MESSAGE_META_COLUMNS,
                "conversation_id = ?",
                new String[] {conversationId},
                null,
                null,
                "local_order ASC"
        );
        try {
            while (cursor.moveToNext()) {
                messages.add(readMessage(database.getReadableDatabase(), cursor));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    private boolean hasVisibleMessages(String conversationId) {
        Cursor cursor = database.getReadableDatabase().query(
                "messages",
                new String[] {"id"},
                "conversation_id = ? AND hidden = 0 AND role NOT IN (?, ?)",
                new String[] {conversationId, "system", "tool"},
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private ConversationRecord readConversation(Cursor cursor, List<MessageRecord> messages) {
        return new ConversationRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("current")) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow("raw_json")),
                messages
        );
    }

    private MessageRecord readMessage(SQLiteDatabase db, Cursor cursor) {
        String messageId = cursor.getString(cursor.getColumnIndexOrThrow("id"));
        return new MessageRecord(
                messageId,
                roleFromStorage(cursor.getString(cursor.getColumnIndexOrThrow("role"))),
                textChunks.read(db, messageId, "content"),
                textChunks.read(db, messageId, "reasoning_content"),
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                cursor.getInt(cursor.getColumnIndexOrThrow("streaming")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("hidden")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("exclude_from_context")) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow("tool_call_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("tool_name")),
                cursor.getInt(cursor.getColumnIndexOrThrow("is_error")) == 1,
                textChunks.read(db, messageId, "raw_json")
        );
    }

    private ChatMessage.Role roleFromStorage(String role) {
        if ("system".equals(role)) {
            return ChatMessage.Role.SYSTEM;
        }
        if ("assistant".equals(role)) {
            return ChatMessage.Role.ASSISTANT;
        }
        if ("tool".equals(role)) {
            return ChatMessage.Role.TOOL;
        }
        return ChatMessage.Role.USER;
    }

    static String scopedMessageId(String conversationId, String messageId, int order) {
        String safeConversationId = conversationId == null ? "" : conversationId;
        String id = messageId == null ? "" : messageId;
        if (id.length() == 0) {
            return safeConversationId + ":" + order;
        }
        if (isBareLegacyMessageId(id) && safeConversationId.length() > 0 && !id.startsWith(safeConversationId)) {
            return safeConversationId + ":" + id;
        }
        return id;
    }

    private static boolean isBareLegacyMessageId(String id) {
        if (id == null || id.length() < 2 || id.charAt(0) != 'm') {
            return false;
        }
        for (int i = 1; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
