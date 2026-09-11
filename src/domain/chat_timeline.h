#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <variant>

namespace linecode::domain {

enum class ReasoningKind : std::uint8_t {
  thinking,
  summary,
};

enum class ToolCallStatus : std::uint8_t {
  requested,
  awaiting_review,
  running,
  completed,
  failed,
  rejected,
};

struct ChatToolCall final {
  std::string id;
  std::string name;
  std::string arguments_json{"{}"};
  ToolCallStatus status{ToolCallStatus::requested};
  std::int64_t created_at_millis{};
  std::int64_t duration_millis{};
  std::string error_message;

  bool operator==(const ChatToolCall &) const = default;
};

struct ChatToolResult final {
  std::string call_id;
  std::string name;
  std::string content;
  bool error{};
  std::string diff_id;
  std::string review_state;
  std::string review_message;

  bool operator==(const ChatToolResult &) const = default;
};

struct AssistantReasoningEvent final {
  std::size_t turn_index{};
  std::string text;
  ReasoningKind kind{ReasoningKind::thinking};
  bool starts_new_segment{};

  bool operator==(const AssistantReasoningEvent &) const = default;
};

struct AssistantTextEvent final {
  std::size_t turn_index{};
  std::string text;

  bool operator==(const AssistantTextEvent &) const = default;
};

struct AssistantToolEvent final {
  std::size_t turn_index{};
  ChatToolCall call;
  std::optional<ChatToolResult> result;

  bool operator==(const AssistantToolEvent &) const = default;
};

using AssistantTimelineEvent =
    std::variant<AssistantReasoningEvent, AssistantTextEvent,
                 AssistantToolEvent>;

} // namespace linecode::domain
