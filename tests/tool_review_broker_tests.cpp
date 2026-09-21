// The review broker is how a loop that did not start the turn still reaches
// the user's prompt -- in practice the sub-agent runner, which outlives any
// single turn and so cannot hold the per-request observer.

#include "gtest_support.h"
#include <chrono>
#include <iostream>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/testing/ui_test.h>

#include "application/tool_review_broker.h"
#include "application/tool_review_coordinator.h"

namespace {

using linecode::application::CompletionObserver;
using linecode::application::ToolReviewBroker;
using linecode::application::ToolReviewCoordinator;

CompletionObserver::ToolReviewRequest Request(std::string id = {}) {
  return CompletionObserver::ToolReviewRequest{
      .call = linecode::application::CompletionToolCall{
          .id = std::move(id), .name = {}, .arguments_json = {}},
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
  EXPECT_EXPRESSION(harness->done);
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
  EXPECT_EXPRESSION(!target.broker->Available());
  Run(target);
  EXPECT_EXPRESSION(target.decision == CompletionObserver::ToolReviewDecision::reject);
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
  EXPECT_EXPRESSION(target.broker->Available());
  Run(target);
  EXPECT_EXPRESSION(target.handler_calls == 1);
  EXPECT_EXPRESSION(target.decision == CompletionObserver::ToolReviewDecision::allow_once);
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
  EXPECT_EXPRESSION(!target.broker->Available());
  Run(target);
  EXPECT_EXPRESSION(target.decision == CompletionObserver::ToolReviewDecision::reject);
}

struct CoordinatorHarness final {
  std::shared_ptr<ToolReviewCoordinator> coordinator;
  std::vector<CompletionObserver::ToolReviewDecision> decisions{
      CompletionObserver::ToolReviewDecision::allow_always,
      CompletionObserver::ToolReviewDecision::allow_always,
  };
  int completed{};
  bool launched{};
};

std::shared_ptr<CoordinatorHarness> coordinator_harness;

huxerui::View CoordinatorProbe() {
  const auto current = coordinator_harness;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    if (current->launched)
      return;
    current->launched = true;
    auto first = current->coordinator->ReviewHandler();
    auto second = current->coordinator->ReviewHandler();
    tasks.Launch([current, first = std::move(first)]() mutable
                     -> huxerui::Task<void> {
      current->decisions[0] = co_await first(Request("first"));
      ++current->completed;
    });
    tasks.Launch([current, second = std::move(second)]() mutable
                     -> huxerui::Task<void> {
      current->decisions[1] = co_await second(Request("second"));
      ++current->completed;
    });
  });
  return huxerui::Text("tool-review-coordinator-probe");
}

void CoordinatorSerializesConcurrentPrompts() {
  coordinator_harness = std::make_shared<CoordinatorHarness>();
  coordinator_harness->coordinator =
      std::make_shared<ToolReviewCoordinator>();
  {
    const huxerui::Application application(CoordinatorProbe,
                                            {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(application);
    for (int frame = 0; frame < 200; ++frame)
      ui.Pump(std::chrono::milliseconds{1});

    auto current = coordinator_harness->coordinator->Current();
    EXPECT_EXPRESSION(current && current->request.call.id == "first");
    EXPECT_EXPRESSION(coordinator_harness->coordinator->Resolve(
        "first", CompletionObserver::ToolReviewDecision::allow_once));
    for (int frame = 0; frame < 200; ++frame)
      ui.Pump(std::chrono::milliseconds{1});

    current = coordinator_harness->coordinator->Current();
    EXPECT_EXPRESSION(current && current->request.call.id == "second");
    EXPECT_EXPRESSION(coordinator_harness->coordinator->Resolve(
        "second", CompletionObserver::ToolReviewDecision::reject));
    for (int frame = 0;
         frame < 2'000 && coordinator_harness->completed != 2; ++frame)
      ui.Pump(std::chrono::milliseconds{1});

    EXPECT_EXPRESSION(coordinator_harness->completed == 2);
    EXPECT_EXPRESSION(coordinator_harness->decisions[0] ==
           CompletionObserver::ToolReviewDecision::allow_once);
    EXPECT_EXPRESSION(coordinator_harness->decisions[1] ==
           CompletionObserver::ToolReviewDecision::reject);
    EXPECT_EXPRESSION(!coordinator_harness->coordinator->Current());
  }
  coordinator_harness.reset();
}

void ClosingCoordinatorRejectsEveryWaiter() {
  coordinator_harness = std::make_shared<CoordinatorHarness>();
  coordinator_harness->coordinator =
      std::make_shared<ToolReviewCoordinator>();
  {
    const huxerui::Application application(CoordinatorProbe,
                                            {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(application);
    for (int frame = 0; frame < 200; ++frame)
      ui.Pump(std::chrono::milliseconds{1});
    coordinator_harness->coordinator->Close();
    for (int frame = 0;
         frame < 2'000 && coordinator_harness->completed != 2; ++frame)
      ui.Pump(std::chrono::milliseconds{1});

    EXPECT_EXPRESSION(coordinator_harness->completed == 2);
    EXPECT_EXPRESSION(coordinator_harness->decisions[0] ==
           CompletionObserver::ToolReviewDecision::reject);
    EXPECT_EXPRESSION(coordinator_harness->decisions[1] ==
           CompletionObserver::ToolReviewDecision::reject);
    EXPECT_EXPRESSION(!coordinator_harness->coordinator->Current());
  }
  coordinator_harness.reset();
}

} // namespace

TEST(tool_review_broker_tests, LegacySuite) {
  DetachedBrokerRejects();
  AttachedHandlerAnswers();
  DetachingRestoresRefusal();
  CoordinatorSerializesConcurrentPrompts();
  ClosingCoordinatorRejectsEveryWaiter();
  std::cout << "tool_review_broker_tests passed\n";
  return;
}
