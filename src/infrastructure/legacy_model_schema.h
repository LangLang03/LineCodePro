#pragma once

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

} // namespace linecode::infrastructure::legacy_model_schema
