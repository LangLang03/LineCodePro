#pragma once

#include <cstddef>
#include <expected>
#include <optional>
#include <string>
#include <string_view>

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

  bool operator==(const OpenAiStreamChunk &) const = default;
};

[[nodiscard]] std::string
OpenAiChatEndpoint(std::string_view base_url);
[[nodiscard]] std::string
EncodeOpenAiChatRequest(const application::CompletionRequest &request);
[[nodiscard]] std::expected<application::CompletionResponse, OpenAiCodecError>
DecodeOpenAiChatResponse(std::string_view json);
[[nodiscard]] std::expected<OpenAiStreamChunk, OpenAiCodecError>
DecodeOpenAiChatStreamEvent(std::string_view data);

} // namespace linecode::infrastructure
