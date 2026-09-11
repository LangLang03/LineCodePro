#pragma once

#include <cstdint>
#include <expected>
#include <functional>
#include <optional>
#include <string>
#include <variant>
#include <vector>

#include <huxerui/task.h>

#include "application/tool_result_display_policy.h"
#include "domain/behavior_settings.h"
#include "domain/model_config.h"

namespace linecode::application {

enum class CompletionRole : std::uint8_t {
  system,
  user,
  assistant,
  tool,
};

struct CompletionTool final {
  std::string name;
  std::string description;
  std::string parameters_json;

  bool operator==(const CompletionTool &) const = default;
};

struct CompletionToolCall final {
  std::string id;
  std::string name;
  std::string arguments_json;

  bool operator==(const CompletionToolCall &) const = default;
};

struct CompletionToolResult final {
  std::string call_id;
  std::string name;
  std::string content;
  bool error{};

  bool operator==(const CompletionToolResult &) const = default;
};

struct CompletionMessage final {
  CompletionRole role{CompletionRole::user};
  std::string content;
  std::string reasoning_content{};
  std::vector<CompletionToolCall> tool_calls{};
  std::optional<CompletionToolResult> tool_result{};

  bool operator==(const CompletionMessage &) const = default;

  [[nodiscard]] static CompletionMessage
  Assistant(std::string content, std::vector<CompletionToolCall> tool_calls,
            std::string reasoning_content = {}) {
    return {.role = CompletionRole::assistant,
            .content = std::move(content),
            .reasoning_content = std::move(reasoning_content),
            .tool_calls = std::move(tool_calls),
            .tool_result = std::nullopt};
  }

  [[nodiscard]] static CompletionMessage Tool(CompletionToolResult result) {
    return {.role = CompletionRole::tool,
            .content = {},
            .tool_calls = {},
            .tool_result = std::move(result)};
  }
};

struct CompletionRequest final {
  domain::ModelConfig model;
  std::vector<CompletionMessage> messages;
  std::vector<CompletionTool> tools;
  domain::ReasoningEffort reasoning_effort{domain::ReasoningEffort::medium};
  bool preserve_reasoning{};
  bool stream{true};
  std::string permission_scope;
};

struct CompletionResponse final {
  std::string text;
  std::string reasoning_content{};
  std::vector<CompletionToolCall> tool_calls;
  std::int64_t input_tokens{};
  std::int64_t output_tokens{};

  bool operator==(const CompletionResponse &) const = default;
};

enum class CompletionReasoningKind : std::uint8_t {
  thinking,
  summary,
};

struct CompletionTextDelta final {
  std::size_t turn_index{};
  std::string text;

  bool operator==(const CompletionTextDelta &) const = default;
};

struct CompletionReasoningDelta final {
  std::size_t turn_index{};
  std::string text;
  CompletionReasoningKind kind{CompletionReasoningKind::thinking};
  bool starts_new_segment{};

  bool operator==(const CompletionReasoningDelta &) const = default;
};

enum class CompletionToolCallStatus : std::uint8_t {
  requested,
  awaiting_review,
  running,
  completed,
  failed,
  rejected,
};

struct CompletionToolCallEvent final {
  std::size_t turn_index{};
  CompletionToolCall call;
  CompletionToolCallStatus status{CompletionToolCallStatus::requested};
  std::optional<CompletionToolResult> result;
  ToolResultDisplayProjection display;
  std::int64_t created_at_millis{};
  std::int64_t duration_millis{};

  bool operator==(const CompletionToolCallEvent &) const = default;
};

using CompletionEvent =
    std::variant<CompletionTextDelta, CompletionReasoningDelta,
                 CompletionToolCallEvent>;

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
  // One typed stream for assistant prose, reasoning and tool lifecycle.
  std::function<void(const CompletionEvent &)> on_event{};
  enum class ToolReviewDecision : std::uint8_t {
    reject,
    allow_once,
    allow_always,
  };
  struct ToolReviewRequest final {
    CompletionToolCall call;
    bool can_allow_always{};

    bool operator==(const ToolReviewRequest &) const = default;
  };
  std::function<huxerui::Task<ToolReviewDecision>(ToolReviewRequest)>
      on_tool_review;
};

class CompletionGateway {
public:
  virtual ~CompletionGateway() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<CompletionResponse, CompletionError>>
  Complete(CompletionRequest request, CompletionObserver observer) = 0;
};

} // namespace linecode::application
