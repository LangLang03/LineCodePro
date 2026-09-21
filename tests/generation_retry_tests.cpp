// Contract tests for the retry bookkeeping the send path relies on.

#include "gtest_support.h"
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
  EXPECT_EXPRESSION(work.has_value());
  const auto id = work->generation_id;
  EXPECT_EXPRESSION(controller.State().phase == application::GenerationPhase::running);

  static_cast<void>(controller.Observe(id, TextDelta("partial answer")));
  EXPECT_EXPRESSION(controller.State().streamed_text == "partial answer");

  EXPECT_EXPRESSION(controller.ResetAttempt(id));
  EXPECT_EXPRESSION(controller.State().streamed_text.empty());
  EXPECT_EXPRESSION(controller.State().streamed_reasoning.empty());
  EXPECT_EXPRESSION(controller.State().promoted_content.empty());
  EXPECT_EXPRESSION(controller.State().timeline.empty());
  EXPECT_EXPRESSION(controller.State().error.empty());
  // Still running: the retry continues the same turn rather than ending it.
  EXPECT_EXPRESSION(controller.State().phase == application::GenerationPhase::running);
  // Only the user's own message is there: the failed attempt left no partial
  // assistant bubble behind.
  EXPECT_EXPRESSION(session.Messages().size() == 1U);
  EXPECT_EXPRESSION(session.Messages().front().role == domain::MessageRole::user);
}

// A stale generation id must not be able to disturb the current one.
void ResetAttemptRejectsAStaleGeneration() {
  application::ChatSession session{
      std::make_unique<infrastructure::InMemoryConversationStore>()};
  application::GenerationController controller{session};

  auto first = controller.Begin("one");
  EXPECT_EXPRESSION(first.has_value());
  // A running generation has to finish before another can start, so complete
  // it and then open the second one.
  application::CompletionResponse done;
  done.text = "one";
  EXPECT_EXPRESSION(controller.Complete(first->generation_id, std::move(done)));
  auto second = controller.Begin("two");
  EXPECT_EXPRESSION(second.has_value());

  static_cast<void>(
      controller.Observe(second->generation_id, TextDelta("current")));
  EXPECT_EXPRESSION(controller.State().streamed_text == "current");
  EXPECT_EXPRESSION(!controller.ResetAttempt(first->generation_id));
  EXPECT_EXPRESSION(controller.State().streamed_text == "current");
}

// Failing still ends the turn, which is what the retry loop falls back to once
// it runs out of attempts.
void FailStillEndsTheTurn() {
  application::ChatSession session{
      std::make_unique<infrastructure::InMemoryConversationStore>()};
  application::GenerationController controller{session};

  auto work = controller.Begin("hello");
  EXPECT_EXPRESSION(work.has_value());
  const auto id = work->generation_id;
  static_cast<void>(controller.Observe(id, TextDelta("partial answer")));
  EXPECT_EXPRESSION(controller.Fail(id, TransportError("boom")));
  EXPECT_EXPRESSION(controller.State().phase == application::GenerationPhase::failed);
  EXPECT_EXPRESSION(controller.State().error == "boom");
}

// The legacy app persists a terminal assistant error even when every retry
// failed before producing a text/reasoning/tool delta.  Keeping that row in
// the conversation is essential: otherwise the failure disappears after a
// process restart and the restored transcript no longer matches what the user
// saw.
void EmptyTerminalFailureIsPersisted() {
  application::ChatSession session{
      std::make_unique<infrastructure::InMemoryConversationStore>()};
  application::GenerationController controller{session};

  const auto work = controller.Begin("hello");
  EXPECT_EXPRESSION(work.has_value());
  EXPECT_EXPRESSION(controller.Fail(work->generation_id,
                         TransportError("all retries failed")));

  const auto messages = session.Messages();
  EXPECT_EXPRESSION(messages.size() == 2U);
  const auto &failure = messages.back();
  EXPECT_EXPRESSION(failure.role == domain::MessageRole::assistant);
  EXPECT_EXPRESSION(failure.content.empty());
  EXPECT_EXPRESSION(failure.error);
  EXPECT_EXPRESSION(failure.error_message == "all retries failed");
  EXPECT_EXPRESSION(failure.processing_started_at > 0);
  EXPECT_EXPRESSION(failure.processing_finished_at >= failure.processing_started_at);
}

} // namespace

TEST(generation_retry_tests, LegacySuite) {
  ResetAttemptKeepsTheTurnRunning();
  ResetAttemptRejectsAStaleGeneration();
  FailStillEndsTheTurn();
  EmptyTerminalFailureIsPersisted();
  std::cout << "generation_retry_tests passed\n";
  return;
}
