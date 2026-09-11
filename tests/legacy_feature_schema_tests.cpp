#include <algorithm>
#include <cassert>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

#include <sqlite3.h>

#include "infrastructure/legacy_feature_schema.h"

namespace {

using linecode::infrastructure::legacy_feature_schema::index_statements;
using linecode::infrastructure::legacy_feature_schema::table_statements;

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
  }

  ~Database() { sqlite3_close(handle_); }

  Database(const Database &) = delete;
  Database &operator=(const Database &) = delete;

  void Execute(std::string_view sql) {
    char *error = nullptr;
    const std::string owned{sql};
    if (sqlite3_exec(handle_, owned.c_str(), nullptr, nullptr, &error) ==
        SQLITE_OK)
      return;
    const std::string message =
        error == nullptr ? "sqlite3_exec failed" : std::string{error};
    sqlite3_free(error);
    throw std::runtime_error(message);
  }

  [[nodiscard]] std::int64_t Integer(std::string_view sql) const {
    sqlite3_stmt *statement = nullptr;
    const std::string owned{sql};
    if (sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr) !=
        SQLITE_OK)
      throw std::runtime_error(sqlite3_errmsg(handle_));
    if (sqlite3_step(statement) != SQLITE_ROW) {
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
        SQLITE_OK)
      throw std::runtime_error(sqlite3_errmsg(handle_));
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

void ApplySchema(Database &database) {
  database.Execute("BEGIN IMMEDIATE");
  try {
    for (const auto statement : table_statements)
      database.Execute(statement);
    for (const auto statement : index_statements)
      database.Execute(statement);
    database.Execute("COMMIT");
  } catch (...) {
    try {
      database.Execute("ROLLBACK");
    } catch (...) {
    }
    throw;
  }
}

[[nodiscard]] bool Contains(const std::vector<std::string> &values,
                            std::string_view expected) {
  return std::ranges::find(values, expected) != values.end();
}

void FreshDatabaseGetsTheLegacyFeatureSchema() {
  static_assert(table_statements.size() == 7);
  static_assert(index_statements.size() == 6);
  Database database;

  ApplySchema(database);
  ApplySchema(database);

  const auto tables = database.TextColumn(
      "SELECT name FROM sqlite_master WHERE type = 'table'");
  for (const std::string_view table :
       {"memories", "working_memory", "conversation_index", "skills",
        "extension_agents", "extension_mcps", "ipc_providers"}) {
    assert(Contains(tables, table));
  }

  const auto indexes = database.TextColumn(
      "SELECT name FROM sqlite_master WHERE type = 'index'");
  for (const std::string_view index :
       {"idx_conversation_index_project", "idx_memories_scope_project",
        "idx_working_memory_project", "idx_extension_agents_enabled",
        "idx_extension_mcps_enabled", "idx_ipc_providers_enabled"}) {
    assert(Contains(indexes, index));
  }

  assert((database.TextColumn(
              "PRAGMA index_info(idx_conversation_index_project)", 2) ==
          std::vector<std::string>{"project_id", "updated_at"}));
  assert((
      database.TextColumn("PRAGMA index_info(idx_memories_scope_project)", 2) ==
      std::vector<std::string>{"scope", "project_id"}));
  assert((
      database.TextColumn("PRAGMA index_info(idx_working_memory_project)", 2) ==
      std::vector<std::string>{"project_id", "expires_at"}));
  assert((database.TextColumn("PRAGMA index_info(idx_extension_agents_enabled)",
                              2) ==
          std::vector<std::string>{"enabled", "updated_at"}));
  assert((
      database.TextColumn("PRAGMA index_info(idx_extension_mcps_enabled)", 2) ==
      std::vector<std::string>{"enabled", "updated_at"}));
  assert((database.TextColumn("PRAGMA index_info(idx_ipc_providers_enabled)",
                              2) ==
          std::vector<std::string>{"enabled", "provider_type",
                                   "updated_at"}));
  assert(database.Integer(
             "SELECT desc FROM pragma_index_xinfo("
             "'idx_conversation_index_project') WHERE name = 'updated_at'") ==
         1);
  assert(database.Integer(
             "SELECT desc FROM pragma_index_xinfo("
             "'idx_extension_agents_enabled') WHERE name = 'updated_at'") == 1);
  assert(database.Integer(
             "SELECT desc FROM pragma_index_xinfo("
             "'idx_extension_mcps_enabled') WHERE name = 'updated_at'") == 1);
  assert(database.Integer(
             "SELECT desc FROM pragma_index_xinfo("
             "'idx_ipc_providers_enabled') WHERE name = 'updated_at'") == 1);
}

