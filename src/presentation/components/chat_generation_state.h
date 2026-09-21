#pragma once

#include <cstdint>
#include <string>
#include <string_view>

#include "domain/compaction_progress.h"

namespace linecode::presentation {

inline constexpr int kMaxGenerationAttempts = 3;
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
};

[[nodiscard]] std::string FormatRetryNotice(const RetryLabels &labels,
                                            int attempt,
                                            std::string_view error);
[[nodiscard]] std::string FormatModelFailed(std::string_view template_text,
                                            std::string_view error);

struct AutoCompactionUiState final {
  std::uint64_t generation_id{};
  bool running{};
  std::string status{std::string{domain::compact_status_running}};

  void Begin(std::uint64_t generation);
  void Finish(std::string next_status);
  [[nodiscard]] bool RunningFor(std::uint64_t generation) const noexcept;
};

} // namespace linecode::presentation
