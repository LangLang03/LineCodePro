#include <cassert>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

#include <sqlite3.h>

#include "infrastructure/legacy_conversation_schema.h"

namespace {

using linecode::infrastructure::legacy_schema::index_statements;
using linecode::infrastructure::legacy_schema::required_columns;
using linecode::infrastructure::legacy_schema::table_statements;
using linecode::infrastructure::legacy_schema::user_version;

class Database final {
public:
  Database() {
    if (sqlite3_open(":memory:", &handle_) != SQLITE_OK) {
      const std::string message =
          handle_ == nullptr ? "sqlite3_open failed" : sqlite3_errmsg(handle_);
      sqlite3_close(handle_);
      handle_ = nullptr;
      throw std::runtime_error(message);
    }
    Execute("PRAGMA foreign_keys = ON");
  }

  ~Database() { sqlite3_close(handle_); }

  Database(const Database &) = delete;
  Database &operator=(const Database &) = delete;

  void Execute(std::string_view sql) {
    char *error = nullptr;
    const std::string owned{sql};
    if (sqlite3_exec(handle_, owned.c_str(), nullptr, nullptr, &error) !=
        SQLITE_OK) {
      const std::string message =
          error == nullptr ? "sqlite3_exec failed" : std::string{error};
      sqlite3_free(error);
      throw std::runtime_error(message);
    }
  }

  [[nodiscard]] std::int64_t Integer(std::string_view sql) const {
    sqlite3_stmt *statement = nullptr;
    const std::string owned{sql};
    if (sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr) !=
        SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(handle_));
    }
    const int step = sqlite3_step(statement);
    if (step != SQLITE_ROW) {
      const std::string message = sqlite3_errmsg(handle_);
      sqlite3_finalize(statement);
      throw std::runtime_error(message);
    }
    const auto value = sqlite3_column_int64(statement, 0);
    sqlite3_finalize(statement);
    return value;
  }

  [[nodiscard]] std::vector<std::string> TextColumn(std::string_view sql,
                                                    int column = 0) const {
    sqlite3_stmt *statement = nullptr;
    const std::string owned{sql};
    if (sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr) !=
        SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(handle_));
    }
    std::vector<std::string> values;
    for (int step = sqlite3_step(statement); step == SQLITE_ROW;
         step = sqlite3_step(statement)) {
      const auto *text = sqlite3_column_text(statement, column);
      values.emplace_back(
          text == nullptr ? "" : reinterpret_cast<const char *>(text));
    }
    sqlite3_finalize(statement);
    return values;
  }

  [[nodiscard]] std::vector<std::string>
  TextColumn(std::string_view sql, std::string_view parameter,
             int column) const {
    sqlite3_stmt *statement = nullptr;
    const std::string owned{sql};
    if (sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr) !=
        SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(handle_));
    }
    if (sqlite3_bind_text(statement, 1, parameter.data(),
                          static_cast<int>(parameter.size()),
                          SQLITE_TRANSIENT) != SQLITE_OK) {
      const std::string message = sqlite3_errmsg(handle_);
      sqlite3_finalize(statement);
      throw std::runtime_error(message);
    }
    std::vector<std::string> values;
    for (int step = sqlite3_step(statement); step == SQLITE_ROW;
         step = sqlite3_step(statement)) {
      const auto *text = sqlite3_column_text(statement, column);
      values.emplace_back(
          text == nullptr ? "" : reinterpret_cast<const char *>(text));
    }
    sqlite3_finalize(statement);
    return values;
  }

private:
  sqlite3 *handle_{};
};

[[nodiscard]] bool Contains(const std::vector<std::string> &values,
                            std::string_view expected) {
  for (const auto &value : values) {
    if (value == expected) {
      return true;
    }
  }
  return false;
}

[[nodiscard]] bool ColumnExists(const Database &database,
                                std::string_view table,
                                std::string_view column) {
  return Contains(
      database.TextColumn("PRAGMA table_info(" + std::string{table} + ")", 1),
      column);
}

