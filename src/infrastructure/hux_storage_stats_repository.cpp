#include "infrastructure/hux_storage_stats_repository.h"

#include <cstdint>
#include <expected>
#include <iterator>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include <huxerui/sqlite.h>

namespace linecode::infrastructure {
namespace {

using application::StorageStatsError;
using domain::StorageCategoryStats;
using huxerui::File;
using huxerui::FileType;
using huxerui::sqlite::Database;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;

Result<std::int64_t> DecodeInteger(const RowView &row) {
  return row.Get<std::int64_t>(0);
}

StorageStatsError DatabaseError(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

huxerui::Task<std::expected<StorageCategoryStats, StorageStatsError>>
MeasureTree(const File &root) {
  const auto root_info = co_await root.StatAsync();
  if (!root_info.Succeeded()) {
    if (root_info.Error().code == huxerui::FileErrorCode::NotFound) {
      co_return StorageCategoryStats{};
    }
    co_return std::unexpected(
        StorageStatsError{.message = root_info.Error().message});
  }
  if (root_info.Value().type == FileType::File) {
    co_return StorageCategoryStats{.bytes = root_info.Value().size, .count = 1};
  }
  if (root_info.Value().type != FileType::Directory) {
    co_return StorageCategoryStats{};
  }

  auto children = co_await root.ListChildrenAsync();
  if (!children.Succeeded()) {
    co_return std::unexpected(
        StorageStatsError{.message = children.Error().message});
  }
  StorageCategoryStats total;
  for (const auto &child : children.Value()) {
    auto measured = co_await MeasureTree(child);
    if (!measured) {
      co_return std::unexpected(std::move(measured.error()));
    }
    total.bytes += measured->bytes;
    total.count += measured->count;
  }
  co_return total;
}

huxerui::Task<std::expected<std::uint64_t, StorageStatsError>>
QueryUnsigned(const Database &database, std::string sql) {
  auto rows = co_await database.QueryAsync<std::int64_t>(std::move(sql),
                                                         DecodeInteger);
  if (!rows) {
    co_return std::unexpected(DatabaseError(rows.Error()));
  }
  if (rows->empty() || rows->front() <= 0) {
    co_return std::uint64_t{};
  }
  co_return static_cast<std::uint64_t>(rows->front());
}

huxerui::Task<std::expected<std::unordered_set<std::string>, StorageStatsError>>
LoadTableNames(const Database &database) {
  auto rows = co_await database.QueryAsync<std::string>(
      "SELECT name FROM sqlite_master WHERE type = 'table'",
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!rows) {
    co_return std::unexpected(DatabaseError(rows.Error()));
  }
  co_return std::unordered_set<std::string>(
      std::make_move_iterator(rows->begin()), std::make_move_iterator(rows->end()));
}

bool HasTable(const std::unordered_set<std::string> &tables,
              std::string_view table) {
  return tables.contains(std::string{table});
}

huxerui::Task<std::expected<std::uint64_t, StorageStatsError>>
TextLength(const Database &database,
           const std::unordered_set<std::string> &tables,
           std::string_view table, std::string_view columns) {
  if (!HasTable(tables, table)) {
    co_return std::uint64_t{};
  }
  co_return co_await QueryUnsigned(
      database, "SELECT COALESCE(SUM(" + std::string{columns} + "), 0) FROM " +
                    std::string{table});
}

huxerui::Task<std::expected<std::uint64_t, StorageStatsError>>
RecordCount(const Database &database,
            const std::unordered_set<std::string> &tables,
            std::string_view table) {
  if (!HasTable(tables, table)) {
    co_return std::uint64_t{};
  }
  co_return co_await QueryUnsigned(
      database, "SELECT COUNT(*) FROM " + std::string{table});
}

} // namespace

HuxStorageStatsRepository::HuxStorageStatsRepository(
    File database_file, std::vector<File> config_roots, File home_root)
    : database_file_(std::move(database_file)),
      config_roots_(std::move(config_roots)), home_root_(std::move(home_root)) {}

huxerui::Task<std::expected<domain::StorageStats, StorageStatsError>>
HuxStorageStatsRepository::Load() {
  domain::StorageStats stats;
  auto opened = co_await Database::OpenAsync(database_file_);
  if (!opened) {
    co_return std::unexpected(DatabaseError(opened.Error()));
  }
  auto tables = co_await LoadTableNames(*opened);
  if (!tables) {
    co_return std::unexpected(std::move(tables.error()));
  }

  auto diff_bytes = co_await TextLength(
      *opened, *tables, "diff_records",
      "COALESCE(length(old_content), 0) + COALESCE(length(new_content), 0)");
  auto diff_count = co_await RecordCount(*opened, *tables, "diff_records");
  if (!diff_bytes || !diff_count) {
    co_return std::unexpected(!diff_bytes ? std::move(diff_bytes.error())
                                             : std::move(diff_count.error()));
  }
  stats.diff_cache = {.bytes = *diff_bytes, .count = *diff_count};

  std::uint64_t chat_bytes{};
  const struct TextSource final {
    std::string_view table;
    std::string_view columns;
  } sources[]{
      {"messages", "COALESCE(length(content), 0) + "
                   "COALESCE(length(reasoning_content), 0)"},
      {"message_text_chunks", "COALESCE(length(content), 0)"},
      {"tool_calls", "COALESCE(length(arguments), 0)"},
      {"tool_results", "COALESCE(length(content), 0)"},
  };
  for (const auto &source : sources) {
    auto measured =
        co_await TextLength(*opened, *tables, source.table, source.columns);
    if (!measured) {
      co_return std::unexpected(std::move(measured.error()));
    }
    chat_bytes += *measured;
  }
  auto database_info = co_await database_file_.StatAsync();
  if (database_info.Succeeded() &&
      database_info.Value().type == FileType::File) {
    chat_bytes += database_info.Value().size;
  }
  auto chat_count = co_await RecordCount(*opened, *tables, "conversations");
  if (!chat_count) {
    co_return std::unexpected(std::move(chat_count.error()));
  }
  stats.chat = {.bytes = chat_bytes, .count = *chat_count};

  for (const auto &root : config_roots_) {
    auto measured = co_await MeasureTree(root);
    if (!measured) {
      co_return std::unexpected(std::move(measured.error()));
    }
    stats.config.bytes += measured->bytes;
    stats.config.count += measured->count;
  }
  auto home = co_await MeasureTree(home_root_);
  if (!home) {
    co_return std::unexpected(std::move(home.error()));
  }
  stats.home = *home;
  co_return stats;
}

} // namespace linecode::infrastructure
