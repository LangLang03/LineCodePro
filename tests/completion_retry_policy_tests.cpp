// Contract tests for the completion retry policy. These pin the decisions that
// used to be a blanket "retry three times, five seconds apart": which failures
// are worth re-issuing, how long to wait, and the guarantee that a retry never
// replays a tool loop that already changed the workspace.

#include "gtest_support.h"

#include <chrono>

#include "application/completion_retry_policy.h"

namespace {

using namespace std::chrono_literals;
using linecode::application::CompletionErrorCode;
using linecode::application::DecideCompletionRetry;
using linecode::application::IsRetryableHttpStatus;
using linecode::application::kMaximumCompletionAttempts;
using linecode::application::kMaximumRetryDelay;
using linecode::application::RetryBackoffForAttempt;
using linecode::application::RetryBlockReason;
using linecode::application::RetryInput;

constexpr std::uint64_t kSeed = 12'345;

RetryInput Input(CompletionErrorCode code, int attempt = 0) {
  RetryInput input;
  input.code = code;
  input.attempt = attempt;
  return input;
}

// Statuses that a second identical request can plausibly satisfy.
void RetriesOnlyTransientStatuses() {
  EXPECT_EXPRESSION(IsRetryableHttpStatus(408));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(409));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(425));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(429));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(500));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(502));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(503));
  EXPECT_EXPRESSION(IsRetryableHttpStatus(504));
  EXPECT_EXPRESSION(!IsRetryableHttpStatus(400));
  EXPECT_EXPRESSION(!IsRetryableHttpStatus(401));
  EXPECT_EXPRESSION(!IsRetryableHttpStatus(403));
  EXPECT_EXPRESSION(!IsRetryableHttpStatus(404));
  EXPECT_EXPRESSION(!IsRetryableHttpStatus(422));
  EXPECT_EXPRESSION(!IsRetryableHttpStatus(501));
}

// A bad key or an unknown model is retried zero times.
void DoesNotRetryDeterministicFailures() {
  auto unauthorized = Input(CompletionErrorCode::http_status);
  unauthorized.http_status = 401;
  const auto denied = DecideCompletionRetry(unauthorized, kSeed);
  EXPECT_EXPRESSION(!denied.retry);
  EXPECT_EXPRESSION(denied.block_reason == RetryBlockReason::not_retryable);

  const auto malformed = DecideCompletionRetry(
      Input(CompletionErrorCode::decode), kSeed);
  EXPECT_EXPRESSION(!malformed.retry);
  EXPECT_EXPRESSION(malformed.block_reason == RetryBlockReason::not_retryable);

  const auto misconfigured = DecideCompletionRetry(
      Input(CompletionErrorCode::invalid_configuration), kSeed);
  EXPECT_EXPRESSION(!misconfigured.retry);

  const auto protocol = DecideCompletionRetry(
      Input(CompletionErrorCode::unsupported_protocol), kSeed);
  EXPECT_EXPRESSION(!protocol.retry);
}

// Transport failures and stalls are retried, and a 503 is retried.
void RetriesTransportAndStalls() {
  EXPECT_EXPRESSION(
      DecideCompletionRetry(Input(CompletionErrorCode::transport), kSeed).retry);
  const auto stalled =
      DecideCompletionRetry(Input(CompletionErrorCode::timeout), kSeed);
  EXPECT_EXPRESSION(stalled.retry);
  EXPECT_EXPRESSION(stalled.block_reason == RetryBlockReason::none);

  auto unavailable = Input(CompletionErrorCode::http_status);
  unavailable.http_status = 503;
  EXPECT_EXPRESSION(DecideCompletionRetry(unavailable, kSeed).retry);
}

// The attempt budget is shared with the "retrying n/m" notice.
void StopsAtTheAttemptBudget() {
  const auto last = DecideCompletionRetry(
      Input(CompletionErrorCode::transport, kMaximumCompletionAttempts - 1),
      kSeed);
  EXPECT_EXPRESSION(!last.retry);
  EXPECT_EXPRESSION(last.block_reason ==
                    RetryBlockReason::attempt_budget_exhausted);
}

// A retry replays the whole tool loop, so a state-changing call that already
// ran must block it even when the failure itself looks retryable.
void RefusesToReplayToolsThatChangedTheWorkspace() {
  auto input = Input(CompletionErrorCode::http_status);
  input.http_status = 503;
  input.executed_tool_calls = 1;
  input.mutating_tool_calls = 1;
  const auto decision = DecideCompletionRetry(input, kSeed);
  EXPECT_EXPRESSION(!decision.retry);
  EXPECT_EXPRESSION(decision.block_reason ==
                    RetryBlockReason::state_changing_tools_ran);
}

