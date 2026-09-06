#include "application/generation_controller.h"

#include <utility>

namespace linecode::application {

GenerationController::GenerationController(ChatSession &session) noexcept
    : session_(session) {}

std::expected<GenerationWork, SendMessageError>
GenerationController::Begin(std::string text) {
  if (state_.phase == GenerationPhase::running) {
    return std::unexpected(SendMessageError::generation_in_progress);
  }
  auto sent = session_.Send(std::move(text));
  if (!sent.has_value()) {
    return std::unexpected(sent.error());
  }

  const auto generation_id = ++next_generation_id_;
  state_ = GenerationState{.generation_id = generation_id,
                           .phase = GenerationPhase::running,
                           .error = {}};

  std::vector<CompletionMessage> messages;
  messages.reserve(session_.Messages().size());
  for (const auto &message : session_.Messages()) {
    if (message.role != domain::MessageRole::user &&
        message.role != domain::MessageRole::assistant) {
      continue;
    }
    messages.push_back(CompletionMessage{
        .role = message.role == domain::MessageRole::assistant
                    ? CompletionRole::assistant
                    : CompletionRole::user,
        .content = message.content,
    });
  }
  return GenerationWork{.generation_id = generation_id,
                        .messages = std::move(messages)};
}

bool GenerationController::Complete(const std::uint64_t generation_id,
                                    CompletionResponse response) {
  if (!IsCurrent(generation_id)) {
    return false;
  }
  if (response.text.empty()) {
    return Fail(generation_id,
                CompletionError{.code = CompletionErrorCode::decode,
                                .message = "Model returned an empty response"});
  }
  static_cast<void>(session_.AppendAssistant(std::move(response.text)));
  state_.phase = GenerationPhase::completed;
  state_.error.clear();
  return true;
}

bool GenerationController::Fail(const std::uint64_t generation_id,
                                CompletionError error) {
  if (!IsCurrent(generation_id)) {
    return false;
  }
  state_.phase = GenerationPhase::failed;
  state_.error = std::move(error.message);
  return true;
}

void GenerationController::Cancel() noexcept {
  if (state_.phase != GenerationPhase::running) {
    return;
  }
  state_.phase = GenerationPhase::cancelled;
  state_.error.clear();
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
