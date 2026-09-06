#include "infrastructure/hux_error_log_store.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <ctime>
#include <expected>
#include <string>
#include <utility>
#include <vector>

#include "application/error_log_policy.h"

namespace linecode::infrastructure {
namespace {

using application::ErrorLogError;
using application::ErrorLogErrorCode;
using application::ErrorLogResult;

// Avoid buffering attacker-controlled files without a practical bound. The
// application redactor applies its tighter 1 MiB disclosure bound afterwards.
constexpr std::uint64_t kMaximumStoredLogBytes = 2U << 20U;

ErrorLogError TranslateFileError(const huxerui::FileError &error,
                                 ErrorLogErrorCode fallback) {
  ErrorLogErrorCode code = fallback;
  switch (error.code) {
  case huxerui::FileErrorCode::NotFound:
    code = ErrorLogErrorCode::not_found;
    break;
  case huxerui::FileErrorCode::TooLarge:
    code = ErrorLogErrorCode::too_large;
    break;
  case huxerui::FileErrorCode::InvalidEncoding:
    code = ErrorLogErrorCode::invalid_text;
    break;
  default:
    break;
  }
  // Deliberately discard FileError::message: it commonly contains a private
  // application path and must not cross the repository boundary.
  return ErrorLogError{.code = code, .detail = "file operation failed"};
}

std::int64_t Milliseconds(std::chrono::system_clock::time_point value) {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             value.time_since_epoch())
      .count();
}

std::string DisplayTime(std::chrono::system_clock::time_point value) {
  const std::time_t raw = std::chrono::system_clock::to_time_t(value);
  std::tm local{};
#if defined(_WIN32)
  if (localtime_s(&local, &raw) != 0) {
    return {};
  }
#else
  if (localtime_r(&raw, &local) == nullptr) {
    return {};
  }
#endif
  char buffer[20]{};
  const int written = std::snprintf(
      buffer, sizeof(buffer), "%04d-%02d-%02d %02d:%02d:%02d",
      local.tm_year + 1900, local.tm_mon + 1, local.tm_mday, local.tm_hour,
      local.tm_min, local.tm_sec);
  return written == 19 ? std::string(buffer, 19) : std::string{};
}

} // namespace

HuxErrorLogStore::HuxErrorLogStore(huxerui::File application_data_directory)
    : directory_(application_data_directory.Child("error_logs")) {}

huxerui::Task<
    ErrorLogResult<std::vector<domain::ErrorLogEntry>>>
HuxErrorLogStore::List() {
  if (!co_await directory_.CreateDirectoriesAsync()) {
    co_return std::unexpected(ErrorLogError{
        .code = ErrorLogErrorCode::unavailable,
        .detail = "error log directory is unavailable",
    });
  }
  auto children = co_await directory_.ListChildrenAsync();
  if (!children.Succeeded()) {
    co_return std::unexpected(TranslateFileError(
        children.Error(), ErrorLogErrorCode::read_failed));
  }

  std::vector<domain::ErrorLogEntry> entries;
  entries.reserve(children.Value().size());
  for (const huxerui::File &child : children.Value()) {
    const std::string name = child.Name();
    if (!application::IsValidErrorLogEntryId(name)) {
      continue;
    }
    auto info = co_await child.StatAsync();
    if (!info.Succeeded() || info.Value().type != huxerui::FileType::File) {
      continue;
    }
    const auto modified = info.Value().modified_at.value_or(
        std::chrono::system_clock::time_point{});
    entries.push_back(domain::ErrorLogEntry{
        .id = name,
        .title = name,
        .subtitle = DisplayTime(modified),
        .timestamp_millis = Milliseconds(modified),
    });
  }
  std::ranges::sort(entries, std::greater{},
                    &domain::ErrorLogEntry::timestamp_millis);
  co_return entries;
}

huxerui::Task<ErrorLogResult<std::string>>
HuxErrorLogStore::Read(std::string_view entry_id) {
  if (!application::IsValidErrorLogEntryId(entry_id)) {
    co_return std::unexpected(ErrorLogError{
        .code = ErrorLogErrorCode::invalid_entry,
        .detail = "invalid error log identity",
    });
  }
  const huxerui::File file = directory_.Child(entry_id);
  auto info = co_await file.StatAsync();
  if (!info.Succeeded()) {
    co_return std::unexpected(
        TranslateFileError(info.Error(), ErrorLogErrorCode::read_failed));
  }
  if (info.Value().type != huxerui::FileType::File) {
    co_return std::unexpected(ErrorLogError{
        .code = ErrorLogErrorCode::not_found,
        .detail = "error log is not a regular file",
    });
  }
  if (info.Value().size > kMaximumStoredLogBytes) {
    co_return std::unexpected(ErrorLogError{
        .code = ErrorLogErrorCode::too_large,
        .detail = "error log exceeds the safe read limit",
    });
  }
  auto content = co_await file.ReadStringAsync();
  if (!content.Succeeded()) {
    co_return std::unexpected(TranslateFileError(
        content.Error(), ErrorLogErrorCode::read_failed));
  }
  co_return std::move(content).Value();
}

huxerui::Task<ErrorLogResult<void>> HuxErrorLogStore::Clear() {
  if (!co_await directory_.CreateDirectoriesAsync()) {
    co_return std::unexpected(ErrorLogError{
        .code = ErrorLogErrorCode::unavailable,
        .detail = "error log directory is unavailable",
    });
  }
  auto children = co_await directory_.ListChildrenAsync();
  if (!children.Succeeded()) {
    co_return std::unexpected(TranslateFileError(
        children.Error(), ErrorLogErrorCode::clear_failed));
  }
  bool all_deleted = true;
  for (const huxerui::File &child : children.Value()) {
    if (!application::IsValidErrorLogEntryId(child.Name())) {
      continue;
    }
    auto info = co_await child.StatAsync();
    if (!info.Succeeded() || info.Value().type != huxerui::FileType::File) {
      continue;
    }
    all_deleted = (co_await child.DeleteAsync()) && all_deleted;
  }
  if (!all_deleted) {
    co_return std::unexpected(ErrorLogError{
        .code = ErrorLogErrorCode::clear_failed,
        .detail = "one or more error logs could not be deleted",
    });
  }
  co_return ErrorLogResult<void>{};
}

} // namespace linecode::infrastructure
