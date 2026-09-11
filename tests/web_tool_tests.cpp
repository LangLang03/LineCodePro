#include <algorithm>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/http.h>
#include <huxerui/testing/ui_test.h>

#include "application/ports/web_tools.h"
#include "application/tool_settings_service.h"
#include "application/web_tool_registry.h"
#include "domain/tool_settings.h"
#include "infrastructure/archive_json.h"
#include "infrastructure/hux_web_tools_gateway.h"
#include "infrastructure/web_tools_codec.h"

namespace {

using namespace linecode;

using application::SettingsResult;
using application::WebFetchRequest;
using application::WebSearchRequest;
using application::WebSearchResultItem;
using application::WebToolError;
using application::WebToolErrorCode;
using domain::McpExecutionSettings;
using domain::McpExecutionMode;
using domain::McpExecutionModeMask;
using domain::McpToolGroupState;
using domain::ToolSettingsState;
using domain::WebSearchConfig;
using domain::WebSearchProvider;

constexpr std::string_view kWebGroup = "web_search";

// Legacy cn.lineai.tool.builtin.WebFetchTool / WebSearchTool literals kept next
// to the assertions so an accidental edit of the registry fails the test.
constexpr std::string_view kExpectedFetchDescription =
    "View and extract the text content of a specified web page. The URL must "
    "use HTTPS, or HTTP on localhost/127.0.0.1/10.0.2.2.";
constexpr std::string_view kExpectedSearchDescription =
    "Search the internet. Requires the user to configure the search API, "
    "model/search source, and key in MCP tool settings first. Suitable for "
    "querying the latest facts, documentation, news, and web materials.";
constexpr std::string_view kExpectedFetchParameters =
    R"({"properties":{"maxChars":{"description":"Maximum characters to return, default 12000, max 30000","type":"number"},"url":{"description":"The web page URL to view","type":"string"}},"required":["url"],"type":"object"})";
constexpr std::string_view kExpectedSearchParameters =
    R"({"properties":{"limit":{"description":"Number of results to return, 1-10, default 5","type":"number"},"query":{"description":"Search keyword or question","type":"string"}},"required":["query"],"type":"object"})";

constexpr std::string_view kNotConfiguredMessage =
    "Web search not configured. Please fill in the search API, model/search "
    "source and key in MCP tool settings.";

namespace json = infrastructure::archive_json;

class StubExecutionSettings final
    : public application::McpExecutionSettingsService {
public:
  huxerui::Task<SettingsResult<McpExecutionSettings>> Load() override {
    if (fail_load) {
      co_return std::unexpected(
          application::SettingsStoreError{.message = "settings unavailable"});
    }
    co_return value;
  }

  huxerui::Task<SettingsResult<void>> SetMode(McpExecutionMode mode) override {
    value.mode = mode;
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>>
  SetToolGroupEnabled(McpExecutionMode, std::string id, bool enabled) override {
    const auto found =
        std::ranges::find(value.groups, id, &McpToolGroupState::id);
    if (found != value.groups.end())
      found->enabled = enabled;
    co_return SettingsResult<void>{};
  }

  McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
  bool fail_load{};
};

class StubToolSettings final : public application::ToolSettingsService {
public:
  huxerui::Task<SettingsResult<ToolSettingsState>> Load() override {
    if (fail_load) {
      co_return std::unexpected(application::SettingsStoreError{
          .message = "tool settings unavailable"});
    }
    co_return value;
  }

  huxerui::Task<SettingsResult<void>>
  Persist(application::ToolSettingsChange) override {
    co_return SettingsResult<void>{};
  }

  ToolSettingsState value{};
  bool fail_load{};
};

class StubWebGateway final : public application::WebToolsGateway {
public:
  huxerui::Task<application::WebToolResult<std::vector<WebSearchResultItem>>>
  Search(WebSearchRequest request) override {
    search_calls.push_back(request);
    if (search_error)
      co_return std::unexpected(*search_error);
    co_return search_results;
  }

  huxerui::Task<application::WebToolResult<std::string>>
  FetchPage(WebFetchRequest request) override {
    fetch_calls.push_back(request);
    if (fetch_error)
      co_return std::unexpected(*fetch_error);
    co_return fetch_content;
  }

  std::vector<WebSearchRequest> search_calls;
  std::vector<WebFetchRequest> fetch_calls;
  std::optional<WebToolError> search_error;
  std::optional<WebToolError> fetch_error;
  std::vector<WebSearchResultItem> search_results;
  std::string fetch_content{"page body"};
};

std::shared_ptr<StubExecutionSettings> active_settings;
std::shared_ptr<StubToolSettings> active_tool_settings;
std::shared_ptr<StubWebGateway> active_gateway;
std::shared_ptr<application::WebToolRegistry> active_registry;
bool active_done{};

void ExpectRegistryError(
    const std::expected<application::ToolInvocationResult,
                        application::ToolRegistryError> &result,
    application::ToolRegistryErrorCode code, std::string_view message) {
  assert(!result);
  assert(result.error().code == code);
  assert(result.error().message == message);
}

std::string PropertyDescription(const json::Object &properties,
                                std::string_view key) {
  const auto *property = json::AsObject(json::Find(properties, key));
  if (property == nullptr)
    return {};
  const auto *description =
      json::AsString(json::Find(*property, "description"));
  return description == nullptr ? std::string{} : *description;
}

std::string RequiredEntry(const json::Object &object) {
  const auto *required = json::AsArray(json::Find(object, "required"));
  if (required == nullptr || required->size() != 1U)
    return {};
  const auto *entry = json::AsString(&required->front());
  return entry == nullptr ? std::string{} : *entry;
}

void CodecConfigurationContract() {
  using infrastructure::BuildWebSearchRequest;
  using infrastructure::ClampWebFetchCharacters;
  using infrastructure::ClampWebSearchResultLimit;
  using infrastructure::ValidateWebSearchConfiguration;
  using infrastructure::ValidateWebUrl;
  using infrastructure::WebHttpMethod;
  using infrastructure::WebSearchMarket;
  using infrastructure::WebSearchRequiresApiKey;

  // Legacy WebSearchConfig.requiresApiKey(): only the public RSS endpoint is
  // keyless (core-model/.../WebSearchConfig.java:67-70).
  assert(!WebSearchRequiresApiKey(WebSearchProvider::bing_rss_free));
  assert(WebSearchRequiresApiKey(WebSearchProvider::tavily));
  assert(WebSearchRequiresApiKey(WebSearchProvider::brave_search));
  assert(WebSearchRequiresApiKey(WebSearchProvider::serp_api));
  assert(WebSearchRequiresApiKey(WebSearchProvider::bing_search));
  assert(WebSearchRequiresApiKey(WebSearchProvider::custom));

  // WebSearchService.search gating (WebSearchService.java:34-41).
  auto config =
      domain::DefaultWebSearchConfig(WebSearchProvider::bing_rss_free);
  assert(ValidateWebSearchConfiguration(config));
  config.base_url.clear();
  assert(ValidateWebSearchConfiguration(config));
  auto keyless = domain::DefaultWebSearchConfig(WebSearchProvider::tavily);
  auto missing_key = ValidateWebSearchConfiguration(keyless);
  assert(!missing_key);
  assert(missing_key.error().code == WebToolErrorCode::not_configured);
  assert(missing_key.error().message == kNotConfiguredMessage);
  keyless.api_key = "secret";
  keyless.base_url.clear();
  auto missing_url = ValidateWebSearchConfiguration(keyless);
  assert(!missing_url);
  assert(missing_url.error().code == WebToolErrorCode::not_configured);
  keyless.base_url = "https://api.tavily.com/search";
  auto configured = ValidateWebSearchConfiguration(keyless);
  assert(configured);
  assert(configured->api_key == "secret");

  // Legacy clamps (WebSearchService.java:42 and :69).
  assert(ClampWebSearchResultLimit(0) == 5);
  assert(ClampWebSearchResultLimit(-3) == 5);
  assert(ClampWebSearchResultLimit(1) == 1);
  assert(ClampWebSearchResultLimit(10) == 10);
  assert(ClampWebSearchResultLimit(20) == 10);
  assert(ClampWebFetchCharacters(0) == 12'000);
  assert(ClampWebFetchCharacters(-1) == 12'000);
  assert(ClampWebFetchCharacters(999) == 1'000);
  assert(ClampWebFetchCharacters(12'000) == 12'000);
  assert(ClampWebFetchCharacters(99'999) == 30'000);

  // BingRssSearchProvider request shape (query, count, mkt, safe).
  auto rss = domain::DefaultWebSearchConfig(WebSearchProvider::bing_rss_free);
  auto plan = BuildWebSearchRequest(rss, "linecode", 5);
  assert(plan);
  assert(plan->method == WebHttpMethod::get);
  assert(plan->url ==
         "https://www.bing.com/search?format=rss&q=linecode&count=5&mkt=" +
             WebSearchMarket() + "&safe=strict");
  assert(plan->headers.size() == 2U);
  assert(plan->headers[0].first == "Accept");
  assert(plan->headers[0].second.starts_with("application/rss+xml"));
  assert(plan->headers[1].first == "User-Agent");
  // A plus sign is percent encoded exactly like java.net.URLEncoder does.
  plan = BuildWebSearchRequest(rss, "c++23", 12);
  assert(plan);
  assert(plan->url.find("q=c%2B%2B23") != std::string::npos);
  assert(plan->url.find("count=10") != std::string::npos);

  // Tavily posts JSON and always uses the Bearer authorization header.
  auto tavily = domain::DefaultWebSearchConfig(WebSearchProvider::tavily);
  tavily.api_key = "tvly-key";
  tavily.model = "advanced";
  plan = BuildWebSearchRequest(tavily, "linecode", 4);
  assert(plan);
  assert(plan->method == WebHttpMethod::post);
  assert(plan->url == "https://api.tavily.com/search");
  assert(plan->headers[0].second == "application/json");
  assert(plan->headers[1].first == "Authorization");
  assert(plan->headers[1].second == "Bearer tvly-key");
  auto body = json::Parse(plan->body);
  assert(body);
  const auto *object = json::AsObject(&*body);
  assert(object != nullptr);
  assert(*json::AsString(json::Find(*object, "query")) == "linecode");
  assert(*json::AsString(json::Find(*object, "search_depth")) == "advanced");
  assert(std::get<std::int64_t>(*json::Find(*object, "max_results")) == 4);
  assert(std::get<bool>(*json::Find(*object, "include_answer")) == false);

  // SerpApi passes the key as a query parameter.
  auto serp = domain::DefaultWebSearchConfig(WebSearchProvider::serp_api);
  serp.api_key = "serp-key";
  plan = BuildWebSearchRequest(serp, "docs", 7);
  assert(plan);
  assert(plan->url.starts_with("https://serpapi.com/search.json?"));
  assert(plan->url.find("q=docs") != std::string::npos);
  assert(plan->url.find("engine=google") != std::string::npos);
  assert(plan->url.find("api_key=serp-key") != std::string::npos);
  assert(plan->url.find("num=7") != std::string::npos);

  // Brave and Bing share the count request with a provider specific key header.
  auto brave = domain::DefaultWebSearchConfig(WebSearchProvider::brave_search);
  brave.api_key = "brave-key";
  plan = BuildWebSearchRequest(brave, "docs", 3);
  assert(plan);
  assert(plan->url.find("count=3") != std::string::npos);
  assert(plan->headers[1].first == "X-Subscription-Token");
  assert(plan->headers[1].second == "brave-key");
  auto bing = domain::DefaultWebSearchConfig(WebSearchProvider::bing_search);
  bing.api_key = "bing-key";
  plan = BuildWebSearchRequest(bing, "docs", 3);
  assert(plan);
  assert(plan->headers[1].first == "Ocp-Apim-Subscription-Key");
  assert(plan->headers[1].second == "bing-key");

  // The custom provider uses the default provider shape: model, key parameter,
  // limit and a Bearer authorization header.
  WebSearchConfig custom{
      .provider = WebSearchProvider::custom,
      .base_url = "https://search.example.com/v1?tenant=line",
      .api_key = "custom-key",
      .model = "sonar",
      .query_param = "q",
      .api_key_header = "Authorization",
      .api_key_param = "token",
  };
  plan = BuildWebSearchRequest(custom, "docs", 5);
  assert(plan);
  assert(plan->url.starts_with("https://search.example.com/v1?tenant=line&"));
  assert(plan->url.find("q=docs") != std::string::npos);
  assert(plan->url.find("model=sonar") != std::string::npos);
  assert(plan->url.find("token=custom-key") != std::string::npos);
  assert(plan->url.find("limit=5") != std::string::npos);
  assert(plan->headers[1].first == "Authorization");
  assert(plan->headers[1].second == "Bearer custom-key");

  auto empty_query = BuildWebSearchRequest(rss, "", 5);
  assert(!empty_query);
  assert(empty_query.error().code == WebToolErrorCode::invalid_arguments);
  assert(empty_query.error().message == "Search query cannot be empty.");

  // Legacy UrlPolicy.requireHttpOrLocalCleartextUrl (UrlPolicy.java:119-130).
  assert(ValidateWebUrl("https://example.com/page"));
  assert(*ValidateWebUrl(" http://localhost:8080/page ") ==
         "http://localhost:8080/page");
  assert(ValidateWebUrl("http://127.0.0.1:9/health"));
  assert(ValidateWebUrl("http://10.0.2.2/"));
  assert(ValidateWebUrl("http://192.168.1.5/"));
  assert(ValidateWebUrl("http://[::1]/"));
  auto cleartext = ValidateWebUrl("http://example.com/");
  assert(!cleartext);
  assert(cleartext.error().code == WebToolErrorCode::unsupported_url);
  assert(cleartext.error().message ==
         "URL using HTTP cleartext is only allowed for localhost, 127.0.0.1, "
         "or 10.0.2.2.");
  // A host name that merely starts with a private range must stay public.
  assert(!ValidateWebUrl("http://10.evil.com/"));
  for (const auto *invalid :
       {"ftp://example.com/", "example.com", "", "https://",
        "file:///etc/passwd"}) {
    auto result = ValidateWebUrl(invalid);
    assert(!result);
    assert(result.error().code == WebToolErrorCode::unsupported_url);
    assert(result.error().message ==
           "URL must start with http:// or https://.");
  }
}

void CodecResponseContract() {
  using application::WebSearchResultItem;
  using infrastructure::CompactWebText;
  using infrastructure::HtmlToPlainText;
  using infrastructure::ParseWebSearchResponse;
  using infrastructure::TruncateWebText;

  auto tavily = ParseWebSearchResponse(
      WebSearchProvider::tavily,
      R"({"results":[{"title":"T","url":"https://a.example","content":"C","published_date":"2024-01-02"}]})");
  assert(tavily);
  assert(tavily->size() == 1U);
  assert(tavily->front() ==
         WebSearchResultItem{.title = "T",
                             .url = "https://a.example",
                             .snippet = "C",
                             .published_date = "2024-01-02"});

  auto brave = ParseWebSearchResponse(
      WebSearchProvider::brave_search,
      R"({"web":{"results":[{"title":"B","url":"https://b.example","description":"D","age":"1 day"}]}})");
  assert(brave && brave->size() == 1U);
  assert(brave->front().snippet == "D");
  assert(brave->front().published_date == "1 day");

  auto bing = ParseWebSearchResponse(
      WebSearchProvider::bing_search,
      R"({"webPages":{"value":[{"name":"N","url":"https://c.example","snippet":"S","dateLastCrawled":"2024"}]}})");
  assert(bing && bing->size() == 1U);
  assert(bing->front().title == "N");
  assert(bing->front().published_date == "2024");

  auto serp = ParseWebSearchResponse(
      WebSearchProvider::serp_api,
      R"({"organic_results":[{"title":"Sp","link":"https://d.example","snippet":"Sn","date":"Mon"}]})");
  assert(serp && serp->size() == 1U);
  assert(serp->front().url == "https://d.example");
  assert(serp->front().snippet == "Sn");

  // Default provider candidate order and item rules: no URL means dropped, no
  // title means "Untitled".
  auto fallback = ParseWebSearchResponse(
      WebSearchProvider::custom,
      R"({"items":[{"link":"https://e.example","description":"Only a link"},{"title":"No URL"},{"url":"https://f.example"}]})");
  assert(fallback && fallback->size() == 2U);
  assert(fallback->at(0).title == "Untitled");
  assert(fallback->at(0).snippet == "Only a link");
  assert(fallback->at(1).url == "https://f.example");

  auto rss = ParseWebSearchResponse(
      WebSearchProvider::bing_rss_free,
      R"(<?xml version="1.0"?><rss version="2.0"><channel><item>)"
      R"(<title>R &amp; D</title><link>https://g.example</link>)"
      R"(<description>&lt;b&gt;Bold&lt;/b&gt; text  here</description>)"
      R"(<pubDate>Mon, 01 Jan 2024</pubDate></item></channel></rss>)");
  assert(rss && rss->size() == 1U);
  assert(rss->front().title == "R & D");
  assert(rss->front().url == "https://g.example");
  assert(rss->front().snippet == "Bold text here");
  assert(rss->front().published_date == "Mon, 01 Jan 2024");

  auto malformed =
      ParseWebSearchResponse(WebSearchProvider::tavily, "<html>no json</html>");
  assert(!malformed);
  assert(malformed.error().code == WebToolErrorCode::decode);

  // htmlToText() steps: drop script/style/noscript, block closings and <br> to
  // newlines, other tags to spaces, decode entities, collapse whitespace.
  assert(HtmlToPlainText(
             "<html><head><style>p{color:red}</style><script>var a=1;</script>"
             "</head><body><h1>Title</h1><p>First&nbsp;line</p><br/>"
             "<p>Second &amp; third</p><noscript>ignored</noscript>"
             "</body></html>") == "Title\nFirst line\n\nSecond & third");
  assert(CompactWebText("\n\n\n a \n\n\n b \n\n\n") == "a \n\n b");
  assert(TruncateWebText("abcdef", 3) ==
         "abc\n\n[Content truncated, original length approx. 6 characters]");
  assert(TruncateWebText("abc", 3) == "abc");
}

void RegistryCatalogContract() {
  const auto registry_tools = active_registry->Tools();
  assert(registry_tools.size() == 2U);
  const auto &fetch = registry_tools[0];
  assert(fetch.name == application::kWebFetchToolName);
  assert(fetch.name == "web_fetch");
  assert(fetch.description == kExpectedFetchDescription);
  assert(fetch.parameters_json == kExpectedFetchParameters);
  assert(fetch.category == kWebGroup);
  // BaseTool.isAllowedInReadonlyMode() defaults to false (BaseTool.java:29-31)
  // and neither web tool overrides it.
  assert(!fetch.allowed_in_read_only);
  assert(!fetch.permanent_grant_supported);
  const auto &search = registry_tools[1];
  assert(search.name == application::kWebSearchToolName);
  assert(search.name == "web_search");
  assert(search.description == kExpectedSearchDescription);
  assert(search.parameters_json == kExpectedSearchParameters);
  assert(search.category == kWebGroup);
  assert(!search.allowed_in_read_only);
  assert(!search.permanent_grant_supported);

  auto fetch_parameters = json::Parse(fetch.parameters_json);
  assert(fetch_parameters);
  const auto *fetch_object = json::AsObject(&*fetch_parameters);
  assert(fetch_object != nullptr);
  assert(RequiredEntry(*fetch_object) == "url");
  const auto *fetch_properties =
      json::AsObject(json::Find(*fetch_object, "properties"));
  assert(fetch_properties != nullptr && fetch_properties->size() == 2U);
  assert(PropertyDescription(*fetch_properties, "url") ==
         "The web page URL to view");
  assert(PropertyDescription(*fetch_properties, "maxChars") ==
         "Maximum characters to return, default 12000, max 30000");

  auto search_parameters = json::Parse(search.parameters_json);
  assert(search_parameters);
  const auto *search_object = json::AsObject(&*search_parameters);
  assert(search_object != nullptr);
  assert(RequiredEntry(*search_object) == "query");
  const auto *search_properties =
      json::AsObject(json::Find(*search_object, "properties"));
  assert(search_properties != nullptr && search_properties->size() == 2U);
  assert(PropertyDescription(*search_properties, "query") ==
         "Search keyword or question");
  assert(PropertyDescription(*search_properties, "limit") ==
         "Number of results to return, 1-10, default 5");
}

void GatewayContract() {
  // The real adapter rejects a missing HttpClient instead of deferring the
  // failure to the first call.
  bool rejected = false;
  try {
    const infrastructure::HuxWebToolsGateway gateway{
        std::shared_ptr<huxerui::HttpClient>{}};
    static_cast<void>(gateway);
  } catch (const std::invalid_argument &) {
    rejected = true;
  }
  assert(rejected);
}

huxerui::View Probe() {
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([tasks] {
    const auto handle = tasks.Launch([]() -> huxerui::Task<void> {
      auto refreshed = co_await active_registry->Refresh();
      assert(refreshed);
      RegistryCatalogContract();

      // web_fetch success keeps the legacy "URL: <url>\n\n<content>" frame.
      active_gateway->fetch_content = "page body";
      auto invoked = co_await active_registry->Invoke(
          "web_fetch", R"({"url":"https://example.com/page"})");
      assert(invoked);
      assert(!invoked->error);
      assert(invoked->content == "URL: https://example.com/page\n\npage body");
      assert(active_gateway->fetch_calls.size() == 1U);
      assert(active_gateway->fetch_calls.front().url ==
             "https://example.com/page");
      assert(active_gateway->fetch_calls.front().max_characters == 12'000);

      // maxChars keeps the legacy default and is forwarded unchanged; the port
      // applies ClampWebFetchCharacters (covered by the codec contract above).
      for (const auto &[raw, expected] :
           std::vector<std::pair<std::string, std::int32_t>>{
               {R"({"url":"https://a.example","maxChars":0})", 0},
               {R"({"url":"https://a.example","maxChars":500})", 500},
               {R"({"url":"https://a.example","maxChars":99999})", 99'999},
           }) {
        auto call = co_await active_registry->Invoke("web_fetch", raw);
        assert(call);
        assert(active_gateway->fetch_calls.back().max_characters == expected);
      }

      // web_fetch argument parsing and failure mapping.
      ExpectRegistryError(
          co_await active_registry->Invoke("web_fetch", "{}"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "URL cannot be empty.");
      ExpectRegistryError(
          co_await active_registry->Invoke("web_fetch", R"({"url":"   "})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "URL cannot be empty.");
      ExpectRegistryError(
          co_await active_registry->Invoke("web_fetch", R"({"url":7})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "URL cannot be empty.");
      ExpectRegistryError(
          co_await active_registry->Invoke("web_fetch", "not json"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "web_fetch arguments must be a JSON object");
      ExpectRegistryError(
          co_await active_registry->Invoke(
              "web_fetch", R"({"url":"https://a.example","maxChars":"big"})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "web_fetch maxChars must be a number");

      active_gateway->fetch_error =
          WebToolError{.code = WebToolErrorCode::unsupported_url,
                       .message = "URL using HTTP cleartext is only allowed "
                                  "for localhost, 127.0.0.1, or 10.0.2.2."};
      ExpectRegistryError(
          co_await active_registry->Invoke(
              "web_fetch", R"({"url":"http://example.com"})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "URL using HTTP cleartext is only allowed for localhost, 127.0.0.1, "
          "or 10.0.2.2.");
      active_gateway->fetch_error =
          WebToolError{.code = WebToolErrorCode::http_status,
                       .message = "Web request failed 404"};
      ExpectRegistryError(
          co_await active_registry->Invoke("web_fetch",
                                           R"({"url":"https://a.example"})"),
          application::ToolRegistryErrorCode::invocation_failed,
          "Web request failed 404");
      active_gateway->fetch_error =
          WebToolError{.code = WebToolErrorCode::transport,
                       .message = "connection reset"};
      ExpectRegistryError(
          co_await active_registry->Invoke("web_fetch",
                                           R"({"url":"https://a.example"})"),
          application::ToolRegistryErrorCode::invocation_failed,
          "connection reset");
      active_gateway->fetch_error.reset();

      // web_search forwards the loaded settings and renders the legacy layout.
      active_tool_settings->value.web_search =
          domain::DefaultWebSearchConfig(WebSearchProvider::brave_search);
      active_tool_settings->value.web_search.api_key = "brave-key";
      active_gateway->search_results = {
          WebSearchResultItem{.title = "First",
                              .url = "https://1.example",
                              .snippet = "S1",
                              .published_date = "2024"},
          WebSearchResultItem{.title = "Second",
                              .url = "https://2.example",
                              .snippet = {},
                              .published_date = {}},
      };
      invoked = co_await active_registry->Invoke("web_search",
                                                 R"({"query":"linecode"})");
      assert(invoked);
      assert(!invoked->error);
      assert(invoked->content ==
             "1. First\nURL: https://1.example\nDate: 2024\nSnippet: S1\n\n"
             "2. Second\nURL: https://2.example");
      assert(active_gateway->search_calls.size() == 1U);
      const auto &search_call = active_gateway->search_calls.front();
      assert(search_call.query == "linecode");
      assert(search_call.limit == 5);
      assert(search_call.config.provider == WebSearchProvider::brave_search);
      assert(search_call.config.api_key == "brave-key");
      assert(search_call.config.base_url ==
             "https://api.search.brave.com/res/v1/web/search");

      // limit and query parsing; the port applies the legacy clamps.
      invoked = co_await active_registry->Invoke(
          "web_search", R"({"query":"docs","limit":3})");
      assert(invoked);
      assert(active_gateway->search_calls.back().limit == 3);
      invoked = co_await active_registry->Invoke(
          "web_search", R"({"query":"docs","limit":-2})");
      assert(invoked);
      assert(active_gateway->search_calls.back().limit == 0);
      invoked = co_await active_registry->Invoke(
          "web_search", R"({"query":"  spaced query  "})");
      assert(invoked);
      assert(active_gateway->search_calls.back().query == "spaced query");

      // No results keeps the legacy message with the trimmed query.
      active_gateway->search_results.clear();
      invoked = co_await active_registry->Invoke("web_search",
                                                 R"({"query":"nothing"})");
      assert(invoked);
      assert(invoked->content == "No web results found for \"nothing\".");

      // web_search argument parsing and failure mapping.
      ExpectRegistryError(
          co_await active_registry->Invoke("web_search", "{}"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "Search query cannot be empty.");
      ExpectRegistryError(
          co_await active_registry->Invoke("web_search", R"({"query":"  "})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "Search query cannot be empty.");
      ExpectRegistryError(
          co_await active_registry->Invoke("web_search", R"({"query":1})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "Search query cannot be empty.");
      ExpectRegistryError(
          co_await active_registry->Invoke("web_search", "[]"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "web_search arguments must be a JSON object");
      ExpectRegistryError(
          co_await active_registry->Invoke(
              "web_search", R"({"query":"q","limit":"many"})"),
          application::ToolRegistryErrorCode::invalid_arguments,
          "web_search limit must be a number");

      // A missing configuration arrives from the port as not_configured and is
      // reported as unavailable with the legacy message.
      active_tool_settings->value.web_search =
          domain::DefaultWebSearchConfig(WebSearchProvider::tavily);
      active_gateway->search_error = WebToolError{
          .code = WebToolErrorCode::not_configured,
          .message = std::string{kNotConfiguredMessage}};
      ExpectRegistryError(
          co_await active_registry->Invoke("web_search",
                                           R"({"query":"linecode"})"),
          application::ToolRegistryErrorCode::unavailable,
          kNotConfiguredMessage);
      const auto &keyless_call = active_gateway->search_calls.back();
      assert(keyless_call.config.provider == WebSearchProvider::tavily);
      assert(keyless_call.config.api_key.empty());
      active_gateway->search_error = WebToolError{
          .code = WebToolErrorCode::http_status,
          .message = "Search API 429: rate limited"};
      ExpectRegistryError(
          co_await active_registry->Invoke("web_search",
                                           R"({"query":"linecode"})"),
          application::ToolRegistryErrorCode::invocation_failed,
          "Search API 429: rate limited");
      active_gateway->search_error.reset();

      // Unknown tools and disabled groups.
      auto rejected = co_await active_registry->Invoke("web_crawl", "{}");
      assert(!rejected);
      assert(rejected.error().code ==
             application::ToolRegistryErrorCode::unknown_tool);

      const auto disable_group = [](bool enabled) {
        const auto found = std::ranges::find(active_settings->value.groups,
                                             std::string{kWebGroup},
                                             &McpToolGroupState::id);
        assert(found != active_settings->value.groups.end());
        found->enabled = enabled;
      };
      disable_group(false);
      refreshed = co_await active_registry->Refresh();
      assert(refreshed);
      assert(active_registry->Tools().empty());
      rejected = co_await active_registry->Invoke(
          "web_search", R"({"query":"linecode"})");
      assert(!rejected);
      assert(rejected.error().code ==
             application::ToolRegistryErrorCode::unavailable);
      rejected = co_await active_registry->Invoke(
          "web_fetch", R"({"url":"https://a.example"})");
      assert(!rejected);
      assert(rejected.error().code ==
             application::ToolRegistryErrorCode::unavailable);

      // Enabled but unsupported for the active execution mode.
      active_settings->value.groups.clear();
      active_settings->value.groups.push_back(McpToolGroupState{
          .id = std::string{kWebGroup},
          .enabled = true,
          .supported_modes = McpExecutionModeMask::local,
      });
      active_settings->value.mode = McpExecutionMode::ssh;
      refreshed = co_await active_registry->Refresh();
      assert(refreshed);
      assert(active_registry->Tools().empty());

      // A settings failure fails the refresh.
      active_settings->fail_load = true;
      auto load_failed = co_await active_registry->Refresh();
      assert(!load_failed);
      assert(load_failed.error().code ==
             application::ToolRegistryErrorCode::load_failed);
      assert(load_failed.error().message == "settings unavailable");
      active_settings->fail_load = false;

      // A tool settings failure fails web_search before any network call.
      active_settings->value = domain::DefaultMcpExecutionSettings();
      refreshed = co_await active_registry->Refresh();
      assert(refreshed);
      assert(active_registry->Tools().size() == 2U);
      active_tool_settings->fail_load = true;
      const auto search_calls_before = active_gateway->search_calls.size();
      auto tool_load_failed = co_await active_registry->Invoke(
          "web_search", R"({"query":"linecode"})");
      assert(!tool_load_failed);
      assert(tool_load_failed.error().code ==
             application::ToolRegistryErrorCode::load_failed);
      assert(tool_load_failed.error().message == "tool settings unavailable");
      assert(active_gateway->search_calls.size() == search_calls_before);
      active_tool_settings->fail_load = false;

      active_done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("web-tool-probe");
}

} // namespace

int main() {
  CodecConfigurationContract();
  CodecResponseContract();
  GatewayContract();

  active_settings = std::make_shared<StubExecutionSettings>();
  active_tool_settings = std::make_shared<StubToolSettings>();
  active_gateway = std::make_shared<StubWebGateway>();
  active_registry = std::make_shared<application::WebToolRegistry>(
      active_settings, active_tool_settings, active_gateway);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active_done; });
  active_registry.reset();
  active_gateway.reset();
  active_tool_settings.reset();
  active_settings.reset();
}
