#include "gtest_support.h"

#include "presentation/components/chat_generation_state.h"

namespace {

using linecode::presentation::LocalModelLoadPercent;
using linecode::presentation::LocalModelLoadUiState;

} // namespace

// No load means no loading label: the status row keeps saying "working".
TEST(local_model_load_state_tests, IdleStateHasNoPercentage) {
  const LocalModelLoadUiState state;
  EXPECT_EXPRESSION(!LocalModelLoadPercent(state).has_value());
}

// The engine reports from its own thread; the value the surface reads is the
// percentage it last published.
TEST(local_model_load_state_tests, ReportsThePublishedPercentage) {
  LocalModelLoadUiState state;
  state.Begin(1);
  EXPECT_EXPRESSION(LocalModelLoadPercent(state) == 0);
  state.Report(42);
  EXPECT_EXPRESSION(LocalModelLoadPercent(state) == 42);
}

// llama.cpp reports a float between 0 and 1, so a rounding overshoot must not
// escape as a percentage outside 0..100.
TEST(local_model_load_state_tests, ClampsTheReportedPercentage) {
  LocalModelLoadUiState state;
  state.Begin(1);
  state.Report(-5);
  EXPECT_EXPRESSION(LocalModelLoadPercent(state) == 0);
  state.Report(140);
  EXPECT_EXPRESSION(LocalModelLoadPercent(state) == 100);
}

// Once the weights are in memory the row goes back to the generic working
// label, even though a percentage had been published.
TEST(local_model_load_state_tests, FinishingClearsThePercentage) {
  LocalModelLoadUiState state;
  state.Begin(1);
  state.Report(80);
  state.Finish(1);
  EXPECT_EXPRESSION(!LocalModelLoadPercent(state).has_value());
}

// A late report from the engine thread after the load finished must not put the
// percentage back on screen.
TEST(local_model_load_state_tests, IgnoresReportsAfterFinish) {
  LocalModelLoadUiState state;
  state.Begin(1);
  state.Finish(1);
  state.Report(70);
  EXPECT_EXPRESSION(!LocalModelLoadPercent(state).has_value());
}

// The previous turn's guard outlives its own label: the next turn arms the row
// before that guard is destroyed, and finishing the old turn must not take the
// new turn's label down with it.
TEST(local_model_load_state_tests, ALateFinishLeavesTheNextTurnAlone) {
  LocalModelLoadUiState state;
  state.Begin(1);
  state.Begin(2);
  state.Finish(1);
  EXPECT_EXPRESSION(LocalModelLoadPercent(state) == 0);
  state.Finish(2);
  EXPECT_EXPRESSION(!LocalModelLoadPercent(state).has_value());
}
