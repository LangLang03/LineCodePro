#include "infrastructure/tool_settings_codec.h"

#include <concepts>
#include <string>
#include <type_traits>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

using archive_json::Object;
using archive_json::Value;

[[nodiscard]] std::string StringOr(const Object &object, std::string_view key,
                                   std::string_view fallback) {
  const auto *value = archive_json::Find(object, key);
  if (!value)
    return std::string{fallback};
  if (const auto *text = archive_json::AsString(value))
    return *text;
  return std::visit(
      [value, fallback](const auto &typed) -> std::string {
        using Type = std::remove_cvref_t<decltype(typed)>;
        if constexpr (std::same_as<Type, bool> ||
                      std::same_as<Type, std::int64_t> ||
                      std::same_as<Type, double>) {
          return archive_json::Serialize(*value);
        } else {
          return std::string{fallback};
        }
      },
      *value);
}

} // namespace

std::string EncodeWebSearchConfig(const domain::WebSearchConfig &config) {
  const auto normalized = domain::NormalizeWebSearchConfig(config);
  return archive_json::Serialize(Object{
      {"provider",
       std::string{domain::WebSearchProviderStorageName(normalized.provider)}},
      {"baseUrl", normalized.base_url},
      {"apiKey", normalized.api_key},
      {"model", normalized.model},
      {"queryParam", normalized.query_param},
      {"apiKeyHeader", normalized.api_key_header},
      {"apiKeyParam", normalized.api_key_param},
  });
}

std::expected<domain::WebSearchConfig, ToolSettingsCodecError>
DecodeWebSearchConfig(std::string_view json) {
  auto parsed = archive_json::Parse(json);
  if (!parsed) {
    return std::unexpected(
        ToolSettingsCodecError{.message = std::move(parsed.error().message)});
  }
  const auto *object = archive_json::AsObject(&*parsed);
  if (!object) {
    return std::unexpected(
        ToolSettingsCodecError{.message = "web search config is not an object"});
  }

  const auto provider = domain::ParseWebSearchProvider(
                            StringOr(*object, "provider", ""))
                            .value_or(domain::WebSearchProvider::bing_rss_free);
  const auto defaults = domain::DefaultWebSearchConfig(provider);
  return domain::NormalizeWebSearchConfig(domain::WebSearchConfig{
      .provider = provider,
      .base_url = StringOr(*object, "baseUrl", defaults.base_url),
      .api_key = StringOr(*object, "apiKey", ""),
      .model = StringOr(*object, "model", ""),
      .query_param = StringOr(*object, "queryParam", defaults.query_param),
      .api_key_header =
          StringOr(*object, "apiKeyHeader", defaults.api_key_header),
      .api_key_param =
          StringOr(*object, "apiKeyParam", defaults.api_key_param),
  });
}

} // namespace linecode::infrastructure
