#include "application/web_tool_registry.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <expected>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

using domain::WebSearchConfig;
using domain::WebSearchProvider;

// WebFetchTool.getDescription() (WebFetchTool.java:22-24), verbatim.
constexpr std::string_view kWebFetchDescription =
    "View and extract the text content of a specified web page. The URL must "
    "use HTTPS, or HTTP on localhost/127.0.0.1/10.0.2.2.";

// WebSearchTool.getDescription() (WebSearchTool.java:42-44), verbatim.
constexpr std::string_view kWebSearchDescription =
    "Search the internet. Requires the user to configure the search API, "
    "model/search source, and key in MCP tool settings first. Suitable for "
    "querying the latest facts, documentation, news, and web materials.";

// WebFetchTool.getParameters() (WebFetchTool.java:52-59): properties url and
// maxChars with the legacy descriptions, required url only.
constexpr std::string_view kWebFetchParameters =
    R"({"properties":{"maxChars":{"description":"Maximum characters to return, default 12000, max 30000","type":"number"},"url":{"description":"The web page URL to view","type":"string"}},"required":["url"],"type":"object"})";

// WebSearchTool.getParameters() (WebSearchTool.java:72-79): properties query
// and limit with the legacy descriptions, required query only.
constexpr std::string_view kWebSearchParameters =
    R"({"properties":{"limit":{"description":"Number of results to return, 1-10, default 5","type":"number"},"query":{"description":"Search keyword or question","type":"string"}},"required":["query"],"type":"object"})";

// feature-tool/src/main/res/values/strings.xml lines 118 and 123.
constexpr std::string_view kQueryEmptyMessage = "Search query cannot be empty.";
constexpr std::string_view kUrlEmptyMessage = "URL cannot be empty.";

constexpr std::int32_t kDefaultSearchLimit = 5;
constexpr std::int32_t kDefaultFetchCharacters = 12'000;
// Bounds the parsed JSON number before it reaches the port, which applies the
// legacy 1..10 and 1000..30000 clamps.
constexpr std::int64_t kMaximumParsedLimit = 1'000;
constexpr std::int64_t kMaximumParsedCharacters = 10'000'000;

struct WebToolContext final {
  ToolSettingsService *settings{};
  WebToolsGateway *gateway{};
};

using WebToolExecutor = huxerui::Task<
    std::expected<ToolInvocationResult, ToolRegistryError>> (*)(
    WebToolContext context, std::string arguments_json);

// Declarative tool table: name, schema, read-only policy and executor live in
// one row, and Invoke dispatches through FindPlan without any name-based
// branch.
struct WebToolPlan final {
  std::string_view name;
  std::string_view description;
  std::string_view parameters_json;
  bool allowed_in_read_only;
  bool permanent_grant_supported;
  WebToolExecutor execute;
};

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

ToolRegistryError Adapt(const WebToolError &error) {
  using enum WebToolErrorCode;
  switch (error.code) {
  case invalid_arguments:
  case unsupported_url:
    return Error(ToolRegistryErrorCode::invalid_arguments, error.message);
  case not_configured:
    return Error(ToolRegistryErrorCode::unavailable, error.message);
  case transport:
  case http_status:
  case response_too_large:
  case decode:
    return Error(ToolRegistryErrorCode::invocation_failed, error.message);
  }
  std::unreachable();
}

std::string Trim(std::string_view value) {
  const auto trimmable = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && trimmable(value.front()))
    value.remove_prefix(1U);
  while (!value.empty() && trimmable(value.back()))
    value.remove_suffix(1U);
  return std::string{value};
}

// JSON Object numbers reach the port as an integer or a floating point value.
std::expected<std::int32_t, ToolRegistryError>
ParseNumber(const json::Value *value, std::string_view message,
            std::int64_t minimum, std::int64_t maximum) {
  std::optional<std::int64_t> parsed;
  if (const auto *integer = std::get_if<std::int64_t>(value))
    parsed = *integer;
  else if (const auto *number = std::get_if<double>(value))
    parsed = static_cast<std::int64_t>(*number);
  if (value == nullptr || !parsed) {
    return std::unexpected(
        Error(ToolRegistryErrorCode::invalid_arguments, std::string{message}));
  }
  return static_cast<std::int32_t>(std::clamp(*parsed, minimum, maximum));
}

