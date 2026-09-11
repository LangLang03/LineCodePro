#include "infrastructure/hux_mcp_tool_invoker.h"

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstddef>
#include <format>
#include <optional>
#include <stdexcept>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/mcp_protocol_codec.h"

namespace linecode::infrastructure {
namespace {

constexpr std::size_t kMaximumResponseBytes = 4U * 1024U * 1024U;
constexpr std::size_t kHttpReadChunkBytes = 64U * 1024U;
constexpr std::string_view kProtocolVersion = "2025-03-26";

struct Response final {
  int status{};
  std::vector<huxerui::HttpHeader> headers;
  std::string body;
};

using InvocationError = application::McpToolInvocationError;
using InvocationErrorCode = application::McpToolInvocationErrorCode;

[[nodiscard]] InvocationError Error(InvocationErrorCode code,
                                    std::string message) {
  return {.code = code, .message = std::move(message)};
}

[[nodiscard]] huxerui::Bytes BytesFromString(std::string_view value) {
  const auto *begin = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(begin, begin + value.size());
}

[[nodiscard]] bool HeaderNameEquals(std::string_view left,
                                    std::string_view right) {
  return left.size() == right.size() &&
         std::ranges::equal(left, right, [](unsigned char first,
                                            unsigned char second) {
           return std::tolower(first) == std::tolower(second);
         });
}

void PutHeader(std::vector<huxerui::HttpHeader> &headers,
               std::string name, std::string value) {
  const auto found = std::ranges::find_if(headers, [&](const auto &header) {
    return HeaderNameEquals(header.name, name);
  });
  if (found == headers.end()) {
    headers.push_back({.name = std::move(name), .value = std::move(value)});
  } else {
    found->name = std::move(name);
    found->value = std::move(value);
  }
}

[[nodiscard]] std::vector<huxerui::HttpHeader>
RequestHeaders(const domain::McpExtension &extension,
               std::string_view session_id = {}) {
  std::vector<huxerui::HttpHeader> headers{
      {.name = "Accept", .value = "application/json, text/event-stream"},
      {.name = "Content-Type", .value = "application/json"},
      {.name = "Mcp-Protocol-Version", .value = std::string{kProtocolVersion}},
  };
  if (!session_id.empty())
    headers.push_back(
        {.name = "Mcp-Session-Id", .value = std::string{session_id}});
  for (const auto &header : extension.request_headers) {
    if (!header.name.empty())
      PutHeader(headers, header.name, header.value);
  }
  return headers;
}

[[nodiscard]] std::string HeaderValue(
    const std::vector<huxerui::HttpHeader> &headers, std::string_view name) {
  const auto found = std::ranges::find_if(headers, [&](const auto &header) {
    return HeaderNameEquals(header.name, name);
  });
  return found == headers.end() ? std::string{} : found->value;
}

[[nodiscard]] std::string RequestId(std::string_view prefix) {
  const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
  return std::format("{}_{}", prefix, now);
}

huxerui::Task<std::expected<Response, InvocationError>>
Send(std::shared_ptr<huxerui::HttpClient> http, std::string url,
     std::vector<huxerui::HttpHeader> headers, std::string body,
     std::chrono::seconds timeout) {
  auto opened = co_await http->SendStreamAsync({
      .url = std::move(url),
      .method = huxerui::HttpMethod::Post,
      .headers = std::move(headers),
      .body = BytesFromString(body),
      .timeout = timeout,
  });
  if (!opened.Succeeded()) {
    co_return std::unexpected(
        Error(InvocationErrorCode::transport, opened.Error().message));
  }
  auto stream = std::move(opened).Value();
  Response response{.status = stream.StatusCode(),
                    .headers = std::vector<huxerui::HttpHeader>(
                        stream.Headers().begin(), stream.Headers().end()),
                    .body = {}};
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kHttpReadChunkBytes);
    if (!read.Succeeded()) {
      co_return std::unexpected(
          Error(InvocationErrorCode::transport, read.Error().message));
    }
    auto chunk = std::move(read).Value();
    if (chunk.empty())
      break;
    if (chunk.size() >
        kMaximumResponseBytes -
            std::min(kMaximumResponseBytes, response.body.size())) {
      co_return std::unexpected(Error(InvocationErrorCode::response_too_large,
                                      "MCP response exceeds 4 MiB"));
    }
    response.body.append(reinterpret_cast<const char *>(chunk.data()),
                         chunk.size());
  }
  co_return response;
}

} // namespace

HuxMcpToolInvoker::HuxMcpToolInvoker(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument("HuxMcpToolInvoker requires HttpClient");
}

huxerui::Task<std::expected<application::McpToolInvocationResult,
                            application::McpToolInvocationError>>
HuxMcpToolInvoker::Invoke(domain::McpExtension extension,
                          domain::McpToolSummary tool,
                          std::string arguments_json) {
  try {
    auto call_body = BuildMcpCallRequest(RequestId("linecode"), tool.name,
                                         arguments_json);
    if (!call_body) {
      co_return std::unexpected(Error(InvocationErrorCode::invalid_arguments,
                                      std::move(call_body.error())));
    }

    const auto initialize_body =
        BuildMcpInitializeRequest(RequestId("linecode_init"));
    auto initialized = co_await Send(http_, extension.url,
                                     RequestHeaders(extension),
                                     initialize_body,
                                     std::chrono::seconds{30});
    if (!initialized)
      co_return std::unexpected(std::move(initialized.error()));

    // LineCode supports stateless MCP endpoints: non-2xx initialize responses
    // deliberately fall through to tools/call without a session header.
    const auto session_id =
        initialized->status >= 200 && initialized->status < 300
            ? HeaderValue(initialized->headers, "Mcp-Session-Id")
            : std::string{};
    auto called = co_await Send(http_, std::move(extension.url),
                                RequestHeaders(extension, session_id),
                                std::move(*call_body),
                                std::chrono::seconds{60});
    if (!called)
      co_return std::unexpected(std::move(called.error()));
    if (called->status < 200 || called->status >= 300) {
      auto body = std::move(called->body);
      if (body.size() > 4096U)
        body.resize(4096U);
      co_return std::unexpected(Error(
          InvocationErrorCode::http_status,
          std::format("{}: {}", called->status, body)));
    }
    auto decoded = DecodeMcpCallResponse(called->body);
    co_return application::McpToolInvocationResult{
        .content = std::move(decoded.content), .error = decoded.error};
  } catch (const std::exception &error) {
    co_return std::unexpected(
        Error(InvocationErrorCode::transport, error.what()));
  }
}

} // namespace linecode::infrastructure
