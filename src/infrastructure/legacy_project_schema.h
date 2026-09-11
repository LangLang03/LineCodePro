#pragma once

#include <string_view>

namespace huxerui::sqlite {
class Transaction;
template <typename T> class Result;
} // namespace huxerui::sqlite

namespace linecode::infrastructure::legacy_project_schema {

inline constexpr std::string_view selected_local_project_key =
    "@linecode_selected_project_local";

inline constexpr std::string_view create_settings = R"sql(
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'string',
  updated_at INTEGER NOT NULL
)
)sql";

inline constexpr std::string_view create_projects = R"sql(
CREATE TABLE IF NOT EXISTS projects (
  id TEXT PRIMARY KEY,
  label TEXT NOT NULL,
  path TEXT NOT NULL,
  source TEXT NOT NULL,
  description TEXT,
  selected INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
)
)sql";

[[nodiscard]] huxerui::sqlite::Result<void>
Ensure(huxerui::sqlite::Transaction &transaction);

} // namespace linecode::infrastructure::legacy_project_schema
