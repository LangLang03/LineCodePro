#include "infrastructure/hux_mcp_tool_catalog.h"

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <format>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "infrastructure/extension_config_codec.h"

namespace linecode::infrastructure {
namespace {

constexpr std::size_t kMaximumResponseBytes = 4U * 1024U * 1024U;
constexpr std::size_t kHttpReadChunkBytes = 64U * 1024U;

huxerui::Bytes BytesFromString(std::string_view value) {
  const auto *begin = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(begin, begin + value.size());
}

application::McpToolCatalogError Error(std::string message) {
  return {.message = std::move(message)};
}

void PutHeader(std::vector<huxerui::HttpHeader> &headers,
               domain::McpRequestHeader source) {
  auto found =
      std::ranges::find(headers, source.name, &huxerui::HttpHeader::name);
  if (found == headers.end()) {
    headers.push_back(
        {.name = std::move(source.name), .value = std::move(source.value)});
  } else {
    found->value = std::move(source.value);
  }
}

huxerui::Task<std::expected<std::string, application::McpToolCatalogError>>
ReadResponse(huxerui::HttpResult<huxerui::HttpResponseStream> opened) {
  if (!opened.Succeeded())
    co_return std::unexpected(Error(opened.Error().message));
  auto stream = std::move(opened).Value();
  const int status = stream.StatusCode();
  std::string body;
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kHttpReadChunkBytes);
    if (!read.Succeeded())
      co_return std::unexpected(Error(read.Error().message));
    auto chunk = std::move(read).Value();
    if (chunk.empty())
      break;
    if (chunk.size() >
        kMaximumResponseBytes - std::min(kMaximumResponseBytes, body.size())) {
      co_return std::unexpected(Error("MCP response exceeds 4 MiB"));
    }
    body.append(reinterpret_cast<const char *>(chunk.data()), chunk.size());
  }
  if (status < 200 || status >= 300) {
    if (body.size() > 4096U)
      body.resize(4096U);
    co_return std::unexpected(Error(std::format(
        "HTTP {}{}", status, body.empty() ? std::string{} : ": " + body)));
  }
  co_return body;
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
        PutHeader(headers, std::move(header));
    }
    const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                         std::chrono::system_clock::now().time_since_epoch())
                         .count();
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
    auto response = co_await ReadResponse(std::move(opened));
    if (!response)
      co_return std::unexpected(std::move(response.error()));
    auto decoded = DecodeMcpToolResponse(*response);
    if (!decoded)
      co_return std::unexpected(Error(std::move(decoded.error())));
    co_return std::move(*decoded);
  } catch (const std::exception &error) {
    co_return std::unexpected(Error(error.what()));
  }
}

} // namespace linecode::infrastructure
