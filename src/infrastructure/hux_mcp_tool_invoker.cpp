#include "infrastructure/hux_mcp_tool_invoker.h"

#include <chrono>
#include <format>
#include <stdexcept>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/mcp_protocol_codec.h"
#include "infrastructure/mcp_response_reader.h"

namespace linecode::infrastructure {
namespace {

constexpr std::string_view kProtocolVersion = "2025-03-26";

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
      PutHttpHeader(headers, header.name, header.value);
  }
  return headers;
}

[[nodiscard]] std::string RequestId(std::string_view prefix) {
  const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
  return std::format("{}_{}", prefix, now);
}

huxerui::Task<std::expected<McpHttpResponse, InvocationError>>
Send(std::shared_ptr<huxerui::HttpClient> http, std::string url,
     std::vector<huxerui::HttpHeader> headers, std::string body,
     std::string request_id, std::chrono::seconds timeout) {
  auto opened = co_await http->SendStreamAsync({
      .url = std::move(url),
      .method = huxerui::HttpMethod::Post,
      .headers = std::move(headers),
      .body = BytesFromString(body),
      .timeout = timeout,
  });
  auto response =
      co_await ReadMcpHttpResponse(std::move(opened), std::move(request_id));
  if (response)
    co_return std::move(*response);
  co_return std::unexpected(Error(
      response.error().code == McpResponseReadErrorCode::response_too_large
          ? InvocationErrorCode::response_too_large
          : InvocationErrorCode::transport,
      std::move(response.error().message)));
}

} // namespace

HuxMcpToolInvoker::HuxMcpToolInvoker(std::shared_ptr<huxerui::HttpClient> http)
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
    const auto call_id = RequestId("linecode");
    auto call_body = BuildMcpCallRequest(call_id, tool.name, arguments_json);
    if (!call_body) {
      co_return std::unexpected(Error(InvocationErrorCode::invalid_arguments,
                                      std::move(call_body.error())));
    }

    const auto initialize_id = RequestId("linecode_init");
    const auto initialize_body = BuildMcpInitializeRequest(initialize_id);
    auto initialized =
        co_await Send(http_, extension.url, RequestHeaders(extension),
                      initialize_body, initialize_id, std::chrono::seconds{30});
    if (!initialized)
      co_return std::unexpected(std::move(initialized.error()));

    // LineCode supports stateless MCP endpoints: non-2xx initialize responses
    // deliberately fall through to tools/call without a session header.
    const auto session_id =
        initialized->status >= 200 && initialized->status < 300
            ? HttpHeaderValue(initialized->headers, "Mcp-Session-Id")
            : std::string{};
    auto called = co_await Send(
        http_, std::move(extension.url), RequestHeaders(extension, session_id),
        std::move(*call_body), call_id, std::chrono::seconds{60});
    if (!called)
      co_return std::unexpected(std::move(called.error()));
    if (called->status < 200 || called->status >= 300) {
      auto body = std::move(called->body);
      if (body.size() > 4096U)
        body.resize(4096U);
      co_return std::unexpected(
          Error(InvocationErrorCode::http_status,
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
