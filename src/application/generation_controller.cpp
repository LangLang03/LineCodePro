#include "application/generation_controller.h"

#include <algorithm>
#include <chrono>
#include <concepts>
#include <map>
#include <utility>

namespace linecode::application {
namespace {

[[nodiscard]] std::int64_t NowMillis() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

[[nodiscard]] domain::ReasoningKind
ToDomain(CompletionReasoningKind kind) noexcept {
  return kind == CompletionReasoningKind::summary
             ? domain::ReasoningKind::summary
             : domain::ReasoningKind::thinking;
}

[[nodiscard]] domain::ToolCallStatus
ToDomain(CompletionToolCallStatus status) noexcept {
  switch (status) {
  case CompletionToolCallStatus::requested:
    return domain::ToolCallStatus::requested;
  case CompletionToolCallStatus::awaiting_review:
    return domain::ToolCallStatus::awaiting_review;
  case CompletionToolCallStatus::running:
    return domain::ToolCallStatus::running;
  case CompletionToolCallStatus::completed:
    return domain::ToolCallStatus::completed;
  case CompletionToolCallStatus::failed:
    return domain::ToolCallStatus::failed;
  case CompletionToolCallStatus::rejected:
    return domain::ToolCallStatus::rejected;
  }
  return domain::ToolCallStatus::failed;
}

struct CompletionTurn final {
  std::string text;
  std::string reasoning;
  std::vector<CompletionToolCall> calls;
  std::vector<CompletionToolResult> results;
};

void AppendParagraph(std::string &content, std::string_view paragraph) {
  if (paragraph.empty() || content.contains(paragraph))
    return;
  if (!content.empty())
    content += "\n\n";
  content += paragraph;
}

void FinalizeOpenTools(std::vector<domain::AssistantTimelineEvent> &timeline,
                       domain::ToolCallStatus status,
                       std::string_view message) {
  for (auto &event : timeline) {
    auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
    if (tool == nullptr ||
        tool->call.status == domain::ToolCallStatus::completed ||
        tool->call.status == domain::ToolCallStatus::failed ||
        tool->call.status == domain::ToolCallStatus::rejected)
      continue;
    tool->call.status = status;
    tool->call.error_message = message;
    domain::ChatToolResult result{};
    result.call_id = tool->call.id;
    result.name = tool->call.name;
    result.content = message;
    result.error = true;
    tool->result = std::move(result);
  }
}

void AppendAssistantHistory(const domain::ChatMessage &message,
                            std::vector<CompletionMessage> &history,
                            const ToolResultDisplayProjector &result_display) {
  std::map<std::size_t, CompletionTurn> turns;
  for (const auto &event : message.timeline) {
    std::visit(
        [&turns, &result_display](const auto &entry) {
          auto &turn = turns[entry.turn_index];
          using Entry = std::decay_t<decltype(entry)>;
          if constexpr (std::same_as<Entry, domain::AssistantTextEvent>) {
            turn.text += result_display.Project({}, entry.text, false)
                             .model_content;
          } else if constexpr (std::same_as<
                                   Entry, domain::AssistantReasoningEvent>) {
            turn.reasoning += entry.text;
          } else {
            const auto existing = std::ranges::find(
                turn.calls, std::string_view{entry.call.id},
                [](const CompletionToolCall &call) {
                  return std::string_view{call.id};
                });
            CompletionToolCall call{.id = entry.call.id,
                                    .name = entry.call.name,
                                    .arguments_json = entry.call.arguments_json};
            if (existing == turn.calls.end())
              turn.calls.push_back(std::move(call));
            else
              *existing = std::move(call);
            if (entry.result) {
              const auto projected = result_display.Project(
                  entry.call.name, entry.result->content,
                  entry.result->error);
              CompletionToolResult result{
                  .call_id = entry.result->call_id,
                  .name = entry.result->name,
                  .content = projected.model_content,
                  .error = entry.result->error,
              };
              const auto old_result = std::ranges::find(
                  turn.results, std::string_view{result.call_id},
                  [](const CompletionToolResult &candidate) {
                    return std::string_view{candidate.call_id};
                  });
              if (old_result == turn.results.end())
                turn.results.push_back(std::move(result));
              else
                *old_result = std::move(result);
            }
          }
        },
        event);
  }

  for (auto &[index, turn] : turns) {
    static_cast<void>(index);
    if (turn.calls.empty())
      continue;
    history.push_back(CompletionMessage::Assistant(
        std::move(turn.text), std::move(turn.calls), std::move(turn.reasoning)));
    for (auto &result : turn.results)
      history.push_back(CompletionMessage::Tool(std::move(result)));
  }
  history.push_back(CompletionMessage::Assistant(
      result_display.Project({}, message.content, false).model_content, {},
      message.reasoning_content));
}

} // namespace

GenerationController::GenerationController(
    ChatSession &session,
    std::shared_ptr<const ToolResultDisplayProjector> result_display)
    : session_(session),
      result_display_(result_display ? std::move(result_display)
                                     : DefaultToolResultDisplayProjector()) {}

std::expected<GenerationWork, SendMessageError>
GenerationController::Begin(std::string text) {
  return Begin(std::move(text), {});
}

std::expected<GenerationWork, SendMessageError>
GenerationController::Begin(
    std::string text, std::vector<domain::InputAttachment> attachments) {
  if (state_.phase == GenerationPhase::running)
    return std::unexpected(SendMessageError::generation_in_progress);

  auto sent = session_.Send(std::move(text), std::move(attachments));
  if (!sent)
    return std::unexpected(sent.error());

  const auto generation_id = ++next_generation_id_;
  state_ = {};
  state_.generation_id = generation_id;
  state_.phase = GenerationPhase::running;
  state_.started_at_millis = NowMillis();

  std::vector<CompletionMessage> messages = BuildMessages();
  return GenerationWork{.generation_id = generation_id,
                        .messages = std::move(messages)};
}

std::vector<CompletionMessage> GenerationController::BuildMessages() const {
  std::vector<CompletionMessage> messages;
  messages.reserve(session_.Messages().size());
  for (const auto &message : session_.Messages()) {
    // Legacy `ContextManager` filters on `isExcludeFromContext()` only
    // (feature-model ContextManager.java:37); `hidden` is a transcript-only
    // flag, which is exactly why a context-compaction summary is written with
    // hidden=true and excludeFromContext=false: it stays out of the timeline
    // but replaces the summarized history in the model request. Compact block
    // progress rows carry exclude_from_context=true, so they are skipped here.
    if (message.exclude_from_context)
      continue;
    if (message.role == domain::MessageRole::user) {
      messages.push_back(CompletionMessage{.role = CompletionRole::user,
                                           .content = message.content});
    } else if (message.role == domain::MessageRole::assistant) {
      AppendAssistantHistory(message, messages, *result_display_);
    }
  }
  return messages;
}

void GenerationController::RefreshMessages(GenerationWork &work) const {
  work.messages = BuildMessages();
}

bool GenerationController::Complete(const std::uint64_t generation_id,
                                    CompletionResponse response) {
  if (!IsCurrent(generation_id))
    return false;
  if (response.text.empty())
    response.text = state_.streamed_text;
  if (response.reasoning_content.empty())
    response.reasoning_content = state_.streamed_reasoning;
  if (response.text.empty() && state_.promoted_content.empty()) {
    return Fail(generation_id,
                CompletionError{.code = CompletionErrorCode::decode,
                                .message = "Model returned an empty response"});
  }
  if (!state_.promoted_content.empty()) {
    auto visible = std::move(state_.promoted_content);
    AppendParagraph(visible, response.text);
    response.text = std::move(visible);
  }

  const auto final_turn = state_.active_turn_index;
  const auto final_turn_has_tools = std::ranges::any_of(
      state_.timeline, [final_turn](const auto &event) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        return tool != nullptr && tool->turn_index == final_turn;
      });
  if (!final_turn_has_tools) {
    std::erase_if(state_.timeline, [final_turn](const auto &event) {
      const auto *text = std::get_if<domain::AssistantTextEvent>(&event);
      return text != nullptr && text->turn_index == final_turn;
    });
  }

