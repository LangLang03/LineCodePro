#pragma once

#include <cstddef>
#include <expected>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/http.h>
#include <huxerui/task.h>

namespace linecode::infrastructure {

inline constexpr std::size_t kMaximumMcpResponseBytes = 4U * 1024U * 1024U;
inline constexpr std::size_t kMcpHttpReadChunkBytes = 64U * 1024U;

enum class McpResponseReadErrorCode {
  transport,
  timeout,
  response_too_large,
};

struct McpResponseReadError final {
  McpResponseReadErrorCode code{McpResponseReadErrorCode::transport};
  std::string message;

  bool operator==(const McpResponseReadError &) const = default;
};

struct McpHttpResponse final {
  int status{};
  std::vector<huxerui::HttpHeader> headers;
  std::string body;
};

// Incrementally recognizes one JSON-RPC response. For SSE, a response becomes
// available only after the complete event delimiter has arrived; multiple data
// fields are joined with newlines as required by the event-stream format.
class McpResponseReader final {
public:
  McpResponseReader(std::string_view content_type, std::string request_id,
                    std::size_t maximum_bytes = kMaximumMcpResponseBytes);

  [[nodiscard]] std::expected<std::optional<std::string>, McpResponseReadError>
  Consume(std::string_view chunk);

  // Called only at EOF. It keeps compatibility with buffered/plain responses
  // while the streaming path can finish earlier through Consume().
  [[nodiscard]] std::expected<std::string, McpResponseReadError> Finish();

private:
  enum class Encoding { json, event_stream };

  [[nodiscard]] std::optional<std::string>
  MatchingJson(std::string_view text) const;
  [[nodiscard]] std::optional<std::string> CompleteEvent();
  [[nodiscard]] std::optional<std::string> ConsumeEventLines();

  Encoding encoding_{Encoding::json};
  std::string request_id_;
  std::size_t maximum_bytes_{};
  std::string body_;
  std::string line_buffer_;
  std::vector<std::string> event_data_;
};

[[nodiscard]] bool HttpHeaderNameEquals(std::string_view left,
                                        std::string_view right) noexcept;

void PutHttpHeader(std::vector<huxerui::HttpHeader> &headers, std::string name,
                   std::string value);

[[nodiscard]] std::string
HttpHeaderValue(std::span<const huxerui::HttpHeader> headers,
                std::string_view name);

[[nodiscard]] huxerui::Task<
    std::expected<McpHttpResponse, McpResponseReadError>>
ReadMcpHttpResponse(huxerui::HttpResult<huxerui::HttpResponseStream> opened,
                    std::string request_id,
                    std::size_t maximum_bytes = kMaximumMcpResponseBytes);

} // namespace linecode::infrastructure
