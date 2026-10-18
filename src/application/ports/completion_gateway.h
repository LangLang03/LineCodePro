#pragma once

#include <cstddef>
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
#include "domain/chat_image.h"
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
  // Mirrors `RegisteredTool::allowed_in_read_only`. The prompt composer renders
  // the read-only allow-list from this instead of a hardcoded name list that
  // drifted away from what the permission layer actually permits.
  bool allowed_in_read_only{};

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
  // Recorded file change, when the tool produced one.
  std::string diff_id{};

  bool operator==(const CompletionToolResult &) const = default;
};

struct CompletionMessage final {
  CompletionRole role{CompletionRole::user};
  std::string content;
  std::string reasoning_content{};
  std::vector<CompletionToolCall> tool_calls{};
  std::optional<CompletionToolResult> tool_result{};
  // An image the user attached to this message. Legacy carried it alongside
  // the text through the whole send path (`onSendWithImage`), so each protocol
  // encoder can emit its own multimodal part.
  std::optional<domain::ChatImage> image{};
  // The conversation row this message was built from, or 0 for a message that
  // exists only inside the running tool loop (the in-flight assistant and tool
  // group). Mid-loop compaction has to name the rows it summarized, and
  // deriving that from position would depend on the request layout staying in
  // step with the session -- provenance is exact and survives any rebuilding.
  std::uint64_t source_id{};

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
  // The connection failed, or the stream ended before the response was
  // complete. Retryable.
  transport,
  // The whole-operation deadline elapsed. Kept separate from `transport` so the
  // retry policy and the user-facing reason can tell a stall from a reset.
  timeout,
  // A response arrived with a non-2xx status. `http_status` carries the code.
  http_status,
  // The response could not be decoded; retrying an identical request cannot fix
  // a protocol or payload problem.
  decode,
};

struct CompletionError final {
  CompletionErrorCode code{CompletionErrorCode::transport};
  std::string message;
  int http_status{};
  // `Retry-After` from the failed response, when the transport exposed it.
  std::optional<std::int64_t> retry_after_seconds{};
  // What the failed attempt already did. A retry replays the whole tool loop
  // from the same request snapshot, so the caller must refuse to retry once a
  // state-changing tool has run.
  std::size_t executed_tool_calls{};
  std::size_t mutating_tool_calls{};

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
