#include "infrastructure/sqlite_diff_store.h"

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <random>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/sqlite.h>

#include "infrastructure/archive_json.h"
#include "infrastructure/legacy_conversation_schema.h"

namespace linecode::infrastructure {
namespace {

using application::DiffRevertResult;
using huxerui::sqlite::Database;
using huxerui::sqlite::Error;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;
namespace json = archive_json;

// Verbatim legacy messages from `DiffRepository.revertDiff`.
constexpr std::string_view kNotFound = "Specified diff record not found";
constexpr std::string_view kAlreadyReverted = "This change has been reverted";
constexpr std::string_view kLaterChangePending =
    "Please revert subsequent changes to this file first";
constexpr std::string_view kReady = "Ready to revert";

constexpr std::string_view kColumns =
    "id, file_path, old_content, new_content, old_exists, timestamp, reverted, "
    "raw_json";

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

// Base-36 rendering of `Long.toString(value, 36)`, which uses lowercase
// digits for values 10-35.
std::string ToBase36(std::uint64_t value) {
  constexpr std::string_view digits = "0123456789abcdefghijklmnopqrstuvwxyz";
  if (value == 0)
    return "0";
  std::string text;
  while (value != 0) {
    text.push_back(digits[value % 36U]);
    value /= 36U;
  }
  std::reverse(text.begin(), text.end());
  return text;
}

// `Math.abs(random.nextLong())` over the full bit pattern, including
// Long.MIN_VALUE which stays negative in Java and therefore yields a leading
// '-'. Reproduced so identifiers keep the legacy alphabet and width.
std::string RandomSuffix(const std::uint64_t bits) {
  const auto magnitude = static_cast<std::uint64_t>(
      bits < (std::uint64_t{1} << 63U) ? -static_cast<std::int64_t>(bits)
                                       : static_cast<std::int64_t>(bits));
  const bool negative = magnitude > static_cast<std::uint64_t>(INT64_MAX);
  return negative ? "-" + ToBase36(magnitude) : ToBase36(magnitude);
}

std::string EscapeJson(std::string_view text) {
  std::string escaped;
  escaped.reserve(text.size() + 2U);
  for (const char character : text) {
    switch (character) {
    case '"':
      escaped += "\\\"";
      break;
    case '\\':
      escaped += "\\\\";
      break;
    case '\n':
      escaped += "\\n";
      break;
    case '\r':
      escaped += "\\r";
      break;
    case '\t':
      escaped += "\\t";
      break;
    case '\b':
      escaped += "\\b";
      break;
    case '\f':
      escaped += "\\f";
      break;
    default:
      if (static_cast<unsigned char>(character) < 0x20U) {
        constexpr std::string_view hex = "0123456789abcdef";
        const auto byte = static_cast<unsigned char>(character);
        escaped += "\\u00";
        escaped.push_back(hex[byte >> 4U]);
        escaped.push_back(hex[byte & 0x0FU]);
      } else {
        escaped.push_back(character);
      }
      break;
    }
  }
  return escaped;
}

// `JSONObject.toString()` for the two review keys written by `setReview`.
std::string ReviewRawJson(std::string_view state, std::string_view message) {
  std::string text = "{\"review_state\":\"";
  text += EscapeJson(state);
  text += "\",\"review_message\":\"";
  text += EscapeJson(message);
  text += "\"}";
  return text;
}

Result<std::string> OptionalText(const RowView &row, const std::size_t column) {
  auto value = row.Get<std::optional<std::string>>(column);
  if (!value)
    return value.Error();
  return value->value_or("");
}

// Mirrors `DiffRepository.readRecord`: a missing or malformed `raw_json`
// decodes to empty review fields instead of failing the read.
Result<domain::DiffRecord> Decode(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id)
    return id.Error();
  auto file_path = row.Get<std::string>(1);
  if (!file_path)
    return file_path.Error();
  auto old_content = OptionalText(row, 2);
  if (!old_content)
    return old_content.Error();
  auto new_content = OptionalText(row, 3);
  if (!new_content)
    return new_content.Error();
  auto old_exists = row.Get<bool>(4);
  if (!old_exists)
    return old_exists.Error();
  auto timestamp = row.Get<std::int64_t>(5);
  if (!timestamp)
    return timestamp.Error();
  auto reverted = row.Get<bool>(6);
  if (!reverted)
    return reverted.Error();
  auto raw_json = OptionalText(row, 7);
  if (!raw_json)
    return raw_json.Error();

  domain::DiffRecord record{
      .id = std::move(*id),
      .file_path = std::move(*file_path),
      .old_content = std::move(*old_content),
      .new_content = std::move(*new_content),
      .old_exists = *old_exists,
      .timestamp = *timestamp,
      .reverted = *reverted,
      .review_state = std::string{},
      .review_message = std::string{},
  };
  if (const auto parsed = json::Parse(*raw_json); parsed) {
    if (const auto *object = json::AsObject(&*parsed)) {
      if (const auto *value = json::AsString(json::Find(*object, "review_state")))
        record.review_state = *value;
      if (const auto *value =
              json::AsString(json::Find(*object, "review_message")))
        record.review_message = *value;
    }
  }
  return record;
}

Result<void> EnsureDiffTable(Transaction &transaction) {
  auto created =
      transaction.Execute(std::string{legacy_schema::create_diff_records});
  if (!created)
    return created.Error();
  return {};
}

} // namespace

