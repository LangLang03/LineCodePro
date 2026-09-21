#pragma once

#include <memory>

#include <huxerui/file.h>

#include "application/ports/settings_store.h"

namespace linecode::infrastructure {

class SQLiteSettingsStoreState;

namespace sqlite_settings_schema {

inline constexpr std::string_view create_table =
    "CREATE TABLE IF NOT EXISTS settings ("
    "key TEXT PRIMARY KEY,"
    "value TEXT NOT NULL,"
    "type TEXT NOT NULL DEFAULT 'string',"
    "updated_at INTEGER NOT NULL)";

inline constexpr std::string_view add_type_column =
    "ALTER TABLE settings ADD COLUMN type TEXT NOT NULL DEFAULT 'string'";
inline constexpr std::string_view add_updated_at_column =
    "ALTER TABLE settings ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0";

} // namespace sqlite_settings_schema

class SQLiteSettingsStore final : public application::AsyncSettingsStore {
public:
  explicit SQLiteSettingsStore(huxerui::File database_file);

  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  SetString(std::string key, std::string value) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  SetBoolean(std::string key, bool value) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  SetInteger(std::string key, std::int64_t value) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string key, bool fallback) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string key, std::int64_t fallback) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  Remove(std::string key) override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  ClearLineCodeSettings() override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override;

private:
  std::shared_ptr<SQLiteSettingsStoreState> state_;
};

} // namespace linecode::infrastructure
