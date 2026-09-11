#pragma once

#include <cstddef>
#include <cstdint>
#include <expected>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/ports/completion_gateway.h"

namespace linecode::infrastructure {

struct CompletionProtocolCodecError final {
  std::string message;

  bool operator==(const CompletionProtocolCodecError &) const = default;
};

struct CompletionProtocolWireRequest final {
  std::string endpoint;
  std::vector<std::pair<std::string, std::string>> headers;
  std::string body;
};

struct CompletionProtocolStreamChunk final {
  bool done{};
  std::optional<std::string> text_delta;
  std::optional<std::string> final_text;
  std::vector<application::CompletionReasoningDelta> reasoning_deltas{};
  std::optional<std::string> final_reasoning{};
  struct ToolCallDelta final {
    std::size_t index{};
    std::optional<std::string> id;
    std::optional<std::string> name;
    std::string arguments_delta;
  };
  std::vector<ToolCallDelta> tool_call_deltas;
  std::vector<application::CompletionToolCall> final_tool_calls;
  std::int64_t input_tokens{};
  std::int64_t output_tokens{};
};

struct CompletionProtocolCodec final {
  domain::ModelProtocol protocol;
  std::expected<CompletionProtocolWireRequest, CompletionProtocolCodecError> (
      *encode)(const application::CompletionRequest &, std::string_view);
  std::expected<application::CompletionResponse, CompletionProtocolCodecError> (
      *decode_response)(std::string_view);
  std::expected<CompletionProtocolStreamChunk, CompletionProtocolCodecError> (
      *decode_stream_event)(std::string_view);
};

[[nodiscard]] const CompletionProtocolCodec *
FindCompletionProtocolCodec(domain::ModelProtocol protocol) noexcept;

} // namespace linecode::infrastructure
