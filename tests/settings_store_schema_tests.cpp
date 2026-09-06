#include <cassert>
#include <cstdint>
#include <limits>
#include <set>
#include <stdexcept>
#include <string>
#include <string_view>

#include <sqlite3.h>

#include "application/ports/settings_store.h"
#include "infrastructure/sqlite_settings_store.h"

namespace {

class Database final {
public:
  Database() {
    if (sqlite3_open(":memory:", &handle_) != SQLITE_OK)
      throw std::runtime_error("sqlite3_open failed");
  }

  ~Database() { sqlite3_close(handle_); }

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

  [[nodiscard]] std::set<std::string, std::less<>> Columns() const {
    sqlite3_stmt *statement = nullptr;
    if (sqlite3_prepare_v2(handle_, "PRAGMA table_info(settings)", -1,
                           &statement, nullptr) != SQLITE_OK)
      throw std::runtime_error(sqlite3_errmsg(handle_));
    std::set<std::string, std::less<>> columns;
    for (int step = sqlite3_step(statement); step == SQLITE_ROW;
         step = sqlite3_step(statement)) {
      const auto *text = sqlite3_column_text(statement, 1);
      if (text != nullptr)
        columns.emplace(reinterpret_cast<const char *>(text));
    }
    sqlite3_finalize(statement);
    return columns;
  }

  [[nodiscard]] std::string ScalarText(std::string_view sql) const {
    sqlite3_stmt *statement = nullptr;
    const std::string owned{sql};
    if (sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr) !=
        SQLITE_OK)
      throw std::runtime_error(sqlite3_errmsg(handle_));
    if (sqlite3_step(statement) != SQLITE_ROW) {
      sqlite3_finalize(statement);
      throw std::runtime_error("query returned no row");
    }
    const auto *text = sqlite3_column_text(statement, 0);
    const std::string value =
        text == nullptr ? std::string{} : reinterpret_cast<const char *>(text);
    sqlite3_finalize(statement);
    return value;
  }

private:
  sqlite3 *handle_{};
};

void EnsureCompatibleSettingsSchema(Database &database) {
  using namespace linecode::infrastructure::sqlite_settings_schema;
  database.Execute(create_table);
  const auto columns = database.Columns();
  if (!columns.contains("type"))
    database.Execute(add_type_column);
  if (!columns.contains("updated_at"))
    database.Execute(add_updated_at_column);
}

} // namespace

int main() {
  using namespace linecode;

  assert(application::SettingsValueKindName(
             application::SettingsValueKind::string) == "string");
  assert(application::SettingsValueKindName(
             application::SettingsValueKind::boolean) == "boolean");
  assert(application::SettingsValueKindName(
             application::SettingsValueKind::integer) == "long");
  assert(application::ParseStoredBoolean("true"));
  assert(application::ParseStoredBoolean("TRUE"));
  assert(application::ParseStoredBoolean("1"));
  assert(!application::ParseStoredBoolean("false"));
  assert(!application::ParseStoredBoolean("yes"));

  assert(application::ParseStoredInteger("-42") == -42);
  assert(application::ParseStoredInteger("0") == 0);
  assert(!application::ParseStoredInteger("42x"));
  assert(!application::ParseStoredInteger(""));
  assert(application::ParseStoredInteger(
             std::to_string(std::numeric_limits<std::int64_t>::max())) ==
         std::numeric_limits<std::int64_t>::max());

  Database legacy;
  legacy.Execute(
      "CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  legacy.Execute("INSERT INTO settings (key, value) VALUES ('theme', 'dark')");
  EnsureCompatibleSettingsSchema(legacy);
  EnsureCompatibleSettingsSchema(legacy);
  const auto migrated_columns = legacy.Columns();
  assert(migrated_columns.contains("key"));
  assert(migrated_columns.contains("value"));
  assert(migrated_columns.contains("type"));
  assert(migrated_columns.contains("updated_at"));
  assert(legacy.ScalarText("SELECT value FROM settings WHERE key = 'theme'") ==
         "dark");
  assert(legacy.ScalarText("SELECT type FROM settings WHERE key = 'theme'") ==
         "string");
  assert(legacy.ScalarText(
             "SELECT CAST(updated_at AS TEXT) FROM settings WHERE key = "
             "'theme'") == "0");

  Database fresh;
  EnsureCompatibleSettingsSchema(fresh);
  fresh.Execute(
      "INSERT INTO settings (key, value, type, updated_at) VALUES "
      "('@linecode_test', 'kept', 'string', 123)");
  EnsureCompatibleSettingsSchema(fresh);
  assert(fresh.ScalarText(
             "SELECT value FROM settings WHERE key = '@linecode_test'") ==
         "kept");
}
