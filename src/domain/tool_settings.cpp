#include "domain/tool_settings.h"

namespace linecode::domain {
namespace {

void Trim(std::string &value) {
  const auto trim_byte = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && trim_byte(value.front()))
    value.erase(value.begin());
  while (!value.empty() && trim_byte(value.back()))
    value.pop_back();
}

} // namespace

WebSearchConfig DefaultWebSearchConfig(WebSearchProvider provider) {
  const auto &descriptor = WebSearchProviderInfo(provider);
  return WebSearchConfig{
      .provider = descriptor.provider,
      .base_url = std::string{descriptor.default_base_url},
      .api_key = {},
      .model = {},
      .query_param = std::string{descriptor.default_query_param},
      .api_key_header = std::string{descriptor.default_api_key_header},
      .api_key_param = std::string{descriptor.default_api_key_param},
  };
}

WebSearchConfig NormalizeWebSearchConfig(WebSearchConfig config) {
  Trim(config.base_url);
  Trim(config.api_key);
  Trim(config.model);
  Trim(config.query_param);
  Trim(config.api_key_header);
  Trim(config.api_key_param);
  if (config.query_param.empty())
    config.query_param = "q";
  return config;
}

std::string NormalizeToolModelId(std::string model_id) {
  Trim(model_id);
  return model_id;
}

std::string_view
EffectiveWebSearchQueryParam(const WebSearchConfig &config) noexcept {
  return config.query_param.empty() ? std::string_view{"q"}
                                    : std::string_view{config.query_param};
}

} // namespace linecode::domain
