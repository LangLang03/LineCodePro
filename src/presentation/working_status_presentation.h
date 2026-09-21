#pragma once

#include <array>
#include <cstddef>

namespace linecode::presentation {

inline constexpr float working_status_cycle_millis = 1'500.0F;
inline constexpr float working_status_frame_millis = 90.0F;
inline constexpr float working_status_text_start = 25.0F;
inline constexpr float working_status_shimmer_half_width = 72.0F;

struct WorkingStatusAnimationFrame final {
  int active_dot{};
  int trailing_dot{};
  float shimmer_center{};

  bool operator==(const WorkingStatusAnimationFrame &) const = default;
};

// The perimeter order and shared 1.5 s progress exactly mirror the legacy
// WorkingStatusView ValueAnimator. Keeping this policy outside the renderer
// makes new visual backends consume the same sequence without conditionals.
[[nodiscard]] constexpr WorkingStatusAnimationFrame
PresentWorkingStatusFrame(float progress, float text_width) noexcept {
  constexpr std::array offsets{1, 2, 5, 8, 7, 6, 3, 0};
  const auto frame = static_cast<std::size_t>(
      progress * working_status_cycle_millis / working_status_frame_millis);
  const auto active = frame % offsets.size();
  const auto trailing = (active + offsets.size() - 1U) % offsets.size();
  return WorkingStatusAnimationFrame{
      .active_dot = offsets[active],
      .trailing_dot = offsets[trailing],
      .shimmer_center =
          working_status_text_start - working_status_shimmer_half_width +
          progress * (text_width + working_status_shimmer_half_width * 2.0F),
  };
}

} // namespace linecode::presentation
