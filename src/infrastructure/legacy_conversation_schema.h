#pragma once

#include <string_view>

namespace linecode::infrastructure::legacy_schema {

inline constexpr int user_version = 4;

inline constexpr std::string_view create_conversations = R"sql(
CREATE TABLE IF NOT EXISTS conversations (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  project_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  current INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_messages = R"sql(
CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  local_order INTEGER NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  reasoning_content TEXT,
  timestamp INTEGER NOT NULL,
  streaming INTEGER NOT NULL DEFAULT 0,
  hidden INTEGER NOT NULL DEFAULT 0,
  exclude_from_context INTEGER NOT NULL DEFAULT 0,
  tool_call_id TEXT,
  tool_name TEXT,
  is_error INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT,
  UNIQUE(conversation_id, local_order)
)
)sql";

inline constexpr std::string_view create_conversations_updated_index =
    "CREATE INDEX IF NOT EXISTS idx_conversations_updated "
    "ON conversations(updated_at DESC)";

inline constexpr std::string_view create_messages_order_index =
    "CREATE INDEX IF NOT EXISTS idx_messages_conversation_order "
    "ON messages(conversation_id, local_order)";

} // namespace linecode::infrastructure::legacy_schema