void ColumnsAndDefaultsStayArchiveCompatible() {
  Database database;
  ApplySchema(database);

  assert((database.TextColumn("PRAGMA table_info(memories)", 1) ==
          std::vector<std::string>{"id", "scope", "project_id", "content",
                                   "source", "confidence", "created_at",
                                   "updated_at", "last_used_at", "use_count",
                                   "raw_json"}));
  assert((database.TextColumn("PRAGMA table_info(working_memory)", 1) ==
          std::vector<std::string>{"id", "project_id", "content", "source",
                                   "expires_at", "created_at", "updated_at",
                                   "raw_json"}));
  assert((database.TextColumn("PRAGMA table_info(conversation_index)", 1) ==
          std::vector<std::string>{"id", "project_id", "conversation_id",
                                   "message_id", "role", "text", "title",
                                   "created_at", "updated_at", "raw_json"}));
  assert((database.TextColumn("PRAGMA table_info(skills)", 1) ==
          std::vector<std::string>{"id", "name", "scope", "path", "description",
                                   "enabled", "updated_at", "raw_json"}));
  assert((database.TextColumn("PRAGMA table_info(extension_agents)", 1) ==
          std::vector<std::string>{"id", "enabled", "name", "slug", "prompt",
                                   "trigger", "tool_names_json", "mcp_ids_json",
                                   "created_at", "updated_at", "raw_json"}));
  assert((database.TextColumn("PRAGMA table_info(extension_mcps)", 1) ==
          std::vector<std::string>{"id", "enabled", "name", "url",
                                   "request_headers_json", "tools_json",
                                   "created_at", "updated_at", "raw_json"}));
  assert((database.TextColumn("PRAGMA table_info(ipc_providers)", 1) ==
          std::vector<std::string>{"id", "enabled", "provider_type", "name",
                                   "package_name", "service_class",
                                   "created_at", "updated_at", "raw_json"}));

  database.Execute("INSERT INTO memories "
                   "(id, scope, content, source, created_at, updated_at) "
                   "VALUES ('memory-1', 'user', 'kept', 'manual', 1, 2)");
  database.Execute("INSERT INTO skills (id, name, scope, updated_at) "
                   "VALUES ('skill-1', 'Kept', 'app', 3)");
  database.Execute("INSERT INTO extension_agents "
                   "(id, name, slug, prompt, created_at, updated_at) "
                   "VALUES ('agent-1', 'Kept', 'kept', 'prompt', 4, 5)");
  database.Execute(
      "INSERT INTO extension_mcps "
      "(id, name, url, created_at, updated_at) "
      "VALUES ('mcp-1', 'Kept', 'https://example.test/mcp', 6, 7)");
  database.Execute(
      "INSERT INTO ipc_providers "
      "(id, provider_type, name, package_name, service_class, created_at, "
      "updated_at) VALUES "
      "('ipc-1', 'terminal', 'Kept', 'dev.provider', '.Service', 8, 9)");

  ApplySchema(database);
  assert(
      database.Integer("SELECT COUNT(*) FROM memories WHERE confidence = 1 AND "
                       "use_count = 0") == 1);
  assert(database.Integer("SELECT COUNT(*) FROM skills WHERE enabled = 1") ==
         1);
  assert(database.Integer(
             "SELECT COUNT(*) FROM extension_agents WHERE enabled = 1") == 1);
  assert(database.Integer(
             "SELECT COUNT(*) FROM extension_mcps WHERE enabled = 1") == 1);
  assert(database.Integer(
             "SELECT COUNT(*) FROM ipc_providers WHERE enabled = 1") == 1);
}

} // namespace

int main() {
  FreshDatabaseGetsTheLegacyFeatureSchema();
  ColumnsAndDefaultsStayArchiveCompatible();
}
