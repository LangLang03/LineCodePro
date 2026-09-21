#include <array>
#include "gtest_support.h"
#include <string>
#include <string_view>

#include "application/tool_settings_service.h"
#include "domain/tool_settings.h"
#include "infrastructure/tool_settings_codec.h"

namespace {

using linecode::domain::WebSearchConfig;
using linecode::domain::WebSearchProvider;

struct ExpectedDefaults final {
  WebSearchProvider provider;
  std::string_view storage_name;
  std::string_view base_url;
  std::string_view query_param;
  std::string_view api_key_header;
  std::string_view api_key_param;
};

constexpr std::array expected_defaults{
    ExpectedDefaults{WebSearchProvider::bing_rss_free, "bing_rss_free",
                     "https://www.bing.com/search?format=rss", "q", "", ""},
    ExpectedDefaults{WebSearchProvider::tavily, "tavily",
                     "https://api.tavily.com/search", "query",
                     "Authorization", ""},
    ExpectedDefaults{WebSearchProvider::brave_search, "brave",
                     "https://api.search.brave.com/res/v1/web/search", "q",
                     "X-Subscription-Token", ""},
    ExpectedDefaults{WebSearchProvider::serp_api, "serpapi",
                     "https://serpapi.com/search.json", "q", "", "api_key"},
    ExpectedDefaults{WebSearchProvider::bing_search, "bing",
                     "https://api.bing.microsoft.com/v7.0/search", "q",
                     "Ocp-Apim-Subscription-Key", ""},
    ExpectedDefaults{WebSearchProvider::custom, "custom", "", "q",
                     "Authorization", ""},
};

void VerifyProviderDefaults() {
  using namespace linecode::domain;
  static_assert(web_search_provider_catalog.size() == expected_defaults.size());
  for (const auto &expected : expected_defaults) {
    EXPECT_EXPRESSION(WebSearchProviderStorageName(expected.provider) ==
           expected.storage_name);
    EXPECT_EXPRESSION(ParseWebSearchProvider(expected.storage_name) == expected.provider);

    const auto config = DefaultWebSearchConfig(expected.provider);
    EXPECT_EXPRESSION(config.provider == expected.provider);
    EXPECT_EXPRESSION(config.base_url == expected.base_url);
    EXPECT_EXPRESSION(config.query_param == expected.query_param);
    EXPECT_EXPRESSION(config.api_key_header == expected.api_key_header);
    EXPECT_EXPRESSION(config.api_key_param == expected.api_key_param);
    EXPECT_EXPRESSION(config.api_key.empty());
    EXPECT_EXPRESSION(config.model.empty());
  }

  EXPECT_EXPRESSION(!ParseWebSearchProvider("unknown"));
  EXPECT_EXPRESSION(!WebSearchFieldsVisible(WebSearchProvider::bing_rss_free));
  for (const auto &descriptor : web_search_provider_catalog) {
    EXPECT_EXPRESSION(WebSearchFieldsVisible(descriptor.provider) ==
           descriptor.fields_visible);
  }
}

void VerifyCodec() {
  using namespace linecode::infrastructure;
  for (const auto &descriptor :
       linecode::domain::web_search_provider_catalog) {
    auto config =
        linecode::domain::DefaultWebSearchConfig(descriptor.provider);
    config.api_key = "key-\n\"\\value";
    config.model = "model-name";
    const auto encoded = EncodeWebSearchConfig(config);
    const auto decoded = DecodeWebSearchConfig(encoded);
    EXPECT_EXPRESSION(decoded);
    EXPECT_EXPRESSION(*decoded == config);
  }

  EXPECT_EXPRESSION(!DecodeWebSearchConfig("not json"));
  EXPECT_EXPRESSION(!DecodeWebSearchConfig("[]"));
  const auto unknown = DecodeWebSearchConfig(
      R"({"provider":"unknown","baseUrl":"","apiKey":"","model":"","queryParam":"q","apiKeyHeader":"","apiKeyParam":""})");
  EXPECT_EXPRESSION(unknown);
  EXPECT_EXPRESSION(unknown->provider == WebSearchProvider::bing_rss_free);
  const auto partial = DecodeWebSearchConfig(R"({"provider":"custom"})");
  EXPECT_EXPRESSION(partial);
  EXPECT_EXPRESSION(*partial ==
         linecode::domain::DefaultWebSearchConfig(WebSearchProvider::custom));
  const auto scalar = DecodeWebSearchConfig(
      R"({"provider":"custom","baseUrl":4,"apiKey":"","model":"","queryParam":"q","apiKeyHeader":"","apiKeyParam":""})");
  EXPECT_EXPRESSION(scalar);
  EXPECT_EXPRESSION(scalar->base_url == "4");

  auto padded = linecode::domain::DefaultWebSearchConfig(
      WebSearchProvider::custom);
  padded.base_url = "  https://example.test/search  ";
  padded.api_key = " key\n";
  const auto normalized = DecodeWebSearchConfig(EncodeWebSearchConfig(padded));
  EXPECT_EXPRESSION(normalized);
  EXPECT_EXPRESSION(normalized->base_url == "https://example.test/search");
  EXPECT_EXPRESSION(normalized->api_key == "key");

  auto empty_query = linecode::domain::DefaultWebSearchConfig(
      WebSearchProvider::custom);
  empty_query.query_param.clear();
  const auto query_normalized =
      DecodeWebSearchConfig(EncodeWebSearchConfig(empty_query));
  EXPECT_EXPRESSION(query_normalized);
  EXPECT_EXPRESSION(query_normalized->query_param == "q");
}

void VerifyApplicationChanges() {
  using namespace linecode;
  domain::ToolSettingsState state;

  auto custom = domain::DefaultWebSearchConfig(WebSearchProvider::custom);
  custom.base_url = "https://search.example/api";
  custom.api_key = "secret";
  custom.model = "advanced";
  state = application::ApplyToolSettingsChange(
      std::move(state),
      application::WebSearchConfigurationChange{.value = custom});
  EXPECT_EXPRESSION(state.web_search == custom);

  state = application::ApplyToolSettingsChange(
      std::move(state),
      application::ImageModelSelectionChange{
          .purpose = domain::ImageModelPurpose::understanding,
          .model_id = "vision-model"});
  state = application::ApplyToolSettingsChange(
      std::move(state),
      application::ImageModelSelectionChange{
          .purpose = domain::ImageModelPurpose::generation,
          .model_id = "image-model"});
  EXPECT_EXPRESSION(state.image_understanding_model_id == "vision-model");
  EXPECT_EXPRESSION(state.image_generation_model_id == "image-model");
  EXPECT_EXPRESSION(state.web_search == custom);

  const auto reset =
      domain::DefaultWebSearchConfig(WebSearchProvider::bing_rss_free);
  EXPECT_EXPRESSION(reset.api_key.empty());
  EXPECT_EXPRESSION(reset.model.empty());
  EXPECT_EXPRESSION(linecode::domain::NormalizeToolModelId("  vision-model \n") ==
         "vision-model");
}

void VerifyCompatibilityKeys() {
  using namespace linecode::application::tool_setting_keys;
  static_assert(web_search_config == "@lineai_web_search_config");
  static_assert(image_understanding_model_id ==
                "@lineai_image_understanding_model_id");
  static_assert(image_generation_model_id ==
                "@lineai_image_generation_model_id");
}

} // namespace

TEST(tool_settings_tests, LegacySuite) {
  VerifyProviderDefaults();
  VerifyCodec();
  VerifyApplicationChanges();
  VerifyCompatibilityKeys();
}
