#pragma once

#include <cstddef>
#include <cstdint>
#include <expected>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/ports/web_tools.h"
#include "domain/tool_settings.h"

namespace linecode::infrastructure {

// Transport-free web search/fetch policy. Every function here is pure so the
// migrated legacy semantics (configuration gating, provider request shape,
// response normalization, URL policy and page-to-text extraction) can be
// verified without a network or an HTTP client.
//
// The web_search configuration contract replicated here comes from:
//   * data/src/main/java/cn/lineai/data/repository/
//     WebSearchConfigRepository.java
//     - :6   KEY_WEB_SEARCH_CONFIG = "@lineai_web_search_config"
//     - :21-23 get() reads that key and decodes it with
//              WebSearchConfig.fromJson (empty string -> defaultConfig)
//   * core-model/src/main/java/cn/lineai/model/WebSearchConfig.java
//     - :39-61 defaultConfig(provider): endpoint, query parameter and key
//              placement per provider
//     - :67-70 requiresApiKey(provider): only bing_rss_free is keyless
//     - :79-99 fromJson: unknown provider -> bing_rss_free, stored baseUrl
//              wins over the provider default, queryParam falls back to "q"
//   * feature-tool/src/main/java/cn/lineai/tool/builtin/WebSearchService.java
//     - :33-45 configuration gating for search
//     - :42/:69 legacy limit and maxChars clamps
// The C++ port reads the same "@lineai_web_search_config" value through
// ToolSettingsService (application/tool_settings_service.h:19-20) and
// infrastructure/tool_settings_codec.cpp:39-81.
enum class WebHttpMethod : std::uint8_t {
  get,
  post,
};

struct WebHttpRequestPlan final {
  std::string url;
  WebHttpMethod method{WebHttpMethod::get};
  std::vector<std::pair<std::string, std::string>> headers;
  std::string body;

  bool operator==(const WebHttpRequestPlan &) const = default;
};

// WebSearchConfig.requiresApiKey (core-model/.../WebSearchConfig.java:67-70):
// only the public Bing RSS endpoint works without a key.
[[nodiscard]] constexpr bool WebSearchRequiresApiKey(
    domain::WebSearchProvider provider) noexcept {
  return provider != domain::WebSearchProvider::bing_rss_free;
}

// WebSearchService.search gating (WebSearchService.java:34-41): a key-provider
// needs both the endpoint and the key, the free RSS provider only needs an
// endpoint when it is not the built-in default endpoint.
[[nodiscard]] std::expected<domain::WebSearchConfig, application::WebToolError>
ValidateWebSearchConfiguration(domain::WebSearchConfig config);

// Legacy WebSearchService.search: limit <= 0 means the default 5, then 1..10.
[[nodiscard]] std::int32_t
ClampWebSearchResultLimit(std::int32_t limit) noexcept;

// Legacy WebSearchService.fetchPage: maxChars <= 0 means the default 12000,
// then 1000..30000.
[[nodiscard]] std::int32_t
ClampWebFetchCharacters(std::int32_t max_characters) noexcept;

[[nodiscard]] std::expected<WebHttpRequestPlan, application::WebToolError>
BuildWebSearchRequest(const domain::WebSearchConfig &config,
                      std::string_view query, std::int32_t limit);

[[nodiscard]] std::expected<std::vector<application::WebSearchResultItem>,
                            application::WebToolError>
ParseWebSearchResponse(domain::WebSearchProvider provider,
                       std::string_view body);

// Legacy UrlPolicy.requireHttpOrLocalCleartextUrl, applied by the legacy
// SimpleHttpClient to every outbound web request: HTTPS everywhere, cleartext
// only for localhost, 127.0.0.1, 10.0.2.2, ::1 and private IPv4 literals.
[[nodiscard]] std::expected<std::string, application::WebToolError>
ValidateWebUrl(std::string_view url);

[[nodiscard]] std::string HtmlToPlainText(std::string_view html);
[[nodiscard]] std::string CompactWebText(std::string_view text);
[[nodiscard]] std::string TruncateWebText(std::string_view text,
                                          std::int32_t max_characters);

// Legacy BingRssSearchProvider used Locale.getDefault().toLanguageTag() for the
// Bing market parameter; the port has no platform locale service, so the
// process environment is the closest available source.
[[nodiscard]] std::string NormalizeLanguageTag(std::string_view value);
[[nodiscard]] std::string WebSearchMarket();

} // namespace linecode::infrastructure
