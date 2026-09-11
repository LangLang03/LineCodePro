#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/mcp_tool_invoker.h"

namespace linecode::infrastructure {

class HuxMcpToolInvoker final : public application::McpToolInvoker {
public:
  explicit HuxMcpToolInvoker(std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<
      std::expected<application::McpToolInvocationResult,
                    application::McpToolInvocationError>>
  Invoke(domain::McpExtension extension, domain::McpToolSummary tool,
         std::string arguments_json) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