SqliteDiffStore::SqliteDiffStore(huxerui::File database_file)
    : database_file_(std::move(database_file)) {}

huxerui::Task<std::optional<Database>> SqliteDiffStore::Open() {
  if (database_)
    co_return *database_;
  auto opened = co_await Database::OpenAsync(
      database_file_,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened)
    co_return std::nullopt;
  auto schema = co_await opened->TransactionAsync(
      [](Transaction &transaction) { return EnsureDiffTable(transaction); });
  if (!schema)
    co_return std::nullopt;
  database_ = *opened;
  co_return *database_;
}

huxerui::Task<domain::DiffRecord>
SqliteDiffStore::Record(std::string file_path, std::string old_content,
                        std::string new_content, const bool old_exists) {
  const auto now = NowMilliseconds();
  std::string suffix;
  {
    const std::lock_guard lock(random_mutex_);
    suffix = RandomSuffix(random_());
  }
  domain::DiffRecord record{
      .id = std::to_string(now) + "_" + suffix,
      .file_path = std::move(file_path),
      .old_content = std::move(old_content),
      .new_content = std::move(new_content),
      .old_exists = old_exists,
      .timestamp = now,
      .reverted = false,
      .review_state = std::string{},
      .review_message = std::string{},
  };

  auto database = co_await Open();
  if (!database)
    co_return domain::DiffRecord{};
  // Exactly the legacy `insertOrReplace`: a conflicting identifier replaces the
  // whole row, including any review state previously carried in `raw_json`.
  const auto inserted = co_await database->ExecuteAsync(
      "INSERT OR REPLACE INTO diff_records (id, file_path, old_content, "
      "new_content, old_exists, timestamp, reverted, raw_json) VALUES (?, ?, ?, "
      "?, ?, ?, ?, ?)",
      record.id, record.file_path, record.old_content, record.new_content,
      record.old_exists, record.timestamp, false, std::string{});
  if (!inserted)
    co_return domain::DiffRecord{};
  co_return record;
}

huxerui::Task<std::optional<domain::DiffRecord>>
SqliteDiffStore::Find(std::string diff_id) {
  // `getDiff` returns null for a null or empty identifier.
  if (diff_id.empty())
    co_return std::nullopt;
  auto database = co_await Open();
  if (!database)
    co_return std::nullopt;
  auto rows = co_await database->QueryAsync<domain::DiffRecord>(
      "SELECT " + std::string{kColumns} + " FROM diff_records WHERE id = ?",
      Decode, diff_id);
  if (!rows || rows->empty())
    co_return std::nullopt;
  co_return rows->front();
}

huxerui::Task<std::vector<domain::DiffRecord>>
SqliteDiffStore::Chain(std::string file_path) {
  if (file_path.empty())
    co_return std::vector<domain::DiffRecord>{};
  auto database = co_await Open();
  if (!database)
    co_return std::vector<domain::DiffRecord>{};
  auto rows = co_await database->QueryAsync<domain::DiffRecord>(
      "SELECT " + std::string{kColumns} +
          " FROM diff_records WHERE file_path = ? ORDER BY timestamp ASC",
      Decode, file_path);
  if (!rows)
    co_return std::vector<domain::DiffRecord>{};
  co_return std::move(*rows);
}

huxerui::Task<DiffRevertResult>
SqliteDiffStore::CheckRevert(std::string diff_id) {
  if (diff_id.empty())
    co_return DiffRevertResult{.success = false,
                                .message = std::string{kNotFound},
                                .record = std::nullopt};

  auto target = co_await Find(diff_id);
  if (!target)
    co_return DiffRevertResult{.success = false,
                                .message = std::string{kNotFound},
                                .record = std::nullopt};
  if (target->reverted) {
    co_return DiffRevertResult{.success = true,
                                .message = std::string{kAlreadyReverted},
                                .record = std::nullopt};
  }

  auto chain = co_await Chain(target->file_path);
  std::size_t target_index = chain.size();
  for (std::size_t index = 0; index < chain.size(); ++index) {
    if (chain[index].id == target->id) {
      target_index = index;
      break;
    }
  }
  if (target_index == chain.size())
    co_return DiffRevertResult{.success = false,
                                .message = std::string{kNotFound},
                                .record = std::nullopt};
  for (std::size_t index = target_index + 1U; index < chain.size(); ++index) {
    if (!chain[index].reverted)
      co_return DiffRevertResult{.success = false,
                                  .message = std::string{kLaterChangePending},
                                .record = std::nullopt};
  }
  co_return DiffRevertResult{.success = true,
                              .message = std::string{kReady},
                              .record = *target};
}

huxerui::Task<void> SqliteDiffStore::MarkReverted(std::string diff_id) {
  if (diff_id.empty())
    co_return;
  auto database = co_await Open();
  if (!database)
    co_return;
  co_await database->ExecuteAsync(
      "UPDATE diff_records SET reverted = 1 WHERE id = ?", diff_id);
  co_return;
}

huxerui::Task<void> SqliteDiffStore::SetReview(std::string diff_id,
                                               std::string state,
                                               std::string message) {
  // `setReview` ignores an empty identifier without touching the row.
  if (diff_id.empty())
    co_return;
  auto database = co_await Open();
  if (!database)
    co_return;
  co_await database->ExecuteAsync(
      "UPDATE diff_records SET raw_json = ? WHERE id = ?",
      ReviewRawJson(state, message), diff_id);
  co_return;
}

} // namespace linecode::infrastructure
