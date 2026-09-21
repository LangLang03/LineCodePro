#include "infrastructure/mcp_response_reader.h"

#include <algorithm>
#include <cctype>
#include <iterator>
#include <stdexcept>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

[[nodiscard]] std::string AsciiLower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(value, std::back_inserter(lowered),
                         [](const unsigned char character) {
                           return static_cast<char>(std::tolower(character));
                         });
  return lowered;
}

[[nodiscard]] McpResponseReadError TooLarge() {
  return {.code = McpResponseReadErrorCode::response_too_large,
          .message = "MCP response exceeds 4 MiB"};
}

[[nodiscard]] McpResponseReadErrorCode
CodeFor(const huxerui::HttpErrorCode code) noexcept {
  return code == huxerui::HttpErrorCode::Timeout
             ? McpResponseReadErrorCode::timeout
             : McpResponseReadErrorCode::transport;
}

[[nodiscard]] McpResponseReadErrorCode
CodeFor(const huxerui::IoErrorCode code) noexcept {
  return code == huxerui::IoErrorCode::Timeout
             ? McpResponseReadErrorCode::timeout
             : McpResponseReadErrorCode::transport;
}

} // namespace

McpResponseReader::McpResponseReader(const std::string_view content_type,
                                     std::string request_id,
                                     const std::size_t maximum_bytes)
    : request_id_(std::move(request_id)), maximum_bytes_(maximum_bytes) {
  if (request_id_.empty())
    throw std::invalid_argument("MCP response request id cannot be empty");
  if (maximum_bytes_ == 0)
    throw std::invalid_argument("MCP response size limit must be positive");
  const auto media_type = AsciiLower(content_type);
  if (media_type.find("text/event-stream") != std::string::npos)
    encoding_ = Encoding::event_stream;
}

std::optional<std::string>
McpResponseReader::MatchingJson(const std::string_view text) const {
  auto parsed = archive_json::Parse(text);
  const auto *object = parsed ? archive_json::AsObject(&*parsed) : nullptr;
  if (object == nullptr)
    return std::nullopt;
  const auto *id = archive_json::AsString(archive_json::Find(*object, "id"));
  if (id == nullptr || *id != request_id_)
    return std::nullopt;
  if (archive_json::Find(*object, "result") == nullptr &&
      archive_json::Find(*object, "error") == nullptr) {
    return std::nullopt;
  }
  return std::string{text};
}

std::optional<std::string> McpResponseReader::CompleteEvent() {
  if (event_data_.empty())
    return std::nullopt;
  std::string data;
  for (const auto &line : event_data_) {
    if (!data.empty())
      data.push_back('\n');
    data += line;
  }
  event_data_.clear();
  if (data == "[DONE]")
    return std::nullopt;
  return MatchingJson(data);
}

std::optional<std::string> McpResponseReader::ConsumeEventLines() {
  while (true) {
    const auto newline = line_buffer_.find('\n');
    if (newline == std::string::npos)
      break;
    auto line = line_buffer_.substr(0, newline);
    line_buffer_.erase(0, newline + 1U);
    if (!line.empty() && line.back() == '\r')
      line.pop_back();
    if (line.empty()) {
      if (auto completed = CompleteEvent())
        return completed;
      continue;
    }
    if (line.starts_with("data:")) {
      auto value = std::string_view{line}.substr(5U);
      if (!value.empty() && value.front() == ' ')
        value.remove_prefix(1U);
      event_data_.emplace_back(value);
    }
  }
  return std::nullopt;
}

std::expected<std::optional<std::string>, McpResponseReadError>
McpResponseReader::Consume(const std::string_view chunk) {
  if (chunk.size() > maximum_bytes_ - std::min(maximum_bytes_, body_.size())) {
    return std::unexpected(TooLarge());
  }
  body_.append(chunk);
  if (encoding_ == Encoding::json)
    return MatchingJson(body_);
  line_buffer_.append(chunk);
  return ConsumeEventLines();
}

std::expected<std::string, McpResponseReadError> McpResponseReader::Finish() {
  if (encoding_ == Encoding::event_stream) {
    if (!line_buffer_.empty()) {
      auto line = std::move(line_buffer_);
      line_buffer_.clear();
      if (!line.empty() && line.back() == '\r')
        line.pop_back();
      if (line.starts_with("data:")) {
        auto value = std::string_view{line}.substr(5U);
        if (!value.empty() && value.front() == ' ')
          value.remove_prefix(1U);
        event_data_.emplace_back(value);
      }
    }
    if (auto completed = CompleteEvent())
      return std::move(*completed);
  }
  return std::move(body_);
}

bool HttpHeaderNameEquals(const std::string_view left,
                          const std::string_view right) noexcept {
  return left.size() == right.size() &&
         std::ranges::equal(
             left, right,
             [](const unsigned char first, const unsigned char second) {
               return std::tolower(first) == std::tolower(second);
             });
}

void PutHttpHeader(std::vector<huxerui::HttpHeader> &headers, std::string name,
                   std::string value) {
  const auto found = std::ranges::find_if(headers, [&](const auto &header) {
    return HttpHeaderNameEquals(header.name, name);
  });
  if (found == headers.end()) {
    headers.push_back({.name = std::move(name), .value = std::move(value)});
  } else {
    found->name = std::move(name);
    found->value = std::move(value);
  }
}

std::string HttpHeaderValue(const std::span<const huxerui::HttpHeader> headers,
                            const std::string_view name) {
  const auto found = std::ranges::find_if(headers, [&](const auto &header) {
    return HttpHeaderNameEquals(header.name, name);
  });
  return found == headers.end() ? std::string{} : found->value;
}

huxerui::Task<std::expected<McpHttpResponse, McpResponseReadError>>
ReadMcpHttpResponse(huxerui::HttpResult<huxerui::HttpResponseStream> opened,
                    std::string request_id, const std::size_t maximum_bytes) {
  if (!opened.Succeeded()) {
    co_return std::unexpected(McpResponseReadError{
        .code = CodeFor(opened.Error().code),
        .message = opened.Error().message,
    });
  }
  auto stream = std::move(opened).Value();
  McpHttpResponse response{
      .status = stream.StatusCode(),
      .headers = std::vector<huxerui::HttpHeader>(stream.Headers().begin(),
                                                  stream.Headers().end()),
      .body = {},
  };
  McpResponseReader reader{HttpHeaderValue(response.headers, "Content-Type"),
                           std::move(request_id), maximum_bytes};
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kMcpHttpReadChunkBytes);
    if (!read.Succeeded()) {
      co_return std::unexpected(McpResponseReadError{
          .code = CodeFor(read.Error().code),
          .message = read.Error().message,
      });
    }
    auto chunk = std::move(read).Value();
    if (chunk.empty()) {
      auto finished = reader.Finish();
      if (!finished)
        co_return std::unexpected(std::move(finished.error()));
      response.body = std::move(*finished);
      co_return response;
    }
    auto consumed = reader.Consume(std::string_view{
        reinterpret_cast<const char *>(chunk.data()), chunk.size()});
    if (!consumed)
      co_return std::unexpected(std::move(consumed.error()));
    if (consumed->has_value()) {
      // Destroying the unfinished HttpResponseStream cancels the still-open SSE
      // request after the matching JSON-RPC response has been obtained.
      response.body = std::move(**consumed);
      co_return response;
    }
  }
}

} // namespace linecode::infrastructure
