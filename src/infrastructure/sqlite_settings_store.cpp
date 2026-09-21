#include "infrastructure/sqlite_settings_store.h"

#include <algorithm>
#include <chrono>
#include <utility>
#include <vector>

#include <huxerui/sqlite.h>

namespace linecode::infrastructure {

class SQLiteSettingsStoreState final {
public:
  explicit SQLiteSettingsStoreState(huxerui::File database_file)
      : database_file(std::move(database_file)) {}

  huxerui::File database_file;
  std::optional<huxerui::sqlite::Database> database;
};

namespace {

using application::SettingsResult;
using application::SettingsStoreError;
using huxerui::sqlite::Database;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;

[[nodiscard]] SettingsStoreError
StoreError(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

[[nodiscard]] std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

[[nodiscard]] Result<std::string> DecodeText(const RowView &row) {
  return row.Get<std::string>(0);
}

[[nodiscard]] Result<std::pair<std::string, std::string>>
DecodeSetting(const RowView &row) {
  auto key = row.Get<std::string>(0);
  if (!key)
    return key.Error();
  auto value = row.Get<std::string>(1);
  if (!value)
    return value.Error();
  return std::pair{std::move(*key), std::move(*value)};
}

[[nodiscard]] bool Contains(const std::vector<std::string> &values,
                            std::string_view target) {
  return std::ranges::find(values, target) != values.end();
}

[[nodiscard]] huxerui::Task<SettingsResult<Database>>
Open(const std::shared_ptr<SQLiteSettingsStoreState> &state) {
  if (state->database)
    co_return *state->database;

  auto opened = co_await Database::OpenAsync(
      state->database_file,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(StoreError(opened.Error()));

  auto compatible = co_await opened->TransactionAsync(
      [](Transaction &transaction) -> Result<void> {
        auto created = transaction.Execute(
            std::string{sqlite_settings_schema::create_table});
        if (!created)
          return created.Error();

        auto columns = transaction.Query<std::string>(
            "PRAGMA table_info(settings)",
            [](const RowView &row) { return row.Get<std::string>(1); });
        if (!columns)
          return columns.Error();

        if (!Contains(*columns, "type")) {
          auto added = transaction.Execute(
              std::string{sqlite_settings_schema::add_type_column});
          if (!added)
            return added.Error();
        }
        if (!Contains(*columns, "updated_at")) {
          auto added = transaction.Execute(
              std::string{sqlite_settings_schema::add_updated_at_column});
          if (!added)
            return added.Error();
        }
        return {};
      });
  if (!compatible)
    co_return std::unexpected(StoreError(compatible.Error()));

  state->database = *opened;
  co_return *state->database;
}

[[nodiscard]] huxerui::Task<
    SettingsResult<std::optional<std::string>>>
ReadValue(const std::shared_ptr<SQLiteSettingsStoreState> &state,
          std::string key) {
  auto database = co_await Open(state);
  if (!database)
    co_return std::unexpected(database.error());

  auto rows = co_await database->QueryAsync<std::string>(
      "SELECT value FROM settings WHERE key = ? LIMIT 1", DecodeText, key);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (rows->empty())
    co_return std::optional<std::string>{};
  co_return std::optional<std::string>{std::move(rows->front())};
}

[[nodiscard]] huxerui::Task<SettingsResult<void>>
WriteValue(const std::shared_ptr<SQLiteSettingsStoreState> &state,
           std::string key, std::string value,
           application::SettingsValueKind kind) {
  auto database = co_await Open(state);
  if (!database)
    co_return std::unexpected(database.error());

  auto written = co_await database->ExecuteAsync(
      "INSERT INTO settings (key, value, type, updated_at) VALUES (?, ?, ?, ?) "
      "ON CONFLICT(key) DO UPDATE SET value = excluded.value, "
      "type = excluded.type, updated_at = excluded.updated_at",
      key, value, std::string{application::SettingsValueKindName(kind)},
      NowMilliseconds());
  if (!written)
    co_return std::unexpected(StoreError(written.Error()));
  co_return SettingsResult<void>{};
}

} // namespace

SQLiteSettingsStore::SQLiteSettingsStore(huxerui::File database_file)
    : state_(std::make_shared<SQLiteSettingsStoreState>(
          std::move(database_file))) {}

huxerui::Task<SettingsResult<std::string>>
SQLiteSettingsStore::GetString(std::string key, std::string fallback) {
  auto stored = co_await ReadValue(state_, std::move(key));
  if (!stored)
    co_return std::unexpected(stored.error());
  co_return stored->value_or(std::move(fallback));
}

huxerui::Task<SettingsResult<bool>>
SQLiteSettingsStore::GetBoolean(std::string key, bool fallback) {
  auto stored = co_await ReadValue(state_, std::move(key));
  if (!stored)
    co_return std::unexpected(stored.error());
  co_return stored->has_value() ? application::ParseStoredBoolean(**stored)
                                : fallback;
}

huxerui::Task<SettingsResult<std::int64_t>>
SQLiteSettingsStore::GetInteger(std::string key, std::int64_t fallback) {
  auto stored = co_await ReadValue(state_, std::move(key));
  if (!stored)
    co_return std::unexpected(stored.error());
  if (!stored->has_value())
    co_return fallback;
  co_return application::ParseStoredInteger(**stored).value_or(fallback);
}

huxerui::Task<SettingsResult<void>>
SQLiteSettingsStore::SetString(std::string key, std::string value) {
  co_return co_await WriteValue(state_, std::move(key), std::move(value),
                                application::SettingsValueKind::string);
}

huxerui::Task<SettingsResult<void>>
SQLiteSettingsStore::SetBoolean(std::string key, bool value) {
  co_return co_await WriteValue(state_, std::move(key),
                                value ? "true" : "false",
                                application::SettingsValueKind::boolean);
}

huxerui::Task<SettingsResult<void>>
SQLiteSettingsStore::SetInteger(std::string key, std::int64_t value) {
  co_return co_await WriteValue(state_, std::move(key), std::to_string(value),
                                application::SettingsValueKind::integer);
}

huxerui::Task<SettingsResult<void>>
SQLiteSettingsStore::Remove(std::string key) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto removed =
      co_await database->ExecuteAsync("DELETE FROM settings WHERE key = ?", key);
  if (!removed)
    co_return std::unexpected(StoreError(removed.Error()));
  co_return SettingsResult<void>{};
}

huxerui::Task<SettingsResult<void>>
SQLiteSettingsStore::ClearLineCodeSettings() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto removed = co_await database->ExecuteAsync(
      "DELETE FROM settings WHERE key GLOB ? OR key GLOB ?",
      std::string{"@lineai_*"}, std::string{"@linecode_*"});
  if (!removed)
    co_return std::unexpected(StoreError(removed.Error()));
  co_return SettingsResult<void>{};
}

huxerui::Task<SettingsResult<
    std::map<std::string, std::string, std::less<>>>>
SQLiteSettingsStore::LineCodeSettings() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<
      std::pair<std::string, std::string>>(
      "SELECT key, value FROM settings WHERE key GLOB ? OR key GLOB ? "
      "ORDER BY key ASC",
      DecodeSetting, std::string{"@lineai_*"},
      std::string{"@linecode_*"});
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));

  std::map<std::string, std::string, std::less<>> values;
  for (auto &[key, value] : *rows)
    values.emplace(std::move(key), std::move(value));
  co_return values;
}

} // namespace linecode::infrastructure
