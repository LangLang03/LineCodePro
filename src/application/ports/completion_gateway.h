#pragma once

#include <cstdint>
#include <expected>
#include <functional>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/model_config.h"

namespace linecode::application {

enum class CompletionRole : std::uint8_t {
  user,
  assistant,
};

struct CompletionMessage final {
  CompletionRole role{CompletionRole::user};
  std::string content;

  bool operator==(const CompletionMessage &) const = default;
};

struct CompletionRequest final {
  domain::ModelConfig model;
  std::vector<CompletionMessage> messages;
  bool stream{true};
};

struct CompletionResponse final {
  std::string text;
  std::int64_t input_tokens{};
  std::int64_t output_tokens{};

  bool operator==(const CompletionResponse &) const = default;
};

enum class CompletionErrorCode : std::uint8_t {
  unsupported_protocol,
  invalid_configuration,
  transport,
  http_status,
  decode,
};

struct CompletionError final {
  CompletionErrorCode code{CompletionErrorCode::transport};
  std::string message;
  int http_status{};

  bool operator==(const CompletionError &) const = default;
};

struct CompletionObserver final {
  std::function<void(std::string)> on_text_delta;
};

class CompletionGateway {
public:
  virtual ~CompletionGateway() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<CompletionResponse, CompletionError>>
  Complete(CompletionRequest request, CompletionObserver observer) = 0;
};

} // namespace linecode::application