  domain::ChatMessage message{};
  message.content = std::move(response.text);
  message.reasoning_content = std::move(response.reasoning_content);
  message.timeline = std::move(state_.timeline);
  message.processing_started_at = state_.started_at_millis;
  message.processing_finished_at = NowMillis();
  static_cast<void>(session_.AppendAssistant(std::move(message)));
  state_.phase = GenerationPhase::completed;
  state_.error.clear();
  state_.streamed_text.clear();
  state_.streamed_reasoning.clear();
  state_.promoted_content.clear();
  state_.timeline.clear();
  return true;
}

bool GenerationController::Observe(const std::uint64_t generation_id,
                                   const CompletionEvent &event) {
  if (!IsCurrent(generation_id))
    return false;

  std::visit(
      [this](const auto &entry) {
        using Entry = std::decay_t<decltype(entry)>;
        if (entry.turn_index != state_.active_turn_index) {
          state_.active_turn_index = entry.turn_index;
          state_.streamed_text.clear();
          state_.streamed_reasoning.clear();
        }
        if constexpr (std::same_as<Entry, CompletionTextDelta>) {
          if (entry.text.empty())
            return;
          state_.streamed_text += entry.text;
          if (!state_.timeline.empty()) {
            if (auto *previous =
                    std::get_if<domain::AssistantTextEvent>(&state_.timeline.back());
                previous && previous->turn_index == entry.turn_index) {
              previous->text += entry.text;
              return;
            }
          }
          state_.timeline.push_back(domain::AssistantTextEvent{
              .turn_index = entry.turn_index, .text = entry.text});
        } else if constexpr (std::same_as<Entry, CompletionReasoningDelta>) {
          if (entry.text.empty())
            return;
          state_.streamed_reasoning += entry.text;
          if (!entry.starts_new_segment && !state_.timeline.empty()) {
            if (auto *previous = std::get_if<domain::AssistantReasoningEvent>(
                    &state_.timeline.back());
                previous && previous->turn_index == entry.turn_index &&
                previous->kind == ToDomain(entry.kind)) {
              previous->text += entry.text;
              return;
            }
          }
          state_.timeline.push_back(domain::AssistantReasoningEvent{
              .turn_index = entry.turn_index,
              .text = entry.text,
              .kind = ToDomain(entry.kind),
              .starts_new_segment = entry.starts_new_segment,
          });
        } else {
          domain::AssistantToolEvent tool{};
          tool.turn_index = entry.turn_index;
          tool.call = domain::ChatToolCall{
              .id = entry.call.id,
              .name = entry.call.name,
              .arguments_json = entry.call.arguments_json,
              .status = ToDomain(entry.status),
              .created_at_millis = entry.created_at_millis,
              .duration_millis = entry.duration_millis,
              .error_message = entry.result && entry.result->error
                                   ? entry.result->content
                                   : std::string{},
          };
          if (entry.result) {
            domain::ChatToolResult result{};
            result.call_id = entry.result->call_id;
            result.name = entry.result->name;
            result.content = entry.result->content;
            result.error = entry.result->error;
            result.diff_id = entry.result->diff_id;
            tool.result = std::move(result);
          }
          const auto previous = std::ranges::find_if(
              state_.timeline, [&entry](const auto &candidate) {
                const auto *candidate_tool =
                    std::get_if<domain::AssistantToolEvent>(&candidate);
                return candidate_tool != nullptr &&
                       candidate_tool->call.id == entry.call.id;
              });
          if (previous == state_.timeline.end())
            state_.timeline.push_back(std::move(tool));
          else
            *previous = std::move(tool);
          if (entry.status == CompletionToolCallStatus::completed &&
              !entry.display.display_markdown.empty()) {
            AppendParagraph(state_.promoted_content,
                            entry.display.display_markdown);
          }
        }
      },
      event);
  return true;
}

