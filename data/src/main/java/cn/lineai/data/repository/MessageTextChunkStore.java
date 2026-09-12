package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.Strings;
import java.util.HashMap;

final class MessageTextChunkStore {
    static final int CHUNK_SIZE = 64 * 1024;
    private static final int SQL_SUBSTR_START_OFFSET = 1;

    private final LineCodeDatabase database;

    MessageTextChunkStore(LineCodeDatabase database) {
        this.database = database;
    }

    void save(SQLiteDatabase db, String messageId, String fieldName, String value) {
        String text = safe(value);
        if (text.length() == 0) {
            return;
        }
        // 写入前由调用方按会话一次性清理旧分块（见 clearForConversation），
        // 因此这里不再逐字段执行 DELETE：长对话持久化时每条消息可省下最多 3 次写语句。
        for (int start = 0, order = 0; start < text.length(); start += CHUNK_SIZE, order++) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            ContentValues values = new ContentValues();
            values.put("message_id", safe(messageId));
            values.put("field_name", safe(fieldName));
            values.put("chunk_order", order);
            values.put("content", text.substring(start, end));
            db.insertWithOnConflict("message_text_chunks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    /**
     * 重写一个会话的消息前清理它已有的全部分块文本（含孤立旧行），
     * 用一条语句代替 {@code 每条消息 × 每个字段} 的 DELETE。
     */
    void clearForConversation(SQLiteDatabase db, String conversationId) {
        db.delete("message_text_chunks",
                "message_id IN (SELECT id FROM messages WHERE conversation_id = ?)",
                new String[] {safe(conversationId)});
    }

    /**
     * 一次性读出整个会话的全部分块文本，key 为 {@link #textKey}。
     *
     * <p>长对话（上千条消息）逐条逐字段查询会产生数千次 SQL，是切换/启动会话时主线程卡死的主因；
     * 这里压成一条按 {@code (message_id, field_name, chunk_order)} 索引顺序扫描的查询。
     */
    HashMap<String, String> readAll(SQLiteDatabase db, String conversationId) {
        HashMap<String, String> texts = new HashMap<>();
        Cursor cursor = db.rawQuery(
                "SELECT c.message_id, c.field_name, c.content FROM message_text_chunks c"
                        + " JOIN messages m ON m.id = c.message_id"
                        + " WHERE m.conversation_id = ?"
                        + " ORDER BY c.message_id ASC, c.field_name ASC, c.chunk_order ASC",
                new String[] {safe(conversationId)});
        try {
            StringBuilder builder = null;
            String key = null;
            while (cursor.moveToNext()) {
                String currentKey = textKey(value(cursor, 0), value(cursor, 1));
                if (!currentKey.equals(key)) {
                    if (builder != null) {
                        texts.put(key, builder.toString());
                    }
                    key = currentKey;
                    builder = new StringBuilder();
                }
                builder.append(value(cursor, 2));
            }
            if (builder != null && key != null) {
                texts.put(key, builder.toString());
            }
        } finally {
            cursor.close();
        }
        return texts;
    }

    static String textKey(String messageId, String fieldName) {
        return safe(messageId) + '\u0000' + safe(fieldName);
    }

    String readFirstChars(SQLiteDatabase db, String messageId, String fieldName, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        String chunks = readChunks(db, messageId, fieldName, maxChars);
        if (chunks.length() > 0) {
            return chunks;
        }
        return readLegacyMessageFieldPrefix(db, messageId, fieldName, maxChars);
    }

    long totalLength(SQLiteDatabase db, String tableName, String columnName) {
        return queryLong(db,
                "SELECT COALESCE(SUM(length(" + safeFieldName(columnName) + ")), 0) FROM " + safeTableName(tableName),
                new String[0]);
    }

    private String readChunks(SQLiteDatabase db, String messageId, String fieldName, int maxChars) {
        StringBuilder builder = new StringBuilder();
        Cursor cursor = db.query(
                "message_text_chunks",
                new String[] {"content"},
                "message_id = ? AND field_name = ?",
                new String[] {safe(messageId), safe(fieldName)},
                null,
                null,
                "chunk_order ASC"
        );
        try {
            while (cursor.moveToNext()) {
                String chunk = value(cursor, 0);
                if (maxChars > 0 && builder.length() + chunk.length() > maxChars) {
                    builder.append(chunk, 0, Math.max(0, maxChars - builder.length()));
                    break;
                }
                builder.append(chunk);
                if (maxChars > 0 && builder.length() >= maxChars) {
                    break;
                }
            }
        } finally {
            cursor.close();
        }
        return builder.toString();
    }

    private String readLegacyMessageFieldPrefix(SQLiteDatabase db, String messageId, String fieldName, int maxChars) {
        return readLegacyMessageFieldRange(db, messageId, fieldName, 0, maxChars);
    }

    private String readLegacyMessageFieldRange(SQLiteDatabase db, String messageId, String fieldName, int start, int maxChars) {
        Cursor cursor = db.rawQuery(
                "SELECT substr(" + safeFieldName(fieldName) + ", ?, ?) FROM messages WHERE id = ? LIMIT 1",
                new String[] {String.valueOf(start + SQL_SUBSTR_START_OFFSET), String.valueOf(maxChars), safe(messageId)});
        try {
            return cursor.moveToFirst() ? value(cursor, 0) : "";
        } finally {
            cursor.close();
        }
    }

    private long queryLong(SQLiteDatabase db, String sql, String[] args) {
        Cursor cursor = db.rawQuery(sql, args);
        try {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private String value(Cursor cursor, int index) {
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static String safe(String value) {
        return Strings.nullToEmpty(value);
    }

    private String safeFieldName(String fieldName) {
        if ("content".equals(fieldName)
                || "reasoning_content".equals(fieldName)
                || "raw_json".equals(fieldName)
                || "old_content".equals(fieldName)
                || "new_content".equals(fieldName)
                || "arguments".equals(fieldName)
                || "text".equals(fieldName)) {
            return fieldName;
        }
        throw new IllegalArgumentException("Unsupported text field: " + fieldName);
    }

    private String safeTableName(String tableName) {
        if ("messages".equals(tableName)
                || "message_text_chunks".equals(tableName)
                || "diff_records".equals(tableName)
                || "tool_calls".equals(tableName)
                || "tool_results".equals(tableName)
                || "conversation_index".equals(tableName)) {
            return tableName;
        }
        throw new IllegalArgumentException("Unsupported text table: " + tableName);
    }
}
