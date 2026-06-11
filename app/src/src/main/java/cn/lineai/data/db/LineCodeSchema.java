package cn.lineai.data.db;

public final class LineCodeSchema {
    public static final String DATABASE_NAME = "linecode.db";
    public static final int VERSION = 1;

    public static final String TABLE_METADATA = "metadata";
    public static final String TABLE_SETTINGS = "settings";
    public static final String TABLE_PROJECTS = "projects";
    public static final String TABLE_MODELS = "model_configs";
    public static final String TABLE_CONVERSATIONS = "conversations";
    public static final String TABLE_MESSAGES = "messages";
    public static final String TABLE_MESSAGE_BLOCKS = "message_blocks";
    public static final String TABLE_TOOL_CALLS = "tool_calls";
    public static final String TABLE_TOOL_RESULTS = "tool_results";
    public static final String TABLE_ATTACHMENTS = "attachments";
    public static final String TABLE_DIFFS = "diff_records";
    public static final String TABLE_MEMORIES = "memories";
    public static final String TABLE_WORKING_MEMORY = "working_memory";
    public static final String TABLE_CONVERSATION_INDEX = "conversation_index";
    public static final String TABLE_SKILLS = "skills";
    public static final String TABLE_SKILL_USAGE = "skill_usage";
    public static final String TABLE_EXTENSION_AGENTS = "extension_agents";
    public static final String TABLE_EXTENSION_MCPS = "extension_mcps";
    public static final String TABLE_IMPORT_JOBS = "import_jobs";

