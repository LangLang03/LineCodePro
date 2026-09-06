#pragma once

#include <expected>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "domain/error_log.h"

namespace linecode::application {

enum class ErrorLogErrorCode {
  unavailable,
  invalid_entry,
  not_found,
  too_large,
  invalid_text,
  read_failed,
  clear_failed,
  platform_failed,
};

struct ErrorLogError final {
  ErrorLogErrorCode code{ErrorLogErrorCode::unavailable};
  // Diagnostic detail for tests/telemetry. Presentation uses localized generic
  // messages so platform paths and provider details are never disclosed.
  std::string detail;

  bool operator==(const ErrorLogError &) const = default;
};

template <class Value>
using ErrorLogResult = std::expected<Value, ErrorLogError>;

class ErrorLogStore {
public:
  virtual ~ErrorLogStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      ErrorLogResult<std::vector<domain::ErrorLogEntry>>>
  List() = 0;
  [[nodiscard]] virtual huxerui::Task<ErrorLogResult<std::string>>
  Read(std::string_view entry_id) = 0;
  [[nodiscard]] virtual huxerui::Task<ErrorLogResult<void>> Clear() = 0;
};

class ErrorLogPlatformActions {
public:
  virtual ~ErrorLogPlatformActions() = default;

  virtual bool CopyText(std::string_view text) = 0;
  virtual bool OpenText(std::string_view title, std::string_view text,
                        std::string_view chooser_title) = 0;
};

} // namespace linecode::application
