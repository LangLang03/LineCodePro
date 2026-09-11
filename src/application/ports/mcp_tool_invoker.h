#pragma once

#include <expected>
#include <string>

#include <huxerui/task.h>

#include "domain/extension_config.h"

namespace linecode::application {

enum class McpToolInvocationErrorCode {
  invalid_arguments,
  transport,
  response_too_large,
  http_status,
};

struct McpToolInvocationError final {
  McpToolInvocationErrorCode code{McpToolInvocationErrorCode::transport};
  std::string message;

  bool operator==(const McpToolInvocationError &) const = default;
};

struct McpToolInvocationResult final {
  std::string content;
  bool error{};

  bool operator==(const McpToolInvocationResult &) const = default;
};

class McpToolInvoker {
public:
  virtual ~McpToolInvoker() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<McpToolInvocationResult, McpToolInvocationError>>
  Invoke(domain::McpExtension extension, domain::McpToolSummary tool,
         std::string arguments_json) = 0;
};

} // namespace linecode::application
