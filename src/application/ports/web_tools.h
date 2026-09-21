#pragma once

#include <cstdint>
#include <expected>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/tool_settings.h"

namespace linecode::application {

// Failures of the outbound web capability. The application layer maps these to
// ToolRegistryError without knowing whether the implementation uses HuxerUI
// HTTP, a platform client, or a fixture.
enum class WebToolErrorCode : std::uint8_t {
  invalid_arguments,
  not_configured,
  unsupported_url,
  transport,
  http_status,
  response_too_large,
  decode,
};

struct WebToolError final {
  WebToolErrorCode code{WebToolErrorCode::transport};
  std::string message;

  bool operator==(const WebToolError &) const = default;
};

template <class Value>
using WebToolResult = std::expected<Value, WebToolError>;

struct WebSearchResultItem final {
  std::string title;
  std::string url;
  std::string snippet;
  std::string published_date;

  bool operator==(const WebSearchResultItem &) const = default;
};

// A search call carries the already-loaded settings value so the gateway never
// reads persistence itself; configuration stays behind ToolSettingsService.
struct WebSearchRequest final {
  domain::WebSearchConfig config;
  std::string query;
  std::int32_t limit{5};

  bool operator==(const WebSearchRequest &) const = default;
};

struct WebFetchRequest final {
  std::string url;
  std::int32_t max_characters{12'000};

  bool operator==(const WebFetchRequest &) const = default;
};

// Narrow outbound-network port for the web tool group. Search provider wire
// details (endpoint, query parameter, API key placement) and page extraction
// remain implementation concerns of the adapter.
class WebToolsGateway {
public:
  virtual ~WebToolsGateway() = default;

  [[nodiscard]] virtual huxerui::Task<
      WebToolResult<std::vector<WebSearchResultItem>>>
  Search(WebSearchRequest request) = 0;

  [[nodiscard]] virtual huxerui::Task<WebToolResult<std::string>>
  FetchPage(WebFetchRequest request) = 0;
};

} // namespace linecode::application
