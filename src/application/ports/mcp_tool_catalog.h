#pragma once

#include <expected>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/extension_config.h"

namespace linecode::application {

struct McpToolCatalogError final {
  std::string message;

  bool operator==(const McpToolCatalogError &) const = default;
};

class McpToolCatalog {
public:
  virtual ~McpToolCatalog() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<std::vector<domain::McpToolSummary>, McpToolCatalogError>>
  Query(std::string url,
        std::vector<domain::McpRequestHeader> request_headers) = 0;
};

} // namespace linecode::application
