package cn.lineai.data.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;

/**
 * Opens the on-disk database and owns schema creation/migration.
 *
 * <p>The database takes a {@link File}, not a {@code Context}: the data layer must be usable from
 * tests and must not reach for shared preferences or app-wide singletons.</p>
 */
public final class LineCodeDatabase {

    public static final int VERSION = 13;

    private final File file;
    private SQLiteDatabase database;

    public LineCodeDatabase(File file) {
        this.file = file;
    }

    public synchronized SQLiteDatabase open() {
        if (database != null && database.isOpen()) {
            return database;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            database = SQLiteDatabase.openOrCreateDatabase(file, null);
            database.execSQL("PRAGMA foreign_keys=ON");
            int version = readVersion(database);
            if (version == 0) {
                createSchema(database);
                writeVersion(database);
            } else if (version < VERSION) {
                migrate(database, version);
                writeVersion(database);
            }
            return database;
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "open " + file.getName(), e);
        }
    }

    public synchronized void close() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    private static int readVersion(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("PRAGMA user_version", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static void writeVersion(SQLiteDatabase db) {
        db.execSQL("PRAGMA user_version=" + VERSION);
    }

    private void migrate(SQLiteDatabase db, int fromVersion) {
        // v1 is the legacy schema; v2 is a fresh, normalized layout. The legacy tables are copied
        // by MigrationV1ToV2 when present, otherwise the schema is created from scratch.
        // Every statement is CREATE ... IF NOT EXISTS, so replaying the schema adds new tables
        // without touching existing rows.
        List<String> statements = new ArrayList<>(schemaStatements());
        // Idempotent: replayed on every upgrade so newly added model columns land on old rows.
        statements.addAll(modelColumnStatements());
        db.beginTransaction();
        try {
            for (String statement : statements) {
                try {
                    db.execSQL(statement);
                } catch (SQLiteException duplicate) {
                    // ALTER TABLE ADD COLUMN has no IF NOT EXISTS; a rerun after a partial
                    // migration must stay idempotent.
                    if (!statement.contains(" ADD COLUMN ")) throw duplicate;
                }
            }
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "migrate v" + fromVersion, e);
        } finally {
            db.endTransaction();
        }
    }

    private void createSchema(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (String statement : schemaStatements()) {
                db.execSQL(statement);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static List<String> schemaStatements() {
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE IF NOT EXISTS conversations ("
                + "id TEXT PRIMARY KEY,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "project_id TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "message_count INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_conversations_updated "
                + "ON conversations(updated_at DESC)");
        statements.add("CREATE TABLE IF NOT EXISTS messages ("
                + "id TEXT PRIMARY KEY,"
                + "conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,"
                + "seq INTEGER NOT NULL,"
                + "role TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "visibility TEXT NOT NULL DEFAULT 'VISIBLE',"
                + "model_id TEXT NOT NULL DEFAULT '',"
                + "provider_id TEXT NOT NULL DEFAULT '',"
                + "tool_call_id TEXT NOT NULL DEFAULT '',"
                + "tool_name TEXT NOT NULL DEFAULT '',"
                + "error_code TEXT NOT NULL DEFAULT '',"
                + "finish_reason TEXT NOT NULL DEFAULT '',"
                + "review_state TEXT NOT NULL DEFAULT '',"
                + "input_tokens INTEGER NOT NULL DEFAULT 0,"
                + "output_tokens INTEGER NOT NULL DEFAULT 0,"
                + "cached_tokens INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(conversation_id, seq))");
        statements.add("CREATE INDEX IF NOT EXISTS idx_messages_conversation "
                + "ON messages(conversation_id, seq)");
        statements.add("CREATE TABLE IF NOT EXISTS message_blocks ("
                + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                + "idx INTEGER NOT NULL,"
                + "type TEXT NOT NULL,"
                + "text TEXT NOT NULL DEFAULT '',"
                + "ref_id TEXT NOT NULL DEFAULT '',"
                + "status TEXT NOT NULL DEFAULT '',"
                + "PRIMARY KEY(message_id, idx))");
        statements.add("CREATE TABLE IF NOT EXISTS message_tool_calls ("
                + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                + "idx INTEGER NOT NULL,"
                + "call_id TEXT NOT NULL DEFAULT '',"
                + "name TEXT NOT NULL DEFAULT '',"
                + "arguments TEXT NOT NULL DEFAULT '',"
                + "PRIMARY KEY(message_id, idx))");
        statements.add("CREATE TABLE IF NOT EXISTS settings ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE TABLE IF NOT EXISTS models ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "provider_id TEXT NOT NULL DEFAULT '',"
                + "model_id TEXT NOT NULL DEFAULT '',"
                + "base_url TEXT NOT NULL DEFAULT '',"
                + "api_key TEXT NOT NULL DEFAULT '',"
                + "protocol TEXT NOT NULL DEFAULT 'OPENAI_COMPATIBLE',"
                + "context_size INTEGER NOT NULL DEFAULT 0,"
                + "temperature REAL NOT NULL DEFAULT 0.7,"
                + "max_tokens INTEGER NOT NULL DEFAULT 4096,"
                + "reasoning_effort TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "is_default INTEGER NOT NULL DEFAULT 0,"
                + "supports_vision INTEGER NOT NULL DEFAULT 0,"
                + "supports_reasoning INTEGER NOT NULL DEFAULT 1,"
                + "supports_video INTEGER NOT NULL DEFAULT 0,"
                + "supports_tools INTEGER NOT NULL DEFAULT 1,"
                + "provider_label TEXT NOT NULL DEFAULT '',"
                + "tool_call_limit INTEGER NOT NULL DEFAULT 200,"
                + "compression_enabled INTEGER NOT NULL DEFAULT 0,"
                + "compression_auto INTEGER NOT NULL DEFAULT 1,"
                + "compression_model_id TEXT NOT NULL DEFAULT '',"
                + "local_model_path TEXT NOT NULL DEFAULT '',"
                + "local_acceleration TEXT NOT NULL DEFAULT 'automatic')");
        statements.add("CREATE INDEX IF NOT EXISTS idx_models_default ON models(is_default)");
        statements.add("CREATE TABLE IF NOT EXISTS error_logs ("
                + "id TEXT PRIMARY KEY,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "subtitle TEXT NOT NULL DEFAULT '',"
                + "detail TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_error_logs_created ON error_logs(created_at DESC)");
        statements.add("CREATE TABLE IF NOT EXISTS memories ("
                + "id TEXT PRIMARY KEY,"
                + "scope TEXT NOT NULL DEFAULT 'user',"
                + "project_id TEXT NOT NULL DEFAULT '',"
                + "title TEXT NOT NULL DEFAULT '',"
                + "content TEXT NOT NULL DEFAULT '',"
                + "source TEXT NOT NULL DEFAULT '',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "last_used_at INTEGER NOT NULL DEFAULT 0,"
                + "use_count INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_memories_scope ON memories(scope, project_id)");
        statements.add("CREATE TABLE IF NOT EXISTS extension_agents ("
                + "id TEXT PRIMARY KEY,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "slug TEXT NOT NULL DEFAULT '',"
                + "prompt TEXT NOT NULL DEFAULT '',"
                + "trigger TEXT NOT NULL DEFAULT '',"
                + "tool_names TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_extension_agents_updated "
                + "ON extension_agents(updated_at DESC)");
        statements.add("CREATE TABLE IF NOT EXISTS extension_mcps ("
                + "id TEXT PRIMARY KEY,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "url TEXT NOT NULL DEFAULT '',"
                + "headers TEXT NOT NULL DEFAULT '',"
                + "tools TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_extension_mcps_updated "
                + "ON extension_mcps(updated_at DESC)");
        statements.add("CREATE TABLE IF NOT EXISTS file_diffs ("
                + "id TEXT PRIMARY KEY,"
                + "root_path TEXT NOT NULL DEFAULT '',"
                + "path TEXT NOT NULL DEFAULT '',"
                + "before_text TEXT NOT NULL DEFAULT '',"
                + "after_text TEXT NOT NULL DEFAULT '',"
                + "existed_before INTEGER NOT NULL DEFAULT 1,"
                + "reverted INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_file_diffs_created "
                + "ON file_diffs(created_at DESC)");
        statements.add("CREATE TABLE IF NOT EXISTS projects ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "path TEXT NOT NULL DEFAULT '',"
                + "selected INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        statements.add("CREATE TABLE IF NOT EXISTS ipc_providers ("
                + "id TEXT PRIMARY KEY,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "package_name TEXT NOT NULL DEFAULT '',"
                + "service_class TEXT NOT NULL DEFAULT '',"
                + "provider_type TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        return statements;
    }

    /** Adds the columns introduced after schema v3. */
    private static List<String> modelColumnStatements() {
        List<String> statements = new ArrayList<>();
        statements.add("ALTER TABLE models ADD COLUMN provider_label TEXT NOT NULL DEFAULT ''");
        statements.add("ALTER TABLE models ADD COLUMN tool_call_limit INTEGER NOT NULL DEFAULT 200");
        statements.add("ALTER TABLE models ADD COLUMN compression_enabled INTEGER NOT NULL DEFAULT 0");
        statements.add("ALTER TABLE models ADD COLUMN compression_auto INTEGER NOT NULL DEFAULT 1");
        statements.add("ALTER TABLE models ADD COLUMN compression_model_id TEXT NOT NULL DEFAULT ''");
        statements.add("ALTER TABLE models ADD COLUMN local_model_path TEXT NOT NULL DEFAULT ''");
        statements.add("ALTER TABLE models ADD COLUMN local_acceleration TEXT NOT NULL DEFAULT 'automatic'");
        statements.add("ALTER TABLE models ADD COLUMN supports_reasoning INTEGER NOT NULL DEFAULT 1");
        statements.add("ALTER TABLE models ADD COLUMN supports_video INTEGER NOT NULL DEFAULT 0");
        return statements;
    }
}