void GenerationController::PersistPartial(bool error,
                                          std::string error_message) {
  if (state_.streamed_text.empty() && state_.streamed_reasoning.empty() &&
      state_.promoted_content.empty() && state_.timeline.empty())
    return;
  domain::ChatMessage message{};
  message.content = state_.promoted_content;
  AppendParagraph(message.content, state_.streamed_text);
  message.reasoning_content = state_.streamed_reasoning;
  message.timeline = state_.timeline;
  message.error = error;
  message.error_message = std::move(error_message);
  message.processing_started_at = state_.started_at_millis;
  message.processing_finished_at = NowMillis();
  static_cast<void>(session_.AppendAssistant(std::move(message)));
}

bool GenerationController::Fail(const std::uint64_t generation_id,
                                CompletionError error) {
  if (!IsCurrent(generation_id))
    return false;
  FinalizeOpenTools(state_.timeline, domain::ToolCallStatus::failed,
                    error.message);
  PersistPartial(true, error.message);
  state_.phase = GenerationPhase::failed;
  state_.error = std::move(error.message);
  state_.streamed_text.clear();
  state_.streamed_reasoning.clear();
  state_.promoted_content.clear();
  state_.timeline.clear();
  return true;
}

void GenerationController::Cancel() noexcept {
  if (state_.phase != GenerationPhase::running)
    return;
  try {
    FinalizeOpenTools(state_.timeline, domain::ToolCallStatus::rejected,
                      "Generation cancelled");
    PersistPartial(false, {});
  } catch (...) {
  }
  state_.phase = GenerationPhase::cancelled;
  state_.error.clear();
  state_.streamed_text.clear();
  state_.streamed_reasoning.clear();
  state_.promoted_content.clear();
  state_.timeline.clear();
}

void GenerationController::Reset() noexcept { state_ = {}; }

bool GenerationController::IsCurrent(
    const std::uint64_t generation_id) const noexcept {
  return state_.phase == GenerationPhase::running &&
         state_.generation_id == generation_id;
}

const GenerationState &GenerationController::State() const noexcept {
  return state_;
}

} // namespace linecode::application