struct WebFetchArguments final {
  WebFetchRequest request;
};

// WebFetchTool.execute (WebFetchTool.java:62-73): a trimmed URL is required and
// maxChars keeps the legacy default of 12000.
std::expected<WebFetchArguments, ToolRegistryError>
ParseWebFetchArguments(std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    return std::unexpected(
        Error(ToolRegistryErrorCode::invalid_arguments,
              "web_fetch arguments must be a JSON object"));
  }
  const auto *url = json::AsString(json::Find(*object, "url"));
  if (url == nullptr) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kUrlEmptyMessage}));
  }
  auto trimmed = Trim(*url);
  if (trimmed.empty()) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kUrlEmptyMessage}));
  }
  WebFetchArguments arguments{.request = WebFetchRequest{
                                  .url = std::move(trimmed),
                                  .max_characters = kDefaultFetchCharacters,
                              }};
  if (const auto *max_characters = json::Find(*object, "maxChars")) {
    auto value =
        ParseNumber(max_characters, "web_fetch maxChars must be a number", 0,
                    kMaximumParsedCharacters);
    if (!value)
      return std::unexpected(std::move(value.error()));
    arguments.request.max_characters = *value;
  }
  return arguments;
}

struct WebSearchArguments final {
  std::string query;
  std::int32_t limit{kDefaultSearchLimit};
};

// WebSearchTool.execute (WebSearchTool.java:82-88): a trimmed query is required
// and limit keeps the legacy default of 5.
std::expected<WebSearchArguments, ToolRegistryError>
ParseWebSearchArguments(std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    return std::unexpected(
        Error(ToolRegistryErrorCode::invalid_arguments,
              "web_search arguments must be a JSON object"));
  }
  const auto *query = json::AsString(json::Find(*object, "query"));
  if (query == nullptr) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kQueryEmptyMessage}));
  }
  auto trimmed = Trim(*query);
  if (trimmed.empty()) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kQueryEmptyMessage}));
  }
  WebSearchArguments arguments{.query = std::move(trimmed),
                               .limit = kDefaultSearchLimit};
  if (const auto *limit = json::Find(*object, "limit")) {
    auto value = ParseNumber(limit, "web_search limit must be a number", 0,
                             kMaximumParsedLimit);
    if (!value)
      return std::unexpected(std::move(value.error()));
    arguments.limit = *value;
  }
  return arguments;
}

// WebSearchTool.execute (WebSearchTool.java:93-108) result rendering.
std::string FormatSearchResults(
    std::string_view query, const std::vector<WebSearchResultItem> &results) {
  if (results.empty()) {
    return "No web results found for \"" + std::string{query} + "\".";
  }
  std::string content;
  for (std::size_t index = 0; index < results.size(); ++index) {
    if (index > 0U)
      content += "\n\n";
    const auto &item = results[index];
    content += std::to_string(index + 1U);
    content += ". ";
    content += item.title;
    content += "\nURL: ";
    content += item.url;
    if (!item.published_date.empty()) {
      content += "\nDate: ";
      content += item.published_date;
    }
    if (!item.snippet.empty()) {
      content += "\nSnippet: ";
      content += item.snippet;
    }
  }
  return content;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteWebFetch(WebToolContext context, std::string arguments_json) {
  auto arguments = ParseWebFetchArguments(arguments_json);
  if (!arguments)
    co_return std::unexpected(std::move(arguments.error()));
  if (context.gateway == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unavailable,
                                    "Web tools are not connected"));
  }
  const auto url = arguments->request.url;
  auto fetched =
      co_await context.gateway->FetchPage(std::move(arguments->request));
  if (!fetched)
    co_return std::unexpected(Adapt(fetched.error()));
  co_return ToolInvocationResult{.content = "URL: " + url + "\n\n" + *fetched,
                                 .error = false};
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteWebSearch(WebToolContext context, std::string arguments_json) {
  auto arguments = ParseWebSearchArguments(arguments_json);
  if (!arguments)
    co_return std::unexpected(std::move(arguments.error()));
  if (context.settings == nullptr || context.gateway == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unavailable,
                                    "Web tools are not connected"));
  }
  auto settings = co_await context.settings->Load();
  if (!settings) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::load_failed,
                                    settings.error().message));
  }
  const auto query = arguments->query;
  auto results = co_await context.gateway->Search(WebSearchRequest{
      .config = settings->web_search,
      .query = std::move(arguments->query),
      .limit = arguments->limit,
  });
  if (!results)
    co_return std::unexpected(Adapt(results.error()));
  co_return ToolInvocationResult{
      .content = FormatSearchResults(query, *results), .error = false};
}