    public static final String[] CREATE_SQL = new String[] {
            "CREATE TABLE IF NOT EXISTS metadata ("
                    + "key TEXT PRIMARY KEY,"
                    + "value TEXT NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ")",
            "CREATE TABLE IF NOT EXISTS settings ("
                    + "key TEXT PRIMARY KEY,"
                    + "value TEXT NOT NULL,"
                    + "type TEXT NOT NULL DEFAULT 'string',"
                    + "updated_at INTEGER NOT NULL"
                    + ")",
            "CREATE TABLE IF NOT EXISTS projects ("
                    + "id TEXT PRIMARY KEY,"
                    + "label TEXT NOT NULL,"
                    + "path TEXT NOT NULL,"
                    + "source TEXT NOT NULL,"
                    + "description TEXT,"
                    + "selected INTEGER NOT NULL DEFAULT 0,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ")",
            "CREATE TABLE IF NOT EXISTS model_configs ("
                    + "id TEXT PRIMARY KEY,"
                    + "name TEXT NOT NULL,"
                    + "protocol_type TEXT NOT NULL,"
                    + "provider_label TEXT NOT NULL,"
                    + "base_url TEXT,"
                    + "api_key TEXT,"
                    + "model_id TEXT NOT NULL,"
                    + "tool_call_limit INTEGER NOT NULL DEFAULT 200,"
                    + "compression_model_enabled INTEGER NOT NULL DEFAULT 0,"
                    + "compression_model_auto INTEGER NOT NULL DEFAULT 1,"
                    + "compression_model_id TEXT,"
                    + "selected INTEGER NOT NULL DEFAULT 0,"
                    + "raw_json TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ")",
            "CREATE TABLE IF NOT EXISTS conversations ("
                    + "id TEXT PRIMARY KEY,"
                    + "title TEXT NOT NULL,"
                    + "project_id TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "current INTEGER NOT NULL DEFAULT 0,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS messages ("
                    + "id TEXT PRIMARY KEY,"
                    + "conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,"
                    + "local_order INTEGER NOT NULL,"
                    + "role TEXT NOT NULL,"
                    + "content TEXT NOT NULL DEFAULT '',"
                    + "reasoning_content TEXT,"
                    + "timestamp INTEGER NOT NULL,"
                    + "streaming INTEGER NOT NULL DEFAULT 0,"
                    + "hidden INTEGER NOT NULL DEFAULT 0,"
                    + "exclude_from_context INTEGER NOT NULL DEFAULT 0,"
                    + "tool_call_id TEXT,"
                    + "tool_name TEXT,"
                    + "is_error INTEGER NOT NULL DEFAULT 0,"
                    + "raw_json TEXT,"
                    + "UNIQUE(conversation_id, local_order)"
                    + ")",
            "CREATE TABLE IF NOT EXISTS message_blocks ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                    + "block_order INTEGER NOT NULL,"
                    + "type TEXT NOT NULL,"
                    + "content TEXT,"
                    + "status TEXT,"
                    + "raw_json TEXT,"
                    + "UNIQUE(message_id, block_order)"
                    + ")",
            "CREATE TABLE IF NOT EXISTS tool_calls ("
                    + "id TEXT PRIMARY KEY,"
                    + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                    + "name TEXT NOT NULL,"
                    + "arguments TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS tool_results ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                    + "tool_call_id TEXT,"
                    + "content TEXT NOT NULL DEFAULT '',"
                    + "is_error INTEGER NOT NULL DEFAULT 0,"
                    + "diff_id TEXT,"
                    + "review_state TEXT,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS attachments ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                    + "name TEXT NOT NULL,"
                    + "path TEXT NOT NULL,"
                    + "source TEXT NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS diff_records ("
                    + "id TEXT PRIMARY KEY,"
                    + "file_path TEXT NOT NULL,"
                    + "old_content TEXT,"
                    + "new_content TEXT,"
                    + "old_exists INTEGER NOT NULL DEFAULT 1,"
                    + "timestamp INTEGER NOT NULL,"
                    + "reverted INTEGER NOT NULL DEFAULT 0,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS memories ("
                    + "id TEXT PRIMARY KEY,"
                    + "scope TEXT NOT NULL,"
                    + "project_id TEXT,"
                    + "content TEXT NOT NULL,"
                    + "source TEXT NOT NULL,"
                    + "confidence REAL NOT NULL DEFAULT 1,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "last_used_at INTEGER,"
                    + "use_count INTEGER NOT NULL DEFAULT 0,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS working_memory ("
                    + "id TEXT PRIMARY KEY,"
                    + "project_id TEXT,"
                    + "content TEXT NOT NULL,"
                    + "source TEXT NOT NULL,"
                    + "expires_at INTEGER,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS conversation_index ("
                    + "id TEXT PRIMARY KEY,"
                    + "project_id TEXT,"
                    + "conversation_id TEXT NOT NULL,"
                    + "message_id TEXT,"
                    + "role TEXT NOT NULL,"
                    + "text TEXT NOT NULL,"
                    + "title TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS skills ("
                    + "id TEXT PRIMARY KEY,"
                    + "name TEXT NOT NULL,"
                    + "scope TEXT NOT NULL,"
                    + "path TEXT,"
                    + "description TEXT,"
                    + "enabled INTEGER NOT NULL DEFAULT 1,"
                    + "updated_at INTEGER NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS skill_usage ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "skill_id TEXT NOT NULL,"
                    + "project_id TEXT,"
                    + "conversation_id TEXT,"
                    + "used_at INTEGER NOT NULL"
                    + ")",
            "CREATE TABLE IF NOT EXISTS extension_agents ("
                    + "id TEXT PRIMARY KEY,"
                    + "enabled INTEGER NOT NULL DEFAULT 1,"
                    + "name TEXT NOT NULL,"
                    + "slug TEXT NOT NULL,"
                    + "prompt TEXT NOT NULL,"
                    + "trigger TEXT,"
                    + "tool_names_json TEXT,"
                    + "mcp_ids_json TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS extension_mcps ("
                    + "id TEXT PRIMARY KEY,"
                    + "enabled INTEGER NOT NULL DEFAULT 1,"
                    + "name TEXT NOT NULL,"
                    + "url TEXT NOT NULL,"
                    + "request_headers_json TEXT,"
                    + "tools_json TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "raw_json TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS import_jobs ("
                    + "id TEXT PRIMARY KEY,"
                    + "source_name TEXT NOT NULL,"
                    + "source_format TEXT NOT NULL,"
                    + "status TEXT NOT NULL,"
                    + "started_at INTEGER NOT NULL,"
                    + "finished_at INTEGER,"
                    + "item_counts_json TEXT,"
                    + "error TEXT"
                    + ")",
            "CREATE INDEX IF NOT EXISTS idx_model_configs_selected ON model_configs(selected)",
            "CREATE INDEX IF NOT EXISTS idx_projects_selected ON projects(selected)",
            "CREATE INDEX IF NOT EXISTS idx_conversations_updated ON conversations(updated_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_messages_conversation_order ON messages(conversation_id, local_order)",
            "CREATE INDEX IF NOT EXISTS idx_message_blocks_message_order ON message_blocks(message_id, block_order)",
            "CREATE INDEX IF NOT EXISTS idx_conversation_index_project ON conversation_index(project_id, updated_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_memories_scope_project ON memories(scope, project_id)",
            "CREATE INDEX IF NOT EXISTS idx_working_memory_project ON working_memory(project_id, expires_at)",
            "CREATE INDEX IF NOT EXISTS idx_extension_agents_enabled ON extension_agents(enabled, updated_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_extension_mcps_enabled ON extension_mcps(enabled, updated_at DESC)"
    };

    public static final String[] OPTIONAL_FTS_SQL = new String[] {
            "CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts4("
                    + "id, scope, project_id, content, tokenize=unicode61"
                    + ")",
            "CREATE VIRTUAL TABLE IF NOT EXISTS conversation_index_fts USING fts4("
                    + "id, project_id, conversation_id, title, text, tokenize=unicode61"
                    + ")",
            "CREATE VIRTUAL TABLE IF NOT EXISTS working_memory_fts USING fts4("
                    + "id, project_id, content, tokenize=unicode61"
                    + ")"
    };

    public static final String[] DROP_SQL = new String[] {
            "DROP TABLE IF EXISTS working_memory_fts",
            "DROP TABLE IF EXISTS conversation_index_fts",
            "DROP TABLE IF EXISTS memories_fts",
            "DROP TABLE IF EXISTS import_jobs",
            "DROP TABLE IF EXISTS extension_mcps",
            "DROP TABLE IF EXISTS extension_agents",
            "DROP TABLE IF EXISTS skill_usage",
            "DROP TABLE IF EXISTS skills",
            "DROP TABLE IF EXISTS conversation_index",
            "DROP TABLE IF EXISTS working_memory",
            "DROP TABLE IF EXISTS memories",
            "DROP TABLE IF EXISTS diff_records",
            "DROP TABLE IF EXISTS attachments",
            "DROP TABLE IF EXISTS tool_results",
            "DROP TABLE IF EXISTS tool_calls",
            "DROP TABLE IF EXISTS message_blocks",
            "DROP TABLE IF EXISTS messages",
            "DROP TABLE IF EXISTS conversations",
            "DROP TABLE IF EXISTS model_configs",
            "DROP TABLE IF EXISTS projects",
            "DROP TABLE IF EXISTS settings",
            "DROP TABLE IF EXISTS metadata"
    };

    private LineCodeSchema() {
    }
}
