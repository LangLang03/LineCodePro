// The review broker is how a loop that did not start the turn still reaches
// the user's prompt -- in practice the sub-agent runner, which outlives any
// single turn and so cannot hold the per-request observer.

#include <cassert>
#include <chrono>
#include <iostream>
#include <memory>
#include <string>

#include <huxerui/testing/ui_test.h>

#include "application/tool_review_broker.h"

namespace {

using linecode::application::CompletionObserver;
using linecode::application::ToolReviewBroker;

CompletionObserver::ToolReviewRequest Request() {
  return CompletionObserver::ToolReviewRequest{
      .call = linecode::application::CompletionToolCall{},
      .can_allow_always = true,
  };
}

struct Harness final {
  std::shared_ptr<ToolReviewBroker> broker =
      std::make_shared<ToolReviewBroker>();
  int handler_calls{};
  bool answer_with_allow_once{};
  bool done{};
  CompletionObserver::ToolReviewDecision decision{
      CompletionObserver::ToolReviewDecision::reject};
};

std::shared_ptr<Harness> harness;

huxerui::View Probe() {
  const auto current = harness;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    if (current->done)
      return;
    tasks.Launch([current]() -> huxerui::Task<void> {
      current->decision = co_await current->broker->Review(Request());
      current->done = true;
    });
  });
  return huxerui::Text("tool-review-broker-probe");
}

void Run(Harness &target) {
  harness = std::make_shared<Harness>(target);
  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  for (std::size_t frame = 0; frame < 20'000U && !harness->done; ++frame)
    ui.Pump(std::chrono::milliseconds{1});
  assert(harness->done);
  target.decision = harness->decision;
  // `handler_calls` is intentionally not copied back: the handler captures the
  // original target, so copying the harness's zero over it would erase the
  // very observation the test is making.
  harness.reset();
}

// A tool call that asked for review must not run just because the prompt could
// not be shown; the broker has to refuse rather than wave it through.
void DetachedBrokerRejects() {
  Harness target;
  assert(!target.broker->Available());
  Run(target);
  assert(target.decision == CompletionObserver::ToolReviewDecision::reject);
}

void AttachedHandlerAnswers() {
  Harness target;
  target.answer_with_allow_once = true;
  target.broker->SetHandler([&target](
                                CompletionObserver::ToolReviewRequest)
                                -> huxerui::Task<
                                    CompletionObserver::ToolReviewDecision> {
    ++target.handler_calls;
    co_return CompletionObserver::ToolReviewDecision::allow_once;
  });
  assert(target.broker->Available());
  Run(target);
  assert(target.handler_calls == 1);
  assert(target.decision == CompletionObserver::ToolReviewDecision::allow_once);
}

// Detaching restores the refusal, so a screen that unmounts cannot leave a
// stale prompt reachable.
void DetachingRestoresRefusal() {
  Harness target;
  target.broker->SetHandler(
      [](CompletionObserver::ToolReviewRequest)
          -> huxerui::Task<CompletionObserver::ToolReviewDecision> {
        co_return CompletionObserver::ToolReviewDecision::allow_always;
      });
  target.broker->SetHandler(nullptr);
  assert(!target.broker->Available());
  Run(target);
  assert(target.decision == CompletionObserver::ToolReviewDecision::reject);
}

} // namespace

int main() {
  DetachedBrokerRejects();
  AttachedHandlerAnswers();
  DetachingRestoresRefusal();
  std::cout << "tool_review_broker_tests passed\n";
  return 0;
}
