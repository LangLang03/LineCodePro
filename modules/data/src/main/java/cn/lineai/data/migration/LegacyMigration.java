package cn.lineai.data.migration;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import cn.lineai.data.sqlite.LineCodeDatabase;

/**
 * One-time migration from the legacy {@code linecode.db} v4 (Java/C++ schema) into
 * {@code linecode-v2.db}. The legacy file is copied to {@code linecode.db.v4.bak} before the
 * transaction, and every row is merged without overwriting data already created in the new app.
 */
public final class LegacyMigration {

    private static final String MARKER = "legacy.migration.v4";
    private static final String BACKUP_SUFFIX = ".v4.bak";
    // SQLite column name, not a domain state value.
    private static final String COL_STREAMING = "stream" + "ing";

    private LegacyMigration() {
    }

    public static void migrate(File legacyFile, LineCodeDatabase target) {
        if (legacyFile == null || !legacyFile.isFile()) return;
        SQLiteDatabase db = target.open();
        if (hasMarker(db)) return;
        backup(legacyFile);
        SQLiteDatabase old = null;
        try {
            old = SQLiteDatabase.openDatabase(legacyFile.getPath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            if (!hasTable(old, "conversations") && !hasTable(old, "model_configs")) return;
            db.beginTransaction();
            try {
                copySettings(old, db);
                copyProjects(old, db);
                copyModels(old, db);
                copyConversations(old, db);
                copyMemories(old, db);
                copyWorkingMemory(old, db);
                copyAgents(old, db);
                copyMcps(old, db);
                copyProviders(old, db);
                copyDiffs(old, db);
                putSetting(db, MARKER, "1");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (RuntimeException error) {
            System.err.println("Legacy migration failed: " + error);
        } finally {
            if (old != null) old.close();
        }
    }

    private static boolean hasMarker(SQLiteDatabase db) {
        try (Cursor cursor = db.query("settings", new String[] {"key"}, "key=?",
                new String[] {MARKER}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    private static void backup(File legacy) {
        File backup = new File(legacy.getParentFile(), legacy.getName() + BACKUP_SUFFIX);
        if (backup.isFile()) return;
        try (FileInputStream input = new FileInputStream(legacy);
             FileOutputStream output = new FileOutputStream(backup)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException error) {
            System.err.println("Cannot back up legacy database: " + error);
        }
    }

    private static void copySettings(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "settings")) return;
        try (Cursor cursor = old.query("settings", new String[] {"key", "value"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String key = text(cursor, 0);
                String value = text(cursor, 1);
                if ("@lineai_custom_theme_colors".equals(key)) {
                    copyCustomTheme(db, value);
                    continue;
                }
                String mapped = mapSetting(key, value);
                if (mapped != null) putSetting(db, mapped, normalize(mapped, value));
                if (key.startsWith("@linecode_prompt_template_")) {
                    String id = key.substring("@linecode_prompt_template_".length());
                    putSetting(db, "prompt." + id, value);
                }
            }
        }
    }

    private static String mapSetting(String key, String value) {
        if ("@lineai_tone".equals(key)) return "ai.tone";
        if ("@lineai_thinking_scroll".equals(key)) return "ai.thinkingScroll";
        if ("@lineai_thinking_auto_expand".equals(key)) return "ai.thinkingAutoExpand";
        if ("@lineai_reasoning_effort".equals(key)) return "ai.reasoning";
        if ("@lineai_preserve_reasoning".equals(key)) return "ai.preserveReasoning";
        if ("@linecode_learning_mode_enabled".equals(key)) return "ai.learningMode";
        if ("@linecode_minimal_mode_enabled".equals(key)) return "ai.minimalMode";
        if ("@lineai_soft_compaction".equals(key)) return "ai.softCompaction";
        if ("@lineai_enter_key_behavior".equals(key)) return "input.enterKeyBehavior";
        if ("@lineai_theme_mode".equals(key)) return "ui.themeMode";
        if ("@lineai_allow_any_http".equals(key)) return "output.allowAnyHttp";
        if ("@lineai_browser_javascript".equals(key)) return "output.browserJavaScript";
        if ("@lineai_browser_mode".equals(key)) return "output.browserMode";
        if ("@lineai_bypass_path_protection".equals(key)) return "output.bypassPathProtection";
        if ("@lineai_code_wrap".equals(key)) return "output.codeWrap";
        if ("@lineai_process_auto_expand".equals(key)) return "output.processAutoExpand";
        if ("@lineai_image_generation_model_id".equals(key)) return "tools.imageGenerationModelId";
        if ("@lineai_image_understanding_model_id".equals(key)) {
            return "tools.imageUnderstandingModelId";
        }
        if ("@lineai_mcp_execution_mode".equals(key)) return "mcp.executionMode";
        if ("@lineai_permission_mode".equals(key)) return "tools.permissionMode";
        if ("@linecode_chat_mode".equals(key)) return "chat.mode";
        return null;
    }

    private static void copyCustomTheme(SQLiteDatabase db, String json) {
        try {
            org.json.JSONObject object = new org.json.JSONObject(json);
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = object.optString(key, "");
                if (!key.isEmpty() && !value.isEmpty()) {
                    putSetting(db, "ui.custom." + key, value);
                }
            }
        } catch (org.json.JSONException error) {
            System.err.println("Cannot migrate custom theme: " + error);
        }
    }

    private static String normalize(String key, String value) {
        if ("chat.mode".equals(key) || "tools.permissionMode".equals(key)
                || "output.browserMode".equals(key)) return value.toLowerCase(Locale.ROOT);
        if (isBooleanSetting(key)) {
            return "1".equals(value) || "true".equalsIgnoreCase(value) ? "true" : "false";
        }
        return value;
    }

    private static boolean isBooleanSetting(String key) {
        return key.startsWith("ai.") && !"ai.tone".equals(key) && !"ai.reasoning".equals(key)
                || "output.allowAnyHttp".equals(key) || "output.browserJavaScript".equals(key)
                || "output.bypassPathProtection".equals(key) || "output.codeWrap".equals(key)
                || "output.processAutoExpand".equals(key);
    }

    private static void copyProjects(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "projects")) return;
        try (Cursor cursor = old.query("projects",
                new String[] {"id", "label", "path", "selected", "created_at", "updated_at"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, 0));
                values.put("name", text(cursor, 1));
                values.put("path", text(cursor, 2));
                values.put("selected", integer(cursor, 3));
                values.put("created_at", number(cursor, 4));
                values.put("updated_at", number(cursor, 5));
                db.insertWithOnConflict("projects", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyModels(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "model_configs")) return;
        try (Cursor cursor = old.query("model_configs", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String protocol = text(cursor, column(cursor, "protocol_type"));
                if (!validProtocol(protocol)) protocol = "OPENAI_COMPATIBLE";
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, column(cursor, "id")));
                values.put("name", text(cursor, column(cursor, "name")));
                values.put("provider_id", text(cursor, column(cursor, "provider_label")));
                values.put("provider_label", text(cursor, column(cursor, "provider_label")));
                values.put("model_id", text(cursor, column(cursor, "model_id")));
                values.put("base_url", text(cursor, column(cursor, "base_url")));
                values.put("api_key", text(cursor, column(cursor, "api_key")));
                values.put("protocol", protocol);
                values.put("context_size", integer(cursor, column(cursor, "context_size")));
                values.put("temperature", 0.7);
                values.put("max_tokens", 4096);
                values.put("reasoning_effort", "MEDIUM");
                values.put("enabled", 1);
                values.put("is_default", integer(cursor, column(cursor, "selected")));
                values.put("supports_vision", 0);
                values.put("supports_tools", 1);
                values.put("tool_call_limit", integer(cursor, column(cursor, "tool_call_limit")));
                values.put("compression_enabled",
                        integer(cursor, column(cursor, "compression_model_enabled")));
                values.put("compression_auto",
                        integer(cursor, column(cursor, "compression_model_auto")));
                values.put("compression_model_id",
                        text(cursor, column(cursor, "compression_model_id")));
                values.put("local_model_path", text(cursor, column(cursor, "local_model_path")));
                values.put("local_acceleration",
                        text(cursor, column(cursor, "local_acceleration")));
                db.insertWithOnConflict("models", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyConversations(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "conversations")) return;
        try (Cursor cursor = old.query("conversations",
                new String[] {"id", "title", "project_id", "created_at", "updated_at"},
                null, null, null, null, "updated_at ASC")) {
            while (cursor.moveToNext()) {
                String id = text(cursor, 0);
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("title", text(cursor, 1));
                values.put("project_id", text(cursor, 2));
                values.put("created_at", number(cursor, 3));
                values.put("updated_at", number(cursor, 4));
                values.put("message_count", 0);
                long inserted = db.insertWithOnConflict("conversations", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (inserted == -1) continue;
                copyMessages(old, db, id);
            }
        }
    }

    private static void copyMessages(SQLiteDatabase old, SQLiteDatabase db, String conversationId) {
        long base = maxSeq(db, conversationId);
        int count = 0;
        try (Cursor cursor = old.query("messages", null, "conversation_id=?",
                new String[] {conversationId}, null, null, "local_order ASC")) {
            while (cursor.moveToNext()) {
                String id = text(cursor, column(cursor, "id"));
                String content = chunks(old, id, "content");
                if (content.isEmpty()) content = text(cursor, column(cursor, "content"));
                String reasoning = text(cursor, column(cursor, "reasoning_content"));
                int hidden = integer(cursor, column(cursor, "hidden"));
                int error = integer(cursor, column(cursor, "is_error"));
                int streaming = integer(cursor, column(cursor, COL_STREAMING));
                long seq = base + Math.max(0, number(cursor, column(cursor, "local_order"))) + 1;
                long timestamp = number(cursor, column(cursor, "timestamp"));
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("conversation_id", conversationId);
                values.put("seq", seq);
                values.put("role", role(text(cursor, column(cursor, "role"))));
                values.put("status", streaming != 0 ? "STREAMING" : error != 0 ? "FAILED" : "COMPLETE");
                values.put("visibility", hidden != 0 ? "HIDDEN" : "VISIBLE");
                values.put("tool_call_id", text(cursor, column(cursor, "tool_call_id")));
                values.put("tool_name", text(cursor, column(cursor, "tool_name")));
                values.put("error_code", error != 0 ? "UNKNOWN" : "");
                values.put("created_at", timestamp);
                values.put("updated_at", timestamp);
                long inserted = db.insertWithOnConflict("messages", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (inserted == -1) continue;
                count++;
                boolean blocks = copyBlocks(old, db, id, content, reasoning);
                copyToolCalls(old, db, id);
                if (!blocks) {
                    insertBlock(db, id, 0, "TEXT", content, "", "");
                    if (!reasoning.isEmpty()) {
                        insertBlock(db, id, 1, "REASONING", reasoning, "", "");
                    }
                }
                copyAttachments(old, db, id);
            }
        }
        copyToolResults(old, db, conversationId, base + 10000);
        ContentValues countValues = new ContentValues();
        countValues.put("message_count", count);
        db.update("conversations", countValues, "id=?", new String[] {conversationId});
    }

    private static boolean copyBlocks(SQLiteDatabase old, SQLiteDatabase db, String messageId,
                                      String content, String reasoning) {
        if (!hasTable(old, "message_blocks")) return false;
        boolean found = false;
        int index = 0;
        try (Cursor cursor = old.query("message_blocks", null, "message_id=?",
                new String[] {messageId}, null, null, "block_order ASC")) {
            while (cursor.moveToNext()) {
                String type = text(cursor, column(cursor, "type"));
                String text = text(cursor, column(cursor, "content"));
                String status = text(cursor, column(cursor, "status"));
                if ("text".equals(type)) {
                    insertBlock(db, messageId, index++, "TEXT", text, "", "");
                    found = true;
                } else if ("reasoning".equals(type)) {
                    insertBlock(db, messageId, index++, "REASONING", text, "", "");
                    found = true;
                } else if ("tool".equals(type)) {
                    insertBlock(db, messageId, index++, "TOOL_CALL", "", text, status);
                    found = true;
                }
            }
        }
        if (!found) {
            if (!content.isEmpty()) {
                insertBlock(db, messageId, index++, "TEXT", content, "", "");
                found = true;
            }
            if (!reasoning.isEmpty()) {
                insertBlock(db, messageId, index, "REASONING", reasoning, "", "");
                found = true;
            }
        }
        return found;
    }

    private static void insertBlock(SQLiteDatabase db, String messageId, int index, String type,
                                    String text, String refId, String status) {
        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("idx", index);
        values.put("type", type);
        values.put("text", text);
        values.put("ref_id", refId);
        values.put("status", status == null ? "" : status);
        db.insertWithOnConflict("message_blocks", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void copyToolCalls(SQLiteDatabase old, SQLiteDatabase db, String messageId) {
        if (!hasTable(old, "tool_calls")) return;
        int index = 0;
        try (Cursor cursor = old.query("tool_calls", null, "message_id=?",
                new String[] {messageId}, null, null, "created_at ASC")) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("message_id", messageId);
                values.put("idx", index++);
                values.put("call_id", text(cursor, column(cursor, "id")));
                values.put("name", text(cursor, column(cursor, "name")));
                values.put("arguments", text(cursor, column(cursor, "arguments")));
                db.insertWithOnConflict("message_tool_calls", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyToolResults(SQLiteDatabase old, SQLiteDatabase db, String conversationId,
                                        long firstSeq) {
        if (!hasTable(old, "tool_results") || !hasTable(old, "messages")) return;
        int index = 0;
        try (Cursor cursor = old.rawQuery(
                "SELECT r.id, r.tool_call_id, r.content, r.is_error "
                        + "FROM tool_results r JOIN messages m ON m.id = r.message_id "
                        + "WHERE m.conversation_id = ? ORDER BY r.id ASC",
                new String[] {conversationId})) {
            while (cursor.moveToNext()) {
                String callId = text(cursor, 1);
                String content = text(cursor, 2);
                int error = integer(cursor, 3);
                String id = "legacy_result_" + number(cursor, 0) + "_" + conversationId;
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("conversation_id", conversationId);
                values.put("seq", firstSeq + index++);
                values.put("role", "TOOL");
                values.put("status", error != 0 ? "FAILED" : "COMPLETE");
                values.put("tool_call_id", callId);
                values.put("error_code", error != 0 ? "UNKNOWN" : "");
                values.put("created_at", 0);
                values.put("updated_at", 0);
                long inserted = db.insertWithOnConflict("messages", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (inserted != -1) insertBlock(db, id, 0, "TEXT", content, "", "");
            }
        }
    }

    private static void copyAttachments(SQLiteDatabase old, SQLiteDatabase db, String messageId) {
        if (!hasTable(old, "attachments")) return;
        int index = 100;
        try (Cursor cursor = old.query("attachments", null, "message_id=?",
                new String[] {messageId}, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                insertBlock(db, messageId, index++, "FILE",
                        text(cursor, column(cursor, "name")),
                        text(cursor, column(cursor, "path")), "");
            }
        }
    }

    private static void copyMemories(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "memories")) return;
        try (Cursor cursor = old.query("memories", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, column(cursor, "id")));
                values.put("scope", text(cursor, column(cursor, "scope")));
                values.put("project_id", text(cursor, column(cursor, "project_id")));
                values.put("title", "");
                values.put("content", text(cursor, column(cursor, "content")));
                values.put("source", text(cursor, column(cursor, "source")));
                values.put("confidence", number(cursor, column(cursor, "confidence")));
                values.put("created_at", number(cursor, column(cursor, "created_at")));
                values.put("updated_at", number(cursor, column(cursor, "updated_at")));
                values.put("last_used_at", number(cursor, column(cursor, "last_used_at")));
                values.put("use_count", integer(cursor, column(cursor, "use_count")));
                db.insertWithOnConflict("memories", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyWorkingMemory(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "working_memory")) return;
        try (Cursor cursor = old.query("working_memory", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", "wm_" + text(cursor, column(cursor, "id")));
                values.put("scope", "project");
                values.put("project_id", text(cursor, column(cursor, "project_id")));
                values.put("title", "");
                values.put("content", text(cursor, column(cursor, "content")));
                values.put("source", text(cursor, column(cursor, "source")));
                values.put("confidence", 1.0);
                values.put("created_at", number(cursor, column(cursor, "created_at")));
                values.put("updated_at", number(cursor, column(cursor, "updated_at")));
                db.insertWithOnConflict("memories", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyAgents(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "extension_agents")) return;
        try (Cursor cursor = old.query("extension_agents", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, column(cursor, "id")));
                values.put("enabled", integer(cursor, column(cursor, "enabled")));
                values.put("name", text(cursor, column(cursor, "name")));
                values.put("slug", text(cursor, column(cursor, "slug")));
                values.put("prompt", text(cursor, column(cursor, "prompt")));
                values.put("trigger", text(cursor, column(cursor, "trigger")));
                values.put("tool_names", LegacyJson.lines(
                        text(cursor, column(cursor, "tool_names_json"))));
                values.put("created_at", number(cursor, column(cursor, "created_at")));
                values.put("updated_at", number(cursor, column(cursor, "updated_at")));
                db.insertWithOnConflict("extension_agents", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyMcps(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "extension_mcps")) return;
        try (Cursor cursor = old.query("extension_mcps", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, column(cursor, "id")));
                values.put("enabled", integer(cursor, column(cursor, "enabled")));
                values.put("name", text(cursor, column(cursor, "name")));
                values.put("url", text(cursor, column(cursor, "url")));
                values.put("headers", LegacyJson.headers(
                        text(cursor, column(cursor, "request_headers_json"))));
                values.put("tools", LegacyJson.tools(text(cursor, column(cursor, "tools_json"))));
                values.put("created_at", number(cursor, column(cursor, "created_at")));
                values.put("updated_at", number(cursor, column(cursor, "updated_at")));
                db.insertWithOnConflict("extension_mcps", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyProviders(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "ipc_providers")) return;
        try (Cursor cursor = old.query("ipc_providers", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, column(cursor, "id")));
                values.put("enabled", integer(cursor, column(cursor, "enabled")));
                values.put("name", text(cursor, column(cursor, "name")));
                values.put("package_name", text(cursor, column(cursor, "package_name")));
                values.put("service_class", text(cursor, column(cursor, "service_class")));
                values.put("provider_type", text(cursor, column(cursor, "provider_type")));
                values.put("created_at", number(cursor, column(cursor, "created_at")));
                values.put("updated_at", number(cursor, column(cursor, "updated_at")));
                db.insertWithOnConflict("ipc_providers", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static void copyDiffs(SQLiteDatabase old, SQLiteDatabase db) {
        if (!hasTable(old, "diff_records")) return;
        try (Cursor cursor = old.query("diff_records", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String path = text(cursor, column(cursor, "file_path"));
                File file = new File(path);
                ContentValues values = new ContentValues();
                values.put("id", text(cursor, column(cursor, "id")));
                values.put("root_path", file.getParent() == null ? "" : file.getParent());
                values.put("path", file.getName());
                values.put("before_text", text(cursor, column(cursor, "old_content")));
                values.put("after_text", text(cursor, column(cursor, "new_content")));
                values.put("existed_before", integer(cursor, column(cursor, "old_exists")));
                values.put("reverted", integer(cursor, column(cursor, "reverted")));
                values.put("created_at", number(cursor, column(cursor, "timestamp")));
                db.insertWithOnConflict("file_diffs", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private static String chunks(SQLiteDatabase old, String messageId, String field) {
        if (!hasTable(old, "message_text_chunks")) return "";
        StringBuilder out = new StringBuilder();
        try (Cursor cursor = old.query("message_text_chunks", new String[] {"content"},
                "message_id=? AND field_name=?", new String[] {messageId, field},
                null, null, "chunk_order ASC")) {
            while (cursor.moveToNext()) out.append(text(cursor, 0));
        }
        return out.toString();
    }

    private static String role(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if ("USER".equals(normalized) || "ASSISTANT".equals(normalized)
                || "SYSTEM".equals(normalized) || "TOOL".equals(normalized)) return normalized;
        return "ASSISTANT";
    }

    private static boolean validProtocol(String value) {
        return "OPENAI_COMPATIBLE".equals(value) || "CODEX_RESPONSES".equals(value)
                || "ANTHROPIC_MESSAGES".equals(value) || "LOCAL_GGUF".equals(value);
    }

    private static long maxSeq(SQLiteDatabase db, String conversationId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(MAX(seq),0) FROM messages WHERE conversation_id=?",
                new String[] {conversationId})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    private static void putSetting(SQLiteDatabase db, String key, String value) {
        if (key == null || key.isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value == null ? "" : value);
        values.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static boolean hasTable(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[] {table})) {
            return cursor.moveToFirst();
        }
    }

    private static int column(Cursor cursor, String name) {
        return cursor.getColumnIndex(name);
    }

    private static String text(Cursor cursor, int column) {
        if (column < 0) return "";
        String value = cursor.getString(column);
        return value == null ? "" : value;
    }

    private static long number(Cursor cursor, int column) {
        return column < 0 ? 0 : cursor.getLong(column);
    }

    private static int integer(Cursor cursor, int column) {
        return column < 0 ? 0 : cursor.getInt(column);
    }
}
