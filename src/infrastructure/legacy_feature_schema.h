#pragma once

#include <array>
#include <string_view>

namespace huxerui::sqlite {
class Transaction;
template <typename T> class Result;
} // namespace huxerui::sqlite

namespace linecode::infrastructure::legacy_feature_schema {

// The extension, terminal-provider, and memory subset of the legacy Android
// database v4. These
// tables must exist before an archive snapshot is matched against the live
// schema; otherwise compatible rows are silently absent from the import plan.
inline constexpr std::string_view create_memories = R"sql(
CREATE TABLE IF NOT EXISTS memories (
  id TEXT PRIMARY KEY,
  scope TEXT NOT NULL,
  project_id TEXT,
  content TEXT NOT NULL,
  source TEXT NOT NULL,
  confidence REAL NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  last_used_at INTEGER,
  use_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_working_memory = R"sql(
CREATE TABLE IF NOT EXISTS working_memory (
  id TEXT PRIMARY KEY,
  project_id TEXT,
  content TEXT NOT NULL,
  source TEXT NOT NULL,
  expires_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_conversation_index = R"sql(
CREATE TABLE IF NOT EXISTS conversation_index (
  id TEXT PRIMARY KEY,
  project_id TEXT,
  conversation_id TEXT NOT NULL,
  message_id TEXT,
  role TEXT NOT NULL,
  text TEXT NOT NULL,
  title TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_skills = R"sql(
CREATE TABLE IF NOT EXISTS skills (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  scope TEXT NOT NULL,
  path TEXT,
  description TEXT,
  enabled INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_extension_agents = R"sql(
CREATE TABLE IF NOT EXISTS extension_agents (
  id TEXT PRIMARY KEY,
  enabled INTEGER NOT NULL DEFAULT 1,
  name TEXT NOT NULL,
  slug TEXT NOT NULL,
  prompt TEXT NOT NULL,
  trigger TEXT,
  tool_names_json TEXT,
  mcp_ids_json TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_extension_mcps = R"sql(
CREATE TABLE IF NOT EXISTS extension_mcps (
  id TEXT PRIMARY KEY,
  enabled INTEGER NOT NULL DEFAULT 1,
  name TEXT NOT NULL,
  url TEXT NOT NULL,
  request_headers_json TEXT,
  tools_json TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_ipc_providers = R"sql(
CREATE TABLE IF NOT EXISTS ipc_providers (
  id TEXT PRIMARY KEY,
  enabled INTEGER NOT NULL DEFAULT 1,
  provider_type TEXT NOT NULL,
  name TEXT NOT NULL,
  package_name TEXT NOT NULL,
  service_class TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::array table_statements{
    create_memories, create_working_memory,   create_conversation_index,
    create_skills,   create_extension_agents, create_extension_mcps,
    create_ipc_providers,
};

inline constexpr std::string_view create_conversation_index_project_index =
    "CREATE INDEX IF NOT EXISTS idx_conversation_index_project "
    "ON conversation_index(project_id, updated_at DESC)";
inline constexpr std::string_view create_memories_scope_project_index =
    "CREATE INDEX IF NOT EXISTS idx_memories_scope_project "
    "ON memories(scope, project_id)";
inline constexpr std::string_view create_working_memory_project_index =
    "CREATE INDEX IF NOT EXISTS idx_working_memory_project "
    "ON working_memory(project_id, expires_at)";
inline constexpr std::string_view create_extension_agents_enabled_index =
    "CREATE INDEX IF NOT EXISTS idx_extension_agents_enabled "
    "ON extension_agents(enabled, updated_at DESC)";
inline constexpr std::string_view create_extension_mcps_enabled_index =
    "CREATE INDEX IF NOT EXISTS idx_extension_mcps_enabled "
    "ON extension_mcps(enabled, updated_at DESC)";
inline constexpr std::string_view create_ipc_providers_enabled_index =
    "CREATE INDEX IF NOT EXISTS idx_ipc_providers_enabled "
    "ON ipc_providers(enabled, provider_type, updated_at DESC)";

inline constexpr std::array index_statements{
    create_conversation_index_project_index,
    create_memories_scope_project_index,
    create_working_memory_project_index,
    create_extension_agents_enabled_index,
    create_extension_mcps_enabled_index,
    create_ipc_providers_enabled_index,
};

[[nodiscard]] huxerui::sqlite::Result<void>
Ensure(huxerui::sqlite::Transaction &transaction);

} // namespace linecode::infrastructure::legacy_feature_schema
