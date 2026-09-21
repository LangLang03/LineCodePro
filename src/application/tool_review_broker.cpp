#include "application/tool_review_broker.h"

#include <utility>

namespace linecode::application {

void ToolReviewBroker::SetHandler(Handler handler) {
  handler_ = std::move(handler);
}

bool ToolReviewBroker::Available() const noexcept {
  return static_cast<bool>(handler_);
}

huxerui::Task<CompletionObserver::ToolReviewDecision>
ToolReviewBroker::Review(CompletionObserver::ToolReviewRequest request) {
  if (!handler_)
    co_return CompletionObserver::ToolReviewDecision::reject;
  // Keep the callable alive while its Task is suspended. The screen may detach
  // the broker during unmount, which replaces `handler_` immediately.
  auto handler = handler_;
  co_return co_await handler(std::move(request));
}

} // namespace linecode::application
