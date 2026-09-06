#pragma once

#include <memory>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/error_log_store.h"

namespace linecode::application {

class ErrorLogService final {
public:
  ErrorLogService(std::shared_ptr<ErrorLogStore> store,
                  std::shared_ptr<ErrorLogPlatformActions> platform_actions);

  [[nodiscard]] huxerui::Task<
      ErrorLogResult<std::vector<domain::ErrorLogEntry>>>
  Refresh();
  [[nodiscard]] huxerui::Task<
      ErrorLogResult<std::vector<domain::ErrorLogEntry>>>
  ClearAndRefresh();
  [[nodiscard]] huxerui::Task<ErrorLogResult<void>>
  Copy(std::string_view entry_id);
  [[nodiscard]] huxerui::Task<ErrorLogResult<void>>
  Open(std::string_view entry_id, std::string_view title,
       std::string_view chooser_title);

private:
  std::shared_ptr<ErrorLogStore> store_;
  std::shared_ptr<ErrorLogPlatformActions> platform_actions_;
};

} // namespace linecode::application
