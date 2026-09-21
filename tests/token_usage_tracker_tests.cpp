// Contract tests for the ported token usage tracker.

#include "gtest_support.h"
#include <cstdint>
#include <iostream>

#include "application/token_usage_tracker.h"
#include "infrastructure/archive_json.h"

namespace {

using linecode::application::CompletionResponse;
using linecode::application::TokenUsageTracker;

CompletionResponse Response(const std::int64_t input,
                            const std::int64_t output) {
  CompletionResponse response;
  response.input_tokens = input;
  response.output_tokens = output;
  return response;
}

// The tracker starts empty, so the compaction trigger falls back to the local
// estimate until a protocol reports real usage.
void StartsEmpty() {
  const TokenUsageTracker tracker;
  EXPECT_EXPRESSION(tracker.LastInputTokens() == 0);
  EXPECT_EXPRESSION(tracker.LastOutputTokens() == 0);
}

void RecordsBothCounts() {
  TokenUsageTracker tracker;
  tracker.Record(Response(1234, 56));
  EXPECT_EXPRESSION(tracker.LastInputTokens() == 1234);
  EXPECT_EXPRESSION(tracker.LastOutputTokens() == 56);
}

// A protocol that omits usage must not wipe the last real measurement; this is
// what makes the fallback in the trigger safe.
void IgnoresNonPositiveCounts() {
  TokenUsageTracker tracker;
  tracker.Record(Response(900, 40));
  tracker.Record(Response(0, 0));
  EXPECT_EXPRESSION(tracker.LastInputTokens() == 900);
  EXPECT_EXPRESSION(tracker.LastOutputTokens() == 40);

  // A response that only carries one of the two updates only that one.
  tracker.Record(Response(1500, 0));
  EXPECT_EXPRESSION(tracker.LastInputTokens() == 1500);
  EXPECT_EXPRESSION(tracker.LastOutputTokens() == 40);
}

void ResetClearsBoth() {
  TokenUsageTracker tracker;
  tracker.Record(Response(700, 30));
  tracker.Reset();
  EXPECT_EXPRESSION(tracker.LastInputTokens() == 0);
  EXPECT_EXPRESSION(tracker.LastOutputTokens() == 0);
}

} // namespace

TEST(token_usage_tracker_tests, LegacySuite) {
  StartsEmpty();
  RecordsBothCounts();
  IgnoresNonPositiveCounts();
  ResetClearsBoth();
  std::cout << "token_usage_tracker_tests passed\n";
  return;
}
