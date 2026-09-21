#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace linecode::domain {

enum class WebSearchProvider : std::uint8_t {
  bing_rss_free,
  tavily,
  brave_search,
  serp_api,
  bing_search,
  custom,
};

struct WebSearchProviderDescriptor final {
  WebSearchProvider provider;
  std::string_view storage_name;
  bool fields_visible;
  std::string_view default_base_url;
  std::string_view default_query_param;
  std::string_view default_api_key_header;
  std::string_view default_api_key_param;

  bool operator==(const WebSearchProviderDescriptor &) const = default;
};

inline constexpr std::array web_search_provider_catalog{
    WebSearchProviderDescriptor{
        WebSearchProvider::bing_rss_free, "bing_rss_free", false,
        "https://www.bing.com/search?format=rss", "q", "", ""},
    WebSearchProviderDescriptor{
        WebSearchProvider::tavily, "tavily", true,
        "https://api.tavily.com/search", "query", "Authorization", ""},
    WebSearchProviderDescriptor{
        WebSearchProvider::brave_search, "brave", true,
        "https://api.search.brave.com/res/v1/web/search", "q",
        "X-Subscription-Token", ""},
    WebSearchProviderDescriptor{
        WebSearchProvider::serp_api, "serpapi", true,
        "https://serpapi.com/search.json", "q", "", "api_key"},
    WebSearchProviderDescriptor{
        WebSearchProvider::bing_search, "bing", true,
        "https://api.bing.microsoft.com/v7.0/search", "q",
        "Ocp-Apim-Subscription-Key", ""},
    WebSearchProviderDescriptor{
        WebSearchProvider::custom, "custom", true, "", "q", "Authorization",
        ""},
};

[[nodiscard]] constexpr const WebSearchProviderDescriptor &
WebSearchProviderInfo(WebSearchProvider provider) noexcept {
  for (const auto &descriptor : web_search_provider_catalog) {
    if (descriptor.provider == provider)
      return descriptor;
  }
  return web_search_provider_catalog.front();
}

[[nodiscard]] constexpr std::string_view
WebSearchProviderStorageName(WebSearchProvider provider) noexcept {
  return WebSearchProviderInfo(provider).storage_name;
}

[[nodiscard]] constexpr std::optional<WebSearchProvider>
ParseWebSearchProvider(std::string_view value) noexcept {
  for (const auto &descriptor : web_search_provider_catalog) {
    if (descriptor.storage_name == value)
      return descriptor.provider;
  }
  return std::nullopt;
}

struct WebSearchConfig final {
  WebSearchProvider provider{WebSearchProvider::bing_rss_free};
  std::string base_url;
  std::string api_key;
  std::string model;
  std::string query_param;
  std::string api_key_header;
  std::string api_key_param;

  bool operator==(const WebSearchConfig &) const = default;
};

[[nodiscard]] WebSearchConfig
DefaultWebSearchConfig(WebSearchProvider provider);

[[nodiscard]] WebSearchConfig NormalizeWebSearchConfig(WebSearchConfig config);

[[nodiscard]] std::string NormalizeToolModelId(std::string model_id);

[[nodiscard]] std::string_view
EffectiveWebSearchQueryParam(const WebSearchConfig &config) noexcept;

[[nodiscard]] inline WebSearchConfig DefaultWebSearchConfig() {
  return DefaultWebSearchConfig(WebSearchProvider::bing_rss_free);
}

[[nodiscard]] constexpr bool
WebSearchFieldsVisible(WebSearchProvider provider) noexcept {
  return WebSearchProviderInfo(provider).fields_visible;
}

enum class ImageModelPurpose : std::uint8_t {
  understanding,
  generation,
};

struct ToolSettingsState final {
  WebSearchConfig web_search{DefaultWebSearchConfig()};
  std::string image_understanding_model_id;
  std::string image_generation_model_id;

  bool operator==(const ToolSettingsState &) const = default;
};

} // namespace linecode::domain
