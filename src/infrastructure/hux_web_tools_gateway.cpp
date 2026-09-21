#include "infrastructure/hux_web_tools_gateway.h"

#include <cctype>
#include <chrono>
#include <cstddef>
#include <optional>
#include <span>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"
#include "infrastructure/web_tools_codec.h"

namespace linecode::infrastructure {
namespace {

using application::WebSearchResultItem;
using application::WebToolError;
using application::WebToolErrorCode;

// cn.lineai.security.SimpleHttpClient: 10s connect plus 20s read in the legacy
// client; HuxerUI exposes one deadline for the whole operation.
constexpr auto kRequestTimeout = std::chrono::seconds{20};
// SimpleHttpClient.MAX_RESPONSE_BODY_BYTES.
constexpr std::size_t kMaximumResponseBytes = 32U * 1024U * 1024U;
constexpr std::size_t kReadChunkBytes = 64U * 1024U;
constexpr std::size_t kMaximumErrorBodyBytes = 2'048U;

WebToolError Error(WebToolErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

huxerui::Bytes BytesFromString(std::string_view value) {
  const auto *first = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(first, first + value.size());
}

std::string StringFromBytes(const huxerui::Bytes &value) {
  if (value.empty())
    return {};
  return {reinterpret_cast<const char *>(value.data()), value.size()};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  for (const unsigned char byte : value)
    lowered.push_back(static_cast<char>(std::tolower(byte)));
  return lowered;
}

std::optional<std::string>
HeaderValue(std::span<const huxerui::HttpHeader> headers,
            std::string_view name) {
  const auto lowered_name = Lower(name);
  for (const auto &header : headers) {
    if (Lower(header.name) == lowered_name)
      return header.value;
  }
  return std::nullopt;
}

std::vector<huxerui::HttpHeader> HeadersFrom(
    const std::vector<std::pair<std::string, std::string>> &values) {
  std::vector<huxerui::HttpHeader> headers;
  headers.reserve(values.size());
  for (const auto &[name, value] : values)
    headers.push_back({.name = name, .value = value});
  return headers;
}

// SimpleHttpClient.readStream: a response above the legacy limit fails the call
// instead of being buffered.
struct BufferedResponse final {
  int status{};
  std::vector<huxerui::HttpHeader> headers;
  std::string body;
};

huxerui::Task<application::WebToolResult<BufferedResponse>>
Send(huxerui::HttpClient &http, WebHttpRequestPlan plan) {
  huxerui::HttpRequest request{
      .url = std::move(plan.url),
      .method = plan.method == WebHttpMethod::post ? huxerui::HttpMethod::Post
                                                   : huxerui::HttpMethod::Get,
      .headers = HeadersFrom(plan.headers),
      .body = BytesFromString(plan.body),
      .timeout = kRequestTimeout,
  };
  auto opened = co_await http.SendStreamAsync(std::move(request));
  if (!opened.Succeeded()) {
    co_return std::unexpected(Error(WebToolErrorCode::transport,
                                    opened.Error().message));
  }
  auto stream = std::move(opened).Value();
  BufferedResponse response;
  response.status = stream.StatusCode();
  const auto headers = stream.Headers();
  response.headers.assign(headers.begin(), headers.end());
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kReadChunkBytes);
    if (!read.Succeeded()) {
      co_return std::unexpected(
          Error(WebToolErrorCode::transport, read.Error().message));
    }
    auto bytes = std::move(read).Value();
    if (bytes.empty())
      break;
    if (bytes.size() > kMaximumResponseBytes - response.body.size()) {
      co_return std::unexpected(Error(
          WebToolErrorCode::response_too_large,
          "Response body too large, current limit is 32 MB."));
    }
    response.body += StringFromBytes(bytes);
  }
  co_return response;
}

// WebSearchService.extractErrorText: prefer error.message, then message, then
// the raw body.
std::string ExtractErrorText(std::string_view body) {
  if (body.empty())
    return "Request failed";
  auto parsed = archive_json::Parse(body);
  const auto *object = parsed ? archive_json::AsObject(&*parsed) : nullptr;
  if (object != nullptr) {
    if (const auto *error = archive_json::AsObject(
            archive_json::Find(*object, "error"))) {
      if (const auto *message =
              archive_json::AsString(archive_json::Find(*error, "message"));
          message != nullptr && !message->empty()) {
        return *message;
      }
    }
    if (const auto *message =
            archive_json::AsString(archive_json::Find(*object, "message"));
        message != nullptr && !message->empty()) {
      return *message;
    }
  }
  if (body.size() <= kMaximumErrorBodyBytes)
    return std::string{body};
  return std::string{body.substr(0U, kMaximumErrorBodyBytes)};
}

bool IsSuccess(int status) noexcept { return status >= 200 && status < 300; }

} // namespace

HuxWebToolsGateway::HuxWebToolsGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument("HuxWebToolsGateway requires HttpClient");
}

huxerui::Task<application::WebToolResult<std::vector<WebSearchResultItem>>>
HuxWebToolsGateway::Search(application::WebSearchRequest request) {
  auto config = ValidateWebSearchConfiguration(std::move(request.config));
  if (!config)
    co_return std::unexpected(std::move(config.error()));
  auto plan =
      BuildWebSearchRequest(*config, request.query, request.limit);
  if (!plan)
    co_return std::unexpected(std::move(plan.error()));
  auto url = ValidateWebUrl(plan->url);
  if (!url)
    co_return std::unexpected(std::move(url.error()));
  plan->url = std::move(*url);

  auto response = co_await Send(*http_, std::move(*plan));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (!IsSuccess(response->status)) {
    co_return std::unexpected(Error(
        WebToolErrorCode::http_status,
        "Search API " + std::to_string(response->status) + ": " +
            ExtractErrorText(response->body)));
  }
  auto results = ParseWebSearchResponse(config->provider, response->body);
  if (!results)
    co_return std::unexpected(std::move(results.error()));
  const auto limit = static_cast<std::size_t>(
      ClampWebSearchResultLimit(request.limit));
  if (results->size() > limit)
    results->resize(limit);
  co_return std::move(*results);
}

huxerui::Task<application::WebToolResult<std::string>>
HuxWebToolsGateway::FetchPage(application::WebFetchRequest request) {
  auto url = ValidateWebUrl(request.url);
  if (!url)
    co_return std::unexpected(std::move(url.error()));
  auto plan = WebHttpRequestPlan{};
  plan.url = std::move(*url);
  plan.method = WebHttpMethod::get;
  plan.headers.emplace_back(
      "Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.6");
  plan.headers.emplace_back("User-Agent", "LineCode/1.0");

  auto response = co_await Send(*http_, std::move(plan));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (!IsSuccess(response->status)) {
    // huxerui::HttpResponse carries no reason phrase, so the legacy
    // ": <reason>" suffix of "Web request failed" is not reproduced.
    co_return std::unexpected(
        Error(WebToolErrorCode::http_status,
              "Web request failed " + std::to_string(response->status)));
  }
  const auto content_type = HeaderValue(response->headers, "content-type");
  const bool html =
      content_type.has_value() &&
      Lower(*content_type).find("html") != std::string::npos;
  auto text = html ? HtmlToPlainText(response->body) : response->body;
  auto compact = CompactWebText(text);
  if (compact.empty()) {
    co_return std::string{
        "Web page content is empty or could not extract body text."};
  }
  co_return TruncateWebText(compact,
                            ClampWebFetchCharacters(request.max_characters));
}

} // namespace linecode::infrastructure
