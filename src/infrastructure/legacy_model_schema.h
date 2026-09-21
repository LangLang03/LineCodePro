#pragma once

#include <array>
#include <string_view>

namespace linecode::infrastructure::legacy_model_schema {

inline constexpr std::string_view create_table = R"sql(
CREATE TABLE IF NOT EXISTS model_configs (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  protocol_type TEXT NOT NULL,
  provider_label TEXT NOT NULL,
  base_url TEXT,
  api_key TEXT,
  model_id TEXT NOT NULL,
  tool_call_limit INTEGER NOT NULL DEFAULT 200,
  compression_model_enabled INTEGER NOT NULL DEFAULT 0,
  compression_model_auto INTEGER NOT NULL DEFAULT 1,
  compression_model_id TEXT,
  context_size INTEGER NOT NULL DEFAULT 0,
  selected INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
)
)sql";

inline constexpr std::string_view clear_selection = R"sql(
UPDATE model_configs SET selected = 0
)sql";

inline constexpr std::string_view select_id = R"sql(
UPDATE model_configs SET selected = 1, updated_at = ? WHERE id = ?
)sql";

inline constexpr std::string_view selected_id = R"sql(
SELECT id
FROM model_configs
WHERE selected = 1
ORDER BY updated_at DESC
LIMIT 1
)sql";

// This fallback deliberately mirrors the legacy Java repository. In
// particular, non-standard INTEGER selected values retain SQLite's numeric
// DESC ordering instead of being normalized to booleans.
inline constexpr std::string_view fallback_model_id = R"sql(
SELECT id
FROM model_configs
ORDER BY selected DESC, updated_at DESC
LIMIT 1
)sql";

struct ColumnMigration final {
  std::string_view column;
  std::string_view statement;
};

// Older Java releases created model_configs before these form fields existed.
// CREATE TABLE IF NOT EXISTS does not upgrade such a table, so every adapter
// opening the shared database applies this declarative compatibility list.
inline constexpr std::array column_migrations{
    ColumnMigration{"tool_call_limit",
                    "ALTER TABLE model_configs ADD COLUMN tool_call_limit "
                    "INTEGER NOT NULL DEFAULT 200"},
    ColumnMigration{
        "compression_model_enabled",
        "ALTER TABLE model_configs ADD COLUMN compression_model_enabled "
        "INTEGER NOT NULL DEFAULT 0"},
    ColumnMigration{
        "compression_model_auto",
        "ALTER TABLE model_configs ADD COLUMN compression_model_auto "
        "INTEGER NOT NULL DEFAULT 1"},
    ColumnMigration{
        "compression_model_id",
        "ALTER TABLE model_configs ADD COLUMN compression_model_id TEXT"},
    ColumnMigration{
        "context_size",
        "ALTER TABLE model_configs ADD COLUMN context_size INTEGER NOT NULL "
        "DEFAULT 0"},
};

} // namespace linecode::infrastructure::legacy_model_schema
