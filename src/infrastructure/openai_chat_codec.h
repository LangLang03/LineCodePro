#pragma once

#include <cstddef>
#include <expected>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "application/ports/completion_gateway.h"

namespace linecode::infrastructure {

struct OpenAiCodecError final {
  std::string message;
  std::size_t offset{};

  bool operator==(const OpenAiCodecError &) const = default;
};

struct OpenAiStreamChunk final {
  bool done{};
  std::optional<std::string> text_delta;
  std::optional<std::string> reasoning_delta{};
  struct ToolCallDelta final {
    std::size_t index{};
    std::optional<std::string> id;
    std::optional<std::string> name;
    std::string arguments_delta;
  };
  std::vector<ToolCallDelta> tool_call_deltas;
  // Compatible endpoints often put the usage on the last chunk, whose
  // `choices` array is empty; legacy `OpenAiCompatibleProtocol` read it before
  // looking at choices for exactly that reason.
  std::int64_t input_tokens{};
  std::int64_t output_tokens{};

  bool operator==(const OpenAiStreamChunk &) const = default;
};

[[nodiscard]] std::string OpenAiChatEndpoint(std::string_view base_url);
[[nodiscard]] std::string
EncodeOpenAiChatRequest(const application::CompletionRequest &request);
[[nodiscard]] std::expected<application::CompletionResponse, OpenAiCodecError>
DecodeOpenAiChatResponse(std::string_view json);
[[nodiscard]] std::expected<OpenAiStreamChunk, OpenAiCodecError>
DecodeOpenAiChatStreamEvent(std::string_view data);

} // namespace linecode::infrastructure
