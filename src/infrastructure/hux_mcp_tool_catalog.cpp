#include "infrastructure/hux_mcp_tool_catalog.h"

#include <chrono>
#include <format>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "infrastructure/extension_config_codec.h"
#include "infrastructure/mcp_response_reader.h"

namespace linecode::infrastructure {
namespace {

huxerui::Bytes BytesFromString(std::string_view value) {
  const auto *begin = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(begin, begin + value.size());
}

application::McpToolCatalogError Error(std::string message) {
  return {.message = std::move(message)};
}

} // namespace

HuxMcpToolCatalog::HuxMcpToolCatalog(std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument("HuxMcpToolCatalog requires HttpClient");
}

huxerui::Task<std::expected<std::vector<domain::McpToolSummary>,
                            application::McpToolCatalogError>>
HuxMcpToolCatalog::Query(
    std::string url, std::vector<domain::McpRequestHeader> request_headers) {
  try {
    std::vector<huxerui::HttpHeader> headers{
        {.name = "Accept", .value = "application/json, text/event-stream"},
        {.name = "Content-Type", .value = "application/json"},
    };
    for (auto &header : request_headers) {
      if (!header.name.empty())
        PutHttpHeader(headers, std::move(header.name), std::move(header.value));
    }
    const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                         std::chrono::system_clock::now().time_since_epoch())
                         .count();
    const auto request_id = std::format("linecode_{}", now);
    const auto body = std::format(
        R"({{"jsonrpc":"2.0","id":"linecode_{}","method":"tools/list","params":{{}}}})",
        now);
    auto opened = co_await http_->SendStreamAsync({
        .url = std::move(url),
        .method = huxerui::HttpMethod::Post,
        .headers = std::move(headers),
        .body = BytesFromString(body),
        .timeout = std::chrono::seconds{30},
    });
    auto response = co_await ReadMcpHttpResponse(std::move(opened), request_id);
    if (!response)
      co_return std::unexpected(Error(std::move(response.error().message)));
    if (response->status < 200 || response->status >= 300) {
      auto error_body = std::move(response->body);
      if (error_body.size() > 4096U)
        error_body.resize(4096U);
      co_return std::unexpected(Error(
          std::format("HTTP {}{}", response->status,
                      error_body.empty() ? std::string{} : ": " + error_body)));
    }
    auto decoded = DecodeMcpToolResponse(response->body);
    if (!decoded)
      co_return std::unexpected(Error(std::move(decoded.error())));
    co_return std::move(*decoded);
  } catch (const std::exception &error) {
    co_return std::unexpected(Error(error.what()));
  }
}

} // namespace linecode::infrastructure