// Read-only tools are safe to replay, so they must not block a retry.
void StillRetriesAfterReadOnlyTools() {
  auto input = Input(CompletionErrorCode::transport);
  input.executed_tool_calls = 3;
  input.mutating_tool_calls = 0;
  EXPECT_EXPRESSION(DecideCompletionRetry(input, kSeed).retry);
}

// A non-retryable status is reported as such even if tools ran, so the user
// sees the real cause instead of a "we did not retry" note.
void ReportsTheFailureReasonBeforeTheToolNote() {
  auto input = Input(CompletionErrorCode::http_status);
  input.http_status = 401;
  input.executed_tool_calls = 2;
  input.mutating_tool_calls = 2;
  const auto decision = DecideCompletionRetry(input, kSeed);
  EXPECT_EXPRESSION(!decision.retry);
  EXPECT_EXPRESSION(decision.block_reason == RetryBlockReason::not_retryable);
}

// Exponential backoff, capped.
void BacksOffExponentially() {
  EXPECT_EXPRESSION(RetryBackoffForAttempt(0) == 1'000ms);
  EXPECT_EXPRESSION(RetryBackoffForAttempt(1) == 2'000ms);
  EXPECT_EXPRESSION(RetryBackoffForAttempt(2) == 4'000ms);
  EXPECT_EXPRESSION(RetryBackoffForAttempt(9) == kMaximumRetryDelay);
}

// `Retry-After` wins over the backoff, and is capped so a hostile or buggy value
// cannot park the request for an hour.
void HonoursRetryAfterWithinALimit() {
  auto throttled = Input(CompletionErrorCode::http_status);
  throttled.http_status = 429;
  throttled.retry_after_seconds = 5;
  const auto five = DecideCompletionRetry(throttled, kSeed);
  EXPECT_EXPRESSION(five.retry);
  EXPECT_EXPRESSION(five.delay >= 5'000ms);
  EXPECT_EXPRESSION(five.delay <= 6'250ms);

  throttled.retry_after_seconds = 3'600;
  const auto capped = DecideCompletionRetry(throttled, kSeed);
  EXPECT_EXPRESSION(capped.retry);
  EXPECT_EXPRESSION(capped.delay >= 60'000ms);
  EXPECT_EXPRESSION(capped.delay <= 75'000ms);

  throttled.retry_after_seconds = 0;
  const auto zero = DecideCompletionRetry(throttled, kSeed);
  EXPECT_EXPRESSION(zero.retry);
  EXPECT_EXPRESSION(zero.delay >= 750ms);
  EXPECT_EXPRESSION(zero.delay <= 1'250ms);
}

// Jitter stays inside +/-25% and is deterministic for a fixed seed, so two
// devices do not retry on the same clock tick and tests can pin the value.
void AppliesBoundedDeterministicJitter() {
  // The last attempt has no budget left, so only the earlier ones retry.
  for (int attempt = 0; attempt < kMaximumCompletionAttempts - 1; ++attempt) {
    const auto input = Input(CompletionErrorCode::transport, attempt);
    const auto base = RetryBackoffForAttempt(attempt);
    const auto first = DecideCompletionRetry(input, kSeed);
    const auto second = DecideCompletionRetry(input, kSeed);
    EXPECT_EXPRESSION(first.retry);
    EXPECT_EXPRESSION(first.delay == second.delay);
    EXPECT_EXPRESSION(first.delay >= base * 3 / 4);
    EXPECT_EXPRESSION(first.delay <= base * 5 / 4);
  }
}

} // namespace

TEST(completion_retry_policy_tests, RetriesOnlyTransientStatuses) {
  RetriesOnlyTransientStatuses();
}
TEST(completion_retry_policy_tests, DoesNotRetryDeterministicFailures) {
  DoesNotRetryDeterministicFailures();
}
TEST(completion_retry_policy_tests, RetriesTransportAndStalls) {
  RetriesTransportAndStalls();
}
TEST(completion_retry_policy_tests, StopsAtTheAttemptBudget) {
  StopsAtTheAttemptBudget();
}
TEST(completion_retry_policy_tests, RefusesToReplayToolsThatChangedTheWorkspace) {
  RefusesToReplayToolsThatChangedTheWorkspace();
}
TEST(completion_retry_policy_tests, StillRetriesAfterReadOnlyTools) {
  StillRetriesAfterReadOnlyTools();
}
TEST(completion_retry_policy_tests, ReportsTheFailureReasonBeforeTheToolNote) {
  ReportsTheFailureReasonBeforeTheToolNote();
}
TEST(completion_retry_policy_tests, BacksOffExponentially) {
  BacksOffExponentially();
}
TEST(completion_retry_policy_tests, HonoursRetryAfterWithinALimit) {
  HonoursRetryAfterWithinALimit();
}
TEST(completion_retry_policy_tests, AppliesBoundedDeterministicJitter) {
  AppliesBoundedDeterministicJitter();
}