constexpr std::array kWebToolPlans{
    WebToolPlan{
        .name = kWebFetchToolName,
        .description = kWebFetchDescription,
        .parameters_json = kWebFetchParameters,
        // BaseTool.isAllowedInReadonlyMode() defaults to false and
        // WebFetchTool does not override it.
        .allowed_in_read_only = false,
        .permanent_grant_supported = false,
        .execute = &ExecuteWebFetch,
    },
    WebToolPlan{
        .name = kWebSearchToolName,
        .description = kWebSearchDescription,
        .parameters_json = kWebSearchParameters,
        // BaseTool.isAllowedInReadonlyMode() defaults to false and
        // WebSearchTool does not override it.
        .allowed_in_read_only = false,
        .permanent_grant_supported = false,
        .execute = &ExecuteWebSearch,
    },
};

const WebToolPlan *FindPlan(std::string_view name) noexcept {
  const auto found = std::ranges::find(kWebToolPlans, name, &WebToolPlan::name);
  return found == kWebToolPlans.end() ? nullptr : &*found;
}

bool WebToolGroupEnabled(const domain::McpExecutionSettings &settings) {
  const auto found = std::ranges::find(
      settings.groups, kWebToolGroupId,
      [](const domain::McpToolGroupState &group) {
        return std::string_view{group.id};
      });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

} // namespace

WebToolRegistry::WebToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<ToolSettingsService> tool_settings,
    std::shared_ptr<WebToolsGateway> gateway)
    : settings_(std::move(settings)), tool_settings_(std::move(tool_settings)),
      gateway_(std::move(gateway)) {
  if (!settings_ || !tool_settings_ || !gateway_) {
    throw std::invalid_argument(
        "WebToolRegistry requires tool settings and web gateway services");
  }
}

huxerui::Task<std::expected<void, ToolRegistryError>>
WebToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::load_failed,
                                    settings.error().message));
  }
  tools_.clear();
  if (WebToolGroupEnabled(*settings)) {
    for (const auto &plan : kWebToolPlans) {
      tools_.push_back(RegisteredTool{
          .name = std::string{plan.name},
          .description = std::string{plan.description},
          .parameters_json = std::string{plan.parameters_json},
          .allowed_in_read_only = plan.allowed_in_read_only,
          .permanent_grant_supported = plan.permanent_grant_supported,
          .category = std::string{kWebToolGroupId},
          .agent_selectable = true,
          .agent_selected_by_default = false,
      });
    }
  }
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool> WebToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
WebToolRegistry::Invoke(std::string name, std::string arguments_json) {
  const auto *plan = FindPlan(name);
  if (plan == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown web tool: " + name));
  }
  if (std::ranges::find(tools_, name, &RegisteredTool::name) == tools_.end()) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::unavailable,
        "Web tools are disabled for the current execution mode"));
  }
  co_return co_await plan->execute(
      WebToolContext{.settings = tool_settings_.get(),
                     .gateway = gateway_.get()},
      std::move(arguments_json));
}

} // namespace linecode::application
