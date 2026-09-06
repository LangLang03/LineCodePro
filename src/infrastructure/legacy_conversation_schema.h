#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::infrastructure::legacy_schema {

// This is the conversation-owned subset of the legacy Android database v4.
// Keep these declarations compatible with LineCodeSchema.java; other bounded
// stores own the remaining legacy tables.
inline constexpr int user_version = 4;
inline constexpr std::size_t message_text_chunk_bytes = 64U * 1024U;

inline std::vector<std::string_view>
SplitMessageText(std::string_view content) {
  std::vector<std::string_view> chunks;
  for (std::size_t start = 0; start < content.size();) {
    std::size_t end = std::min(start + message_text_chunk_bytes, content.size());
    if (end < content.size()) {
      while (end > start &&
             (static_cast<unsigned char>(content[end]) & 0xC0U) == 0x80U) {
        --end;
      }
    }
    if (end == start) {
      end = std::min(start + message_text_chunk_bytes, content.size());
    }
    chunks.emplace_back(content.substr(start, end - start));
    start = end;
  }
  return chunks;
}

// Legacy v4 stores message text in ordered chunks and leaves the compatibility
// column empty. A non-empty chunk stream is authoritative; older unchunked
// rows fall back to messages.content.
inline constexpr std::string_view load_visible_messages = R"sql(
SELECT m.id, m.local_order, m.role,
       COALESCE(
         NULLIF((
           SELECT group_concat(ordered_chunk.content, '')
           FROM (
             SELECT content
             FROM message_text_chunks
             WHERE message_id = m.id AND field_name = 'content'
             ORDER BY chunk_order
           ) AS ordered_chunk
         ), ''),
         m.content,
         ''
       )
FROM messages AS m
WHERE m.conversation_id = ? AND m.hidden = 0
ORDER BY m.local_order
)sql";

inline constexpr std::string_view list_visible_conversations = R"sql(
SELECT id, title, updated_at FROM conversations
WHERE EXISTS (
  SELECT 1 FROM messages
  WHERE conversation_id = conversations.id AND hidden = 0
  AND role NOT IN ('system', 'tool')
)
ORDER BY updated_at DESC
)sql";

inline constexpr std::string_view find_resume_conversation = R"sql(
SELECT c.id, c.created_at FROM conversations AS c
WHERE EXISTS (
  SELECT 1 FROM messages AS m
  WHERE m.conversation_id = c.id AND m.hidden = 0
  AND m.role NOT IN ('system', 'tool')
)
ORDER BY CASE WHEN c.current = 1 THEN 0 ELSE 1 END,
         c.updated_at DESC
LIMIT 1
)sql";

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

inline constexpr std::string_view create_message_text_chunks = R"sql(
CREATE TABLE IF NOT EXISTS message_text_chunks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  field_name TEXT NOT NULL,
  chunk_order INTEGER NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  UNIQUE(message_id, field_name, chunk_order)
)
)sql";

inline constexpr std::string_view create_message_blocks = R"sql(
CREATE TABLE IF NOT EXISTS message_blocks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  block_order INTEGER NOT NULL,
  type TEXT NOT NULL,
  content TEXT,
  status TEXT,
  raw_json TEXT,
  UNIQUE(message_id, block_order)
)
)sql";

inline constexpr std::string_view create_tool_calls = R"sql(
CREATE TABLE IF NOT EXISTS tool_calls (
  id TEXT PRIMARY KEY,
  message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  arguments TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  error_message TEXT,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_tool_results = R"sql(
CREATE TABLE IF NOT EXISTS tool_results (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  tool_call_id TEXT,
  content TEXT NOT NULL DEFAULT '',
  is_error INTEGER NOT NULL DEFAULT 0,
  diff_id TEXT,
  review_state TEXT,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_attachments = R"sql(
CREATE TABLE IF NOT EXISTS attachments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  path TEXT NOT NULL,
  source TEXT NOT NULL,
  raw_json TEXT
)
)sql";

inline constexpr std::string_view create_diff_records = R"sql(
CREATE TABLE IF NOT EXISTS diff_records (
  id TEXT PRIMARY KEY,
  file_path TEXT NOT NULL,
  old_content TEXT,
  new_content TEXT,
  old_exists INTEGER NOT NULL DEFAULT 1,
  timestamp INTEGER NOT NULL,
  reverted INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
)
)sql";

inline constexpr std::array table_statements{
    create_conversations,  create_messages,     create_message_text_chunks,
    create_message_blocks, create_tool_calls,   create_tool_results,
    create_attachments,    create_diff_records,
};

inline constexpr std::string_view create_conversations_updated_index =
    "CREATE INDEX IF NOT EXISTS idx_conversations_updated "
    "ON conversations(updated_at DESC)";

inline constexpr std::string_view create_messages_order_index =
    "CREATE INDEX IF NOT EXISTS idx_messages_conversation_order "
    "ON messages(conversation_id, local_order)";

inline constexpr std::string_view create_message_text_chunks_index =
    "CREATE INDEX IF NOT EXISTS idx_message_text_chunks_message_field "
    "ON message_text_chunks(message_id, field_name, chunk_order)";

inline constexpr std::string_view create_message_blocks_index =
    "CREATE INDEX IF NOT EXISTS idx_message_blocks_message_order "
    "ON message_blocks(message_id, block_order)";

inline constexpr std::array index_statements{
    create_conversations_updated_index,
    create_messages_order_index,
    create_message_text_chunks_index,
    create_message_blocks_index,
};

struct RequiredColumn final {
  std::string_view table;
  std::string_view name;
  std::string_view add_statement;
};

// v2 introduced these columns. CREATE TABLE IF NOT EXISTS cannot repair an
// already existing v1 tool_calls table, so they must be probed individually.
inline constexpr std::array required_columns{
    RequiredColumn{
        .table = "tool_calls",
        .name = "duration_ms",
        .add_statement =
            "ALTER TABLE tool_calls ADD COLUMN duration_ms INTEGER NOT NULL "
            "DEFAULT 0",
    },
    RequiredColumn{
        .table = "tool_calls",
        .name = "error_message",
        .add_statement = "ALTER TABLE tool_calls ADD COLUMN error_message TEXT",
    },
};

} // namespace linecode::infrastructure::legacy_schema
