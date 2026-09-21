#pragma once

#include <cstdint>
#include <memory>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "domain/app_state.h"

namespace linecode::application {
class AgentResultReader;
}

namespace linecode::presentation {

enum class ToolTimelineVisualKind : std::uint8_t {
  shell,
  read,
  write,
  remove,
  todo,
  agent,
  agent_pipeline,
  generic,
};

// Semantic icon tokens are resolved to platform assets by the view layer.
// Keeping the token in the registration prevents tool-name conditionals from
// leaking into renderers when a new read-family tool is added.
enum class ToolTimelineIconKind : std::uint8_t {
  file,
  folder,
  search,
  globe,
  paintbrush,
  sparkles,
  bot,
  book_open,
};

struct ToolTimelineTodoItem final {
  enum class State : std::uint8_t { pending, in_progress, completed };

  std::string content;
  State state{State::pending};

  bool operator==(const ToolTimelineTodoItem &) const = default;
};

struct AgentRunTimelinePresentation final {
  std::string id{};
  std::string type{};
  std::string description{};
  std::vector<std::string> dependencies{};
  std::string status{};
  std::string thinking{};
  std::string output{};
  std::vector<domain::AssistantToolEvent> tool_calls{};
  bool failed{};

  bool operator==(const AgentRunTimelinePresentation &) const = default;
};

struct ToolTimelinePresentation final {
  ToolTimelineVisualKind visual{ToolTimelineVisualKind::generic};
  ToolTimelineIconKind icon{ToolTimelineIconKind::file};
  domain::ToolCallStatus status{domain::ToolCallStatus::requested};
  std::string title{};
  std::string detail{};
  std::string input_detail{};
  std::string output_detail{};
  std::string auxiliary{};
  std::string agent_id{};
  // Recorded file change for write-family tools; empty when the tool produced
  // no revertable change. The `{}` keeps `-Wmissing-field-initializers` quiet
  // at designated-initializer call sites.
  // Tool call this card belongs to; the review action is keyed by it, exactly
  // like the legacy `ToolReviewController.review(toolCallId, ...)`.
  std::string tool_call_id{};
  std::string diff_id{};
  // "accepted" / "rejected" / empty while still pending review.
  std::string review_state{};
  std::string review_message{};
  std::vector<ToolTimelineTodoItem> todo_items{};
  std::vector<AgentRunTimelinePresentation> agent_runs{};
  int item_count{};
  int completed_count{};
  int running_count{};
  int failed_count{};
  int tool_call_count{};
  bool running{};
  bool failed{};
  bool expandable{true};
  bool initially_expanded{};
  bool visible{true};

  bool operator==(const ToolTimelinePresentation &) const = default;
};

enum class ToolNameMatch : std::uint8_t { exact, prefix };

struct ToolTimelineRendererRegistration final {
  std::string name;
  ToolNameMatch match{ToolNameMatch::exact};
  ToolTimelineVisualKind visual{ToolTimelineVisualKind::generic};
  ToolTimelineIconKind icon{ToolTimelineIconKind::file};
};

// Runtime registry keeps the timeline open for extension without name-based UI
// conditionals. Registrations are validated once and contract-testable.
class ToolTimelineRendererRegistry final {
public:
  explicit ToolTimelineRendererRegistry(
      std::vector<ToolTimelineRendererRegistration> registrations);

  [[nodiscard]] ToolTimelineVisualKind
  Resolve(std::string_view tool_name) const noexcept;

  [[nodiscard]] ToolTimelineIconKind
  ResolveIcon(std::string_view tool_name) const noexcept;

private:
  std::vector<ToolTimelineRendererRegistration> registrations_;
};

[[nodiscard]] const ToolTimelineRendererRegistry &
DefaultToolTimelineRendererRegistry();

struct ToolTimelineLayoutMetrics final {
  float header_height;
  float icon_slot_width;
  float icon_slot_height;
  float icon_width;
  float icon_height;
  float title_size;
  float title_leading_margin;
  float detail_max_height;
  float detail_radius;
  float detail_text_size;
  float detail_horizontal_padding;
  float detail_vertical_padding;

  bool operator==(const ToolTimelineLayoutMetrics &) const = default;
};

[[nodiscard]] constexpr ToolTimelineLayoutMetrics
ToolTimelineMetrics(ToolTimelineVisualKind visual) noexcept {
  switch (visual) {
  case ToolTimelineVisualKind::remove:
    return {48, 24, 32, 24, 16, 14, 6, 200, 12, 13, 14, 12};
  case ToolTimelineVisualKind::write:
    return {48, 24, 32, 24, 16, 14, 6, 224, 12, 13, 14, 10};
  case ToolTimelineVisualKind::agent:
    return {48, 28, 28, 28, 14, 14, 8, 400, 8, 14, 16, 8};
  case ToolTimelineVisualKind::agent_pipeline:
    return {48, 30, 30, 30, 15, 14, 8, 280, 8, 14, 8, 8};
  case ToolTimelineVisualKind::todo:
    return {44, 14, 14, 14, 14, 14, 8, 0, 0, 14, 0, 4};
  case ToolTimelineVisualKind::shell:
  case ToolTimelineVisualKind::read:
  case ToolTimelineVisualKind::generic:
    return {48, 24, 32, 24, 16, 14, 6, 240, 12, 13, 14, 12};
  }
  return {48, 24, 32, 24, 16, 14, 6, 240, 12, 13, 14, 12};
}

struct AssistantProcessPresentation final {
  bool visible{};
  bool running{};
  bool pending_review{};
  bool failed{};
  bool initially_expanded{};
  std::int64_t duration_millis{};

  bool operator==(const AssistantProcessPresentation &) const = default;
};

[[nodiscard]] ToolTimelinePresentation
PresentToolTimeline(const domain::AssistantToolEvent &event,
                    const application::AgentResultReader *agent_results =
                        nullptr);

// The file a tool call targets, read from its arguments. Legacy
// `AssistantTurnView.renderFiles()` labelled its "N files changed" block by
// looking up `file_path`, then `path`, then falling back to the diff id, so
// the same order is kept here.
[[nodiscard]] std::string ToolCallTargetPath(
    std::string_view arguments_json, std::string_view fallback);

[[nodiscard]] AssistantProcessPresentation PresentAssistantProcess(
    const domain::ChatMessage &message, bool live,
    bool process_auto_expand, std::int64_t now_millis = 0) noexcept;

// Compact elapsed time used by the process disclosure header. This mirrors
// legacy ProcessingDuration instead of exposing fractional seconds.
[[nodiscard]] std::string FormatProcessingDuration(
    std::int64_t elapsed_millis);

// Builds the transcript rows exactly as the legacy ConversationTimeline did:
// adjacent assistant records that belong to one model turn are presented as
// one process disclosure plus one final answer. Persistence and model context
// remain untouched; this is a presentation-only projection.
[[nodiscard]] std::vector<domain::ChatMessage>
BuildConversationPresentationMessages(
    std::span<const domain::ChatMessage> messages);

} // namespace linecode::presentation