void ApplySchema(Database &database) {
  database.Execute("BEGIN IMMEDIATE");
  try {
    const auto version = database.Integer("PRAGMA user_version");
    if (version < 0 || version > user_version) {
      throw std::runtime_error("unsupported schema version");
    }
    for (const auto statement : table_statements) {
      database.Execute(statement);
    }
    for (const auto &required : required_columns) {
      if (!ColumnExists(database, required.table, required.name)) {
        database.Execute(required.add_statement);
      }
    }
    for (const auto statement : index_statements) {
      database.Execute(statement);
    }
    database.Execute("PRAGMA user_version = 4");
    database.Execute("COMMIT");
  } catch (...) {
    try {
      database.Execute("ROLLBACK");
    } catch (...) {
    }
    throw;
  }
}

void FreshDatabaseGetsLegacyV4CoreSchema() {
  Database database;

  ApplySchema(database);
  ApplySchema(database);

  assert(database.Integer("PRAGMA user_version") == user_version);
  const auto tables = database.TextColumn(
      "SELECT name FROM sqlite_master WHERE type = 'table'");
  for (const std::string_view table :
       {"conversations", "messages", "message_text_chunks", "message_blocks",
        "tool_calls", "tool_results", "attachments", "diff_records"}) {
    assert(Contains(tables, table));
  }

  const auto indexes = database.TextColumn(
      "SELECT name FROM sqlite_master WHERE type = 'index'");
  for (const std::string_view index :
       {"idx_conversations_updated", "idx_messages_conversation_order",
        "idx_message_text_chunks_message_field",
        "idx_message_blocks_message_order"}) {
    assert(Contains(indexes, index));
  }

  assert(ColumnExists(database, "tool_calls", "duration_ms"));
  assert(ColumnExists(database, "tool_calls", "error_message"));
  assert(database.Integer(
             "SELECT COUNT(*) FROM pragma_foreign_key_list('attachments') "
             "WHERE \"table\" = 'messages' AND \"from\" = 'message_id' "
             "AND on_delete = 'CASCADE'") == 1);
}

void ExistingLegacyRowsSurviveIdempotentMigration() {
  Database database;
  database.Execute("PRAGMA user_version = 1");
  database.Execute(
      linecode::infrastructure::legacy_schema::create_conversations);
  database.Execute(linecode::infrastructure::legacy_schema::create_messages);
  database.Execute(
      "CREATE TABLE tool_calls ("
      "id TEXT PRIMARY KEY,"
      "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
      "name TEXT NOT NULL, arguments TEXT NOT NULL, created_at INTEGER NOT "
      "NULL, raw_json TEXT)");
  database.Execute("INSERT INTO conversations "
                   "(id, title, created_at, updated_at, current) "
                   "VALUES ('conversation-1', 'kept', 10, 20, 1)");
  database.Execute(
      "INSERT INTO messages "
      "(id, conversation_id, local_order, role, content, timestamp) "
      "VALUES ('message-1', 'conversation-1', 0, 'assistant', 'kept', 30)");
  database.Execute("INSERT INTO tool_calls "
                   "(id, message_id, name, arguments, created_at) "
                   "VALUES ('tool-1', 'message-1', 'read', '{}', 40)");

  ApplySchema(database);
  ApplySchema(database);

  assert(database.Integer("SELECT COUNT(*) FROM conversations WHERE title = "
                          "'kept'") == 1);
  assert(database.Integer("SELECT COUNT(*) FROM messages WHERE content = "
                          "'kept'") == 1);
  assert(database.Integer("SELECT COUNT(*) FROM tool_calls WHERE id = "
                          "'tool-1' AND duration_ms = 0 AND error_message IS "
                          "NULL") == 1);
  assert(database.Integer("PRAGMA user_version") == user_version);
}

void NewerSchemaIsRejectedWithoutPartialWrites() {
  Database database;
  database.Execute("PRAGMA user_version = 5");

  bool rejected = false;
  try {
    ApplySchema(database);
  } catch (const std::runtime_error &) {
    rejected = true;
  }

  assert(rejected);
  assert(database.Integer("PRAGMA user_version") == 5);
  assert(database.Integer(
             "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND "
             "name = 'conversations'") == 0);
}

