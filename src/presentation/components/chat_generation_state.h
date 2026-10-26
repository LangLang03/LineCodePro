#pragma once

#include <atomic>
#include <chrono>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

#include "application/completion_retry_policy.h"
#include "domain/compaction_progress.h"

namespace linecode::presentation {

// The retry budget lives in the policy header so the send path, the "retrying
// n/m" notice and the tests cannot drift apart.
inline constexpr int kMaxGenerationAttempts =
    application::kMaximumCompletionAttempts;
// A socket that stays open without producing a single stream event would
// otherwise sit until the transport's whole-operation deadline expires.
inline constexpr auto kCompletionIdleTimeout = std::chrono::seconds{180};
inline constexpr auto kCompletionIdlePollInterval = std::chrono::seconds{15};
inline constexpr std::string_view kAttemptMarker{"\x1f"
                                                 "0"
                                                 "\x1f"};
inline constexpr std::string_view kErrorMarker{"\x1f"
                                               "2"
                                               "\x1f"};

struct RetryLabels final {
  std::string attempt;
  std::string failed;
  std::string no_model;
  std::string model_missing;
  // Appended to a terminal failure when the request was not retried because the
  // earlier attempt had already changed the workspace. Defaulted so the existing
  // designated initializers keep compiling under -Wmissing-field-initializers.
  std::string skipped_writes{};
  // Shown when the idle watchdog stopped a stalled request.
  std::string idle_timeout{};
  // Shown instead when the request stalled while it was still loading the
  // weights, where "the model stopped sending data" would be misleading.
  std::string load_timeout{};
};

[[nodiscard]] std::string FormatRetryNotice(const RetryLabels &labels,
                                            int attempt,
                                            std::string_view error);
[[nodiscard]] std::string FormatModelFailed(std::string_view template_text,
                                            std::string_view error);

// Tracks the GGUF load a local turn performs before it can stream anything. The
// engine reports progress from its own loading thread, so the published value is
// atomic; the runner republishes a revision whenever it moves and the status row
// reads it through the conversation surface.
struct LocalModelLoadUiState final {
  std::atomic<bool> loading{false};
  std::atomic<int> percent{0};
  // The generation that armed the label. Only the runtime thread touches it, so
  // it stays a plain integer; the atomics above are what the loading thread and
  // the renderer share.
  std::uint64_t owner{0};

  // Arms the label for `generation`. A newer generation supersedes an older
  // one, so a turn that ends late cannot clear a label its successor just
  // raised.
  void Begin(std::uint64_t generation) noexcept;
  void Report(int value) noexcept;
  // Clears the label, but only while `generation` still owns it.
  void Finish(std::uint64_t generation) noexcept;
};

// The percentage to display, or nothing when no load is in flight. Kept as a
// free function so the renderer and its tests agree on what "loading" means.
[[nodiscard]] std::optional<int>
LocalModelLoadPercent(const LocalModelLoadUiState &state) noexcept;

struct AutoCompactionUiState final {
  std::uint64_t generation_id{};
  bool running{};
  std::string status{std::string{domain::compact_status_running}};

  void Begin(std::uint64_t generation);
  void Finish(std::string next_status);
  [[nodiscard]] bool RunningFor(std::uint64_t generation) const noexcept;
};

} // namespace linecode::presentation
