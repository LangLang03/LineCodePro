#pragma once

#include <mutex>
#include <optional>
#include <random>
#include <string>
#include <vector>

#include <huxerui/file.h>
#include <huxerui/sqlite.h>
#include <huxerui/task.h>

#include "application/ports/diff_store.h"

namespace linecode::infrastructure {

// SQLite adapter for the legacy `diff_records` table, replacing
// `DiffRepository`. The table itself is shared with the conversation store, so
// this adapter only issues `CREATE TABLE IF NOT EXISTS` for its own columns and
// never alters the existing schema.
class SqliteDiffStore final : public application::DiffStore {
public:
  explicit SqliteDiffStore(huxerui::File database_file);

  [[nodiscard]] huxerui::Task<domain::DiffRecord>
  Record(std::string file_path, std::string old_content, std::string new_content,
         bool old_exists) override;

  [[nodiscard]] huxerui::Task<std::optional<domain::DiffRecord>>
  Find(std::string diff_id) override;

  [[nodiscard]] huxerui::Task<std::vector<domain::DiffRecord>>
  Chain(std::string file_path) override;

  [[nodiscard]] huxerui::Task<application::DiffRevertResult>
  CheckRevert(std::string diff_id) override;

  [[nodiscard]] huxerui::Task<void> MarkReverted(std::string diff_id) override;

  [[nodiscard]] huxerui::Task<void> SetReview(std::string diff_id,
                                              std::string state,
                                              std::string message) override;

private:
  // Opens (once) and returns the connection, ensuring the diff table exists.
  // An absent value means the connection or its schema could not be prepared,
  // in which case the caller reports the legacy "not found"/no-op outcome.
  [[nodiscard]] huxerui::Task<std::optional<huxerui::sqlite::Database>> Open();

  huxerui::File database_file_;
  std::optional<huxerui::sqlite::Database> database_;
  // One engine per store; guarded by `random_mutex_` when tasks interleave.
  std::mt19937_64 random_{std::random_device{}()};
  std::mutex random_mutex_;
};

} // namespace linecode::infrastructure
