package cn.lineai.data.migration;

import android.app.Application;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

import cn.lineai.data.sqlite.LineCodeDatabase;
import cn.lineai.data.sqlite.SqliteConversationStore;
import cn.lineai.data.sqlite.SqliteMemoryStore;
import cn.lineai.data.sqlite.SqliteMessageStore;
import cn.lineai.data.sqlite.SqliteModelStore;
import cn.lineai.data.sqlite.SqliteSettingsStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class LegacyMigrationTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase target;

    @After public void close() {
        if (target != null) target.close();
    }

    @Test public void migratesConversationModelMemoryAndSettingsIdempotently() throws Exception {
        File legacy = new File(temporary.getRoot(), "linecode.db");
        createLegacy(legacy);
        target = new LineCodeDatabase(new File(temporary.getRoot(), "linecode-v2.db"));

        LegacyMigration.migrate(legacy, target);

        SqliteConversationStore conversations = new SqliteConversationStore(target, () -> 1L);
        assertEquals(1, conversations.list().size());
        assertEquals("Old chat", conversations.list().get(0).title);

        SqliteMessageStore messages = new SqliteMessageStore(target);
        assertEquals(2, messages.load("conv-1").size());
        assertTrue(messages.load("conv-1").get(0).plainText().contains("hello from legacy"));

        SqliteModelStore models = new SqliteModelStore(target);
        assertEquals(1, models.list().size());
        assertEquals("legacy-model", models.list().get(0).modelId);
        assertTrue(models.list().get(0).isDefault);

        SqliteMemoryStore memories = new SqliteMemoryStore(target, () -> 1L);
        assertEquals(1, memories.list("").size());
        assertEquals("remember this", memories.list("").get(0).content);

        SqliteSettingsStore settings = new SqliteSettingsStore(target, () -> 1L);
        assertEquals("true", settings.get("ai.learningMode", ""));
        assertEquals("custom tone", settings.get("prompt.toneCoding", ""));

        assertTrue(new File(temporary.getRoot(), "linecode.db.v4.bak").isFile());
        LegacyMigration.migrate(legacy, target);
        assertEquals(1, conversations.list().size());
        assertFalse(messages.load("conv-1").isEmpty());
    }

    private void createLegacy(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE conversations (id TEXT PRIMARY KEY, title TEXT NOT NULL,"
                + " project_id TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,"
                + " current INTEGER NOT NULL DEFAULT 0, raw_json TEXT)");
        db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL,"
                + " local_order INTEGER NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL"
                + " DEFAULT '', reasoning_content TEXT, timestamp INTEGER NOT NULL,"
                + " streaming INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0,"
                + " exclude_from_context INTEGER NOT NULL DEFAULT 0, tool_call_id TEXT,"
                + " tool_name TEXT, is_error INTEGER NOT NULL DEFAULT 0, raw_json TEXT)");
        db.execSQL("CREATE TABLE message_text_chunks (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " message_id TEXT NOT NULL, field_name TEXT NOT NULL, chunk_order INTEGER"
                + " NOT NULL, content TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE message_blocks (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " message_id TEXT NOT NULL, block_order INTEGER NOT NULL, type TEXT NOT NULL,"
                + " content TEXT, status TEXT, raw_json TEXT)");
        db.execSQL("CREATE TABLE model_configs (id TEXT PRIMARY KEY, name TEXT NOT NULL,"
                + " protocol_type TEXT NOT NULL, provider_label TEXT NOT NULL, base_url TEXT,"
                + " api_key TEXT, model_id TEXT NOT NULL, tool_call_limit INTEGER NOT NULL"
                + " DEFAULT 200, compression_model_enabled INTEGER NOT NULL DEFAULT 0,"
                + " compression_model_auto INTEGER NOT NULL DEFAULT 1,"
                + " compression_model_id TEXT, context_size INTEGER NOT NULL DEFAULT 0,"
                + " local_model_path TEXT NOT NULL DEFAULT '', local_acceleration TEXT NOT NULL"
                + " DEFAULT 'AUTOMATIC', selected INTEGER NOT NULL DEFAULT 0, raw_json TEXT,"
                + " created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE memories (id TEXT PRIMARY KEY, scope TEXT NOT NULL,"
                + " project_id TEXT, content TEXT NOT NULL, source TEXT NOT NULL,"
                + " confidence REAL NOT NULL DEFAULT 1, created_at INTEGER NOT NULL,"
                + " updated_at INTEGER NOT NULL, last_used_at INTEGER,"
                + " use_count INTEGER NOT NULL DEFAULT 0, raw_json TEXT)");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL,"
                + " type TEXT NOT NULL DEFAULT 'string', updated_at INTEGER NOT NULL)");

        ContentValues conversation = new ContentValues();
        conversation.put("id", "conv-1");
        conversation.put("title", "Old chat");
        conversation.put("created_at", 10);
        conversation.put("updated_at", 20);
        db.insert("conversations", null, conversation);

        ContentValues user = new ContentValues();
        user.put("id", "msg-1");
        user.put("conversation_id", "conv-1");
        user.put("local_order", 1);
        user.put("role", "user");
        user.put("content", "");
        user.put("timestamp", 11);
        db.insert("messages", null, user);
        ContentValues chunk = new ContentValues();
        chunk.put("message_id", "msg-1");
        chunk.put("field_name", "content");
        chunk.put("chunk_order", 0);
        chunk.put("content", "hello from legacy");
        db.insert("message_text_chunks", null, chunk);

        ContentValues assistant = new ContentValues();
        assistant.put("id", "msg-2");
        assistant.put("conversation_id", "conv-1");
        assistant.put("local_order", 2);
        assistant.put("role", "assistant");
        assistant.put("content", "reply");
        assistant.put("timestamp", 12);
        db.insert("messages", null, assistant);

        ContentValues model = new ContentValues();
        model.put("id", "model-1");
        model.put("name", "Legacy model");
        model.put("protocol_type", "OPENAI_COMPATIBLE");
        model.put("provider_label", "Custom");
        model.put("base_url", "https://example.com/v1");
        model.put("api_key", "key");
        model.put("model_id", "legacy-model");
        model.put("selected", 1);
        model.put("created_at", 1);
        model.put("updated_at", 2);
        db.insert("model_configs", null, model);

        ContentValues memory = new ContentValues();
        memory.put("id", "mem-1");
        memory.put("scope", "user");
        memory.put("content", "remember this");
        memory.put("source", "manual");
        memory.put("created_at", 1);
        memory.put("updated_at", 2);
        db.insert("memories", null, memory);

        setting(db, "@linecode_learning_mode_enabled", "1");
        setting(db, "@linecode_prompt_template_toneCoding", "custom tone");
        db.close();
    }

    private static void setting(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("updated_at", 1);
        db.insert("settings", null, values);
    }
}