void ChunkedMessageContentIsAuthoritativeWithColumnFallback() {
  Database database;
  ApplySchema(database);
  database.Execute(
      "INSERT INTO conversations "
      "(id, title, created_at, updated_at, current) "
      "VALUES ('conversation-1', 'chunks', 10, 20, 1)");
  database.Execute(
      "INSERT INTO messages "
      "(id, conversation_id, local_order, role, content, timestamp) VALUES "
      "('chunk-only', 'conversation-1', 0, 'user', '', 30),"
      "('fallback', 'conversation-1', 1, 'assistant', 'legacy body', 31),"
      "('chunk-wins', 'conversation-1', 2, 'assistant', 'stale body', 32)");
  database.Execute(
      "INSERT INTO message_text_chunks "
      "(message_id, field_name, chunk_order, content) VALUES "
      "('chunk-only', 'content', 1, 'world'),"
      "('chunk-only', 'content', 0, 'hello '),"
      "('chunk-wins', 'content', 0, 'fresh body')");

  const auto bodies = database.TextColumn(
      linecode::infrastructure::legacy_schema::load_visible_messages,
      "conversation-1", 3);
  assert((bodies == std::vector<std::string>{"hello world", "legacy body",
                                             "fresh body"}));
}

void LongUtf8ContentSplitsWithoutBreakingCodePoints() {
  using linecode::infrastructure::legacy_schema::message_text_chunk_bytes;
  using linecode::infrastructure::legacy_schema::SplitMessageText;

  std::string body(message_text_chunk_bytes - 1, 'a');
  body += "界";
  body += std::string(message_text_chunk_bytes, 'b');
  const auto chunks = SplitMessageText(body);
  assert(chunks.size() == 3);
  assert(chunks[0].size() == message_text_chunk_bytes - 1);
  assert(chunks[1].starts_with("界"));
  std::string reassembled;
  for (const auto chunk : chunks) {
    assert(chunk.size() <= message_text_chunk_bytes);
    reassembled.append(chunk);
  }
  assert(reassembled == body);
}

void DrawerListsOnlyVisibleConversationsAndResumesLatestVisible() {
  Database database;
  ApplySchema(database);
  database.Execute(
      "INSERT INTO conversations "
      "(id, title, created_at, updated_at, current) VALUES "
      "('empty-current', 'empty', 1, 500, 1),"
      "('hidden', 'hidden', 2, 400, 0),"
      "('tool-only', 'tool', 3, 300, 0),"
      "('visible-old', 'old', 4, 100, 0),"
      "('visible-new', 'new', 5, 200, 0),"
      "('visible-current-two', 'two', 6, 150, 2)");
  database.Execute(
      "INSERT INTO messages "
      "(id, conversation_id, local_order, role, content, timestamp, hidden) "
      "VALUES "
      "('hidden-message', 'hidden', 0, 'user', 'hidden', 10, 1),"
      "('tool-message', 'tool-only', 0, 'tool', 'tool', 11, 0),"
      "('old-message', 'visible-old', 0, 'user', 'old', 12, 0),"
      "('new-message', 'visible-new', 0, 'assistant', 'new', 13, 0),"
      "('two-message', 'visible-current-two', 0, 'user', 'two', 14, 0)");

  const auto visible = database.TextColumn(
      linecode::infrastructure::legacy_schema::list_visible_conversations, 1);
  assert((visible == std::vector<std::string>{"new", "two", "old"}));
  const auto fallback = database.TextColumn(
      linecode::infrastructure::legacy_schema::find_resume_conversation);
  assert((fallback == std::vector<std::string>{"visible-new"}));

  database.Execute(
      "UPDATE conversations SET current = CASE "
      "WHEN id = 'visible-old' THEN 1 ELSE 0 END");
  const auto explicit_current = database.TextColumn(
      linecode::infrastructure::legacy_schema::find_resume_conversation);
  assert((explicit_current == std::vector<std::string>{"visible-old"}));
}

} // namespace

int main() {
  FreshDatabaseGetsLegacyV4CoreSchema();
  ExistingLegacyRowsSurviveIdempotentMigration();
  NewerSchemaIsRejectedWithoutPartialWrites();
  ChunkedMessageContentIsAuthoritativeWithColumnFallback();
  LongUtf8ContentSplitsWithoutBreakingCodePoints();
  DrawerListsOnlyVisibleConversationsAndResumesLatestVisible();
  return 0;
}
