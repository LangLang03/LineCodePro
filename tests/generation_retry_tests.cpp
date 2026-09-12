// Contract tests for the retry bookkeeping the send path relies on.

#include <cassert>
#include <cstdint>
#include <iostream>
#include <memory>
#include <string>

#include "application/chat_session.h"
#include "application/generation_controller.h"
#include "infrastructure/in_memory_conversation_store.h"

namespace {

using namespace linecode;

application::CompletionError TransportError(std::string message) {
  return application::CompletionError{
      .code = application::CompletionErrorCode::transport,
      .message = std::move(message)};
}

application::CompletionEvent TextDelta(std::string text) {
  return application::CompletionTextDelta{.turn_index = 0,
                                          .text = std::move(text)};
}

// `GenerationController::ResetAttempt` exists so the retry loop can discard a
// failed attempt without failing the generation. Legacy removed the partial
// assistant message; this port never persisted one, so clearing the streamed
// state has to be enough — and the turn must stay `running`.
void ResetAttemptKeepsTheTurnRunning() {
  application::ChatSession session{
      std::make_unique<infrastructure::InMemoryConversationStore>()};
  application::GenerationController controller{session};

  auto work = controller.Begin("hello");
  assert(work.has_value());
  const auto id = work->generation_id;
  assert(controller.State().phase == application::GenerationPhase::running);

  static_cast<void>(controller.Observe(id, TextDelta("partial answer")));
  assert(controller.State().streamed_text == "partial answer");

  assert(controller.ResetAttempt(id));
  assert(controller.State().streamed_text.empty());
  assert(controller.State().streamed_reasoning.empty());
  assert(controller.State().promoted_content.empty());
  assert(controller.State().timeline.empty());
  assert(controller.State().error.empty());
  // Still running: the retry continues the same turn rather than ending it.
  assert(controller.State().phase == application::GenerationPhase::running);
  // Only the user's own message is there: the failed attempt left no partial
  // assistant bubble behind.
  assert(session.Messages().size() == 1U);
  assert(session.Messages().front().role == domain::MessageRole::user);
}

// A stale generation id must not be able to disturb the current one.
void ResetAttemptRejectsAStaleGeneration() {
  application::ChatSession session{
      std::make_unique<infrastructure::InMemoryConversationStore>()};
  application::GenerationController controller{session};

  auto first = controller.Begin("one");
  assert(first.has_value());
  // A running generation has to finish before another can start, so complete
  // it and then open the second one.
  application::CompletionResponse done;
  done.text = "one";
  assert(controller.Complete(first->generation_id, std::move(done)));
  auto second = controller.Begin("two");
  assert(second.has_value());

  static_cast<void>(
      controller.Observe(second->generation_id, TextDelta("current")));
  assert(controller.State().streamed_text == "current");
  assert(!controller.ResetAttempt(first->generation_id));
  assert(controller.State().streamed_text == "current");
}

// Failing still ends the turn, which is what the retry loop falls back to once
// it runs out of attempts.
void FailStillEndsTheTurn() {
  application::ChatSession session{
      std::make_unique<infrastructure::InMemoryConversationStore>()};
  application::GenerationController controller{session};

  auto work = controller.Begin("hello");
  assert(work.has_value());
  const auto id = work->generation_id;
  static_cast<void>(controller.Observe(id, TextDelta("partial answer")));
  assert(controller.Fail(id, TransportError("boom")));
  assert(controller.State().phase == application::GenerationPhase::failed);
  assert(controller.State().error == "boom");
}

} // namespace

int main() {
  ResetAttemptKeepsTheTurnRunning();
  ResetAttemptRejectsAStaleGeneration();
  FailStillEndsTheTurn();
  std::cout << "generation_retry_tests passed\n";
  return 0;
}
