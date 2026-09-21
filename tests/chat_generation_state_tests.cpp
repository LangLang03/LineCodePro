#include "gtest_support.h"

#include <string>

#include "presentation/components/chat_generation_state.h"

namespace {

using namespace linecode::presentation;

TEST(ChatGenerationStateTest, FormatsRetryAndFailureLabels) {
  const RetryLabels labels{
      .attempt = std::string{"Retry "} + std::string{kAttemptMarker} +
                 "/3: " + std::string{kErrorMarker},
      .failed = std::string{"Failed: "} + std::string{kErrorMarker},
      .no_model = "No model",
      .model_missing = "Missing model",
  };

  EXPECT_EQ(FormatRetryNotice(labels, 2, "timeout"), "Retry 2/3: timeout");
  EXPECT_EQ(FormatModelFailed(labels.failed, "offline"), "Failed: offline");
}

TEST(ChatGenerationStateTest, TracksOnlyTheOwningGeneration) {
  AutoCompactionUiState state;

  EXPECT_FALSE(state.RunningFor(7));
  state.Begin(7);
  EXPECT_EXPRESSION(state.RunningFor(7));
  EXPECT_FALSE(state.RunningFor(8));
  EXPECT_EQ(state.status, linecode::domain::compact_status_running);

  state.Finish(std::string{linecode::domain::compact_status_done});
  EXPECT_FALSE(state.RunningFor(7));
  EXPECT_EQ(state.status, linecode::domain::compact_status_done);
}

} // namespace
