#pragma once

#include <expected>
#include <functional>
#include <optional>
#include <string>

#include <huxerui/task.h>

#include "application/ports/completion_gateway.h"

namespace linecode::application {

// Lets a loop that did not start the turn still ask the user about a tool call.
//
// The main loop gets its reviewer from the per-request `CompletionObserver`,
// which a long-lived collaborator like the sub-agent runner cannot hold: the
// runner outlives any single turn, while the review UI belongs to the screen.
// The screen installs its handler here and the runner consults it, so a
// sub-agent's writes are reviewed by the same prompt as the main agent's --
// matching the legacy, where `executeAgentToolCall` ran
// `executeAgentToolCallWithReview` for anything needing confirmation.
class ToolReviewBroker final {
public:
  using Handler = std::function<huxerui::Task<
      CompletionObserver::ToolReviewDecision>(
      CompletionObserver::ToolReviewRequest)>;

  // Called by the screen while it is mounted. A null handler detaches, which
  // is what happens when no review UI is available.
  void SetHandler(Handler handler);
  [[nodiscard]] bool Available() const noexcept;

  // Falls back to rejecting when nothing is attached: a tool call that wanted
  // review must not silently run just because the prompt could not be shown.
  [[nodiscard]] huxerui::Task<CompletionObserver::ToolReviewDecision>
  Review(CompletionObserver::ToolReviewRequest request);

private:
  Handler handler_;
};

} // namespace linecode::application
