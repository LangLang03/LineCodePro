#include "application/error_log_service.h"

#include <stdexcept>
#include <string>

#include "application/error_log_redactor.h"

namespace linecode::application {
namespace {

ErrorLogResult<void> PlatformFailure(std::string detail) {
  return std::unexpected(ErrorLogError{
      .code = ErrorLogErrorCode::platform_failed,
      .detail = std::move(detail),
  });
}

} // namespace

ErrorLogService::ErrorLogService(
    std::shared_ptr<ErrorLogStore> store,
    std::shared_ptr<ErrorLogPlatformActions> platform_actions)
    : store_(std::move(store)), platform_actions_(std::move(platform_actions)) {
  if (!store_ || !platform_actions_) {
    throw std::invalid_argument("ErrorLogService dependencies must not be null");
  }
}

huxerui::Task<ErrorLogResult<std::vector<domain::ErrorLogEntry>>>
ErrorLogService::Refresh() {
  co_return co_await store_->List();
}

huxerui::Task<ErrorLogResult<std::vector<domain::ErrorLogEntry>>>
ErrorLogService::ClearAndRefresh() {
  auto cleared = co_await store_->Clear();
  if (!cleared) {
    co_return std::unexpected(cleared.error());
  }
  co_return co_await store_->List();
}

huxerui::Task<ErrorLogResult<void>>
ErrorLogService::Copy(std::string_view entry_id) {
  auto content = co_await store_->Read(entry_id);
  if (!content) {
    co_return std::unexpected(content.error());
  }
  const std::string safe = RedactErrorLogText(*content);
  if (!platform_actions_->CopyText(safe)) {
    co_return PlatformFailure("clipboard rejected error log text");
  }
  co_return ErrorLogResult<void>{};
}

huxerui::Task<ErrorLogResult<void>>
ErrorLogService::Open(std::string_view entry_id, std::string_view title,
                      std::string_view chooser_title) {
  auto content = co_await store_->Read(entry_id);
  if (!content) {
    co_return std::unexpected(content.error());
  }
  const std::string safe = RedactErrorLogText(*content);
  if (!platform_actions_->OpenText(title, safe, chooser_title)) {
    co_return PlatformFailure("platform rejected error log text");
  }
  co_return ErrorLogResult<void>{};
}

} // namespace linecode::application
