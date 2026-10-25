#pragma once

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <optional>

#include "application/ports/completion_gateway.h"

namespace linecode::application {

// Attempt budget for one model request, including the first attempt. Kept here
// so the retry policy, the "retrying n/m" notice and the tests share one value.
inline constexpr int kMaximumCompletionAttempts = 3;

inline constexpr std::chrono::milliseconds kFirstRetryDelay{1'000};
inline constexpr std::chrono::milliseconds kMaximumRetryDelay{30'000};
inline constexpr std::int64_t kMaximumRetryAfterSeconds{60};

// Why an attempt was not retried. The runner turns this into a localized
// sentence, so the policy stays free of user-facing text.
enum class RetryBlockReason : std::uint8_t {
  none,
  attempt_budget_exhausted,
  not_retryable,
  state_changing_tools_ran,
};

struct RetryInput final {
  CompletionErrorCode code{CompletionErrorCode::transport};
  int http_status{};
  // `Retry-After` from the response, when the transport exposed it.
  std::optional<std::int64_t> retry_after_seconds{};
  // What the failed attempt actually did. A retry replays the whole tool loop
  // from the same request snapshot, so every executed call runs a second time.
  std::size_t executed_tool_calls{};
  std::size_t mutating_tool_calls{};
  // Zero-based index of the attempt that just failed.
  int attempt{};
};

struct RetryDecision final {
  bool retry{};
  std::chrono::milliseconds delay{};
  RetryBlockReason block_reason{RetryBlockReason::none};
};

// A retry is only worth issuing when the server or the network failed
// transiently. Deterministic answers (bad request, bad key, unknown model,
// unsupported protocol, malformed stream, tool-call budget) cannot succeed on a
// second identical request and only burn the user's time and money.
[[nodiscard]] constexpr bool IsRetryableHttpStatus(int status) noexcept {
  switch (status) {
  case 408: // request timeout
  case 409: // conflict
  case 425: // too early
  case 429: // rate limited
  case 500:
  case 502:
  case 503:
  case 504:
    return true;
  default:
    return false;
  }
}

[[nodiscard]] constexpr std::chrono::milliseconds
RetryBackoffForAttempt(int attempt) noexcept {
  auto delay = kFirstRetryDelay;
  for (int index = 0; index < attempt && delay < kMaximumRetryDelay; ++index)
    delay = std::min(delay * 2, kMaximumRetryDelay);
  return delay;
}

// Spreads retries over a window instead of letting every device retry on the
// same clock tick. `seed` keeps the function pure so tests can pin the result.
[[nodiscard]] constexpr std::chrono::milliseconds
ApplyRetryJitter(std::chrono::milliseconds delay,
                 std::uint64_t seed) noexcept {
  const auto base = delay.count();
  if (base <= 1)
    return delay;
  const auto span = static_cast<std::int64_t>(base / 2);
  const auto offset =
      static_cast<std::int64_t>((seed * 2'654'435'761ULL) %
                                static_cast<std::uint64_t>(span + 1)) -
      span / 2;
  return std::chrono::milliseconds{std::max<std::int64_t>(base + offset, 1)};
}

[[nodiscard]] inline RetryDecision
DecideCompletionRetry(const RetryInput &input, std::uint64_t jitter_seed) {
  if (input.attempt + 1 >= kMaximumCompletionAttempts)
    return {.retry = false,
            .delay = {},
            .block_reason = RetryBlockReason::attempt_budget_exhausted};
  const bool retryable =
      input.code == CompletionErrorCode::transport ||
      input.code == CompletionErrorCode::timeout ||
      (input.code == CompletionErrorCode::http_status &&
       IsRetryableHttpStatus(input.http_status));
  if (!retryable)
    return {.retry = false,
            .delay = {},
            .block_reason = RetryBlockReason::not_retryable};
  // Automatic retry must never repeat a side effect that already happened.
  if (input.mutating_tool_calls > 0)
    return {.retry = false,
            .delay = {},
            .block_reason = RetryBlockReason::state_changing_tools_ran};

  auto delay = RetryBackoffForAttempt(input.attempt);
  if (input.retry_after_seconds && *input.retry_after_seconds > 0) {
    const auto requested = std::chrono::seconds{
        std::min(*input.retry_after_seconds, kMaximumRetryAfterSeconds)};
    delay = std::max(delay, std::chrono::duration_cast<std::chrono::milliseconds>(
                                requested));
  }
  return {.retry = true,
          .delay = ApplyRetryJitter(delay, jitter_seed),
          .block_reason = RetryBlockReason::none};
}

} // namespace linecode::application
