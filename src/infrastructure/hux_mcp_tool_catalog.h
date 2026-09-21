#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/mcp_tool_catalog.h"

namespace linecode::infrastructure {

class HuxMcpToolCatalog final : public application::McpToolCatalog {
public:
  explicit HuxMcpToolCatalog(std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<std::expected<std::vector<domain::McpToolSummary>,
                                            application::McpToolCatalogError>>
  Query(std::string url,
        std::vector<domain::McpRequestHeader> request_headers) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
