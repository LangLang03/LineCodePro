#pragma once

#include <cstdint>
#include <string>
#include <string_view>

#include "domain/app_state.h"

namespace linecode::presentation {

// Which of the two status icons a compact block shows.
enum class CompactStatusIcon : std::uint8_t {
  // `IconButtonView.CHECK` (ui-theme `IconButtonView.java:24`).
  check,
  // `IconButtonView.CLOSE` (`IconButtonView.java:18`).
  close,
};

// Port of `cn.lineai.ui.component.ContextCompactBlockView`
// (`app/src/main/java/cn/lineai/ui/component/ContextCompactBlockView.java`).
//
// The view is a single horizontal row:
//   [archive icon 18dp] [label 13sp, 6dp left margin] [weight-1 spacer]
//   [indeterminate progress 18dp (running only)] [status icon 18dp]
// with minHeight 48dp and a 6dp top/bottom padding. This projection resolves
// the status-dependent parts; the geometry is constant and lives in
// `CompactBlockMetrics()`.
struct CompactProgressPresentation final {
  // `bind` defaults an empty status to `running`
  // (ContextCompactBlockView.java:53).
  bool running{};
  bool failed{};
  // `progressBar.setVisibility(running ? VISIBLE : GONE)` (line 63).
  bool show_progress_bar{};
  // `statusIcon.setVisibility(running ? GONE : VISIBLE)` (line 64).
  bool show_status_icon{};
  CompactStatusIcon status_icon{CompactStatusIcon::check};
  // `int color = error ? LineTheme.DANGER : LineTheme.TEXT_TERTIARY` (line 60).
  bool danger{};
  std::string status;

  bool operator==(const CompactProgressPresentation &) const = default;
};

// Fixed geometry of `ContextCompactBlockView` (constructor lines 22-49).
struct CompactBlockMetrics final {
  // `setMinimumHeight(LineTheme.dp(context, 48))` (line 25).
  float min_height{48.0F};
  // `LineTheme.padding(this, 0, 6, 0, 6)` (line 27).
  float vertical_padding{6.0F};
  // `addView(icon, new LayoutParams(dp(18), dp(18)))` (line 32).
  float icon_slot{18.0F};
  // `icon.setIconSizeDp(18, 14)` (line 31).
  float archive_icon_size{14.0F};
  // `labelParams.leftMargin = LineTheme.dp(context, 6)` (line 37).
  float label_left_margin{6.0F};
  // `LineTheme.FONT_SM` (ui-theme `LineTheme.java:52`).
  float label_size{13.0F};
  // `addView(progressBar, new LayoutParams(dp(18), dp(18)))` (line 44).
  float progress_size{18.0F};
  // `setIconSizeDp(18, 13)` (line 48).
  float status_icon_size{13.0F};

  bool operator==(const CompactBlockMetrics &) const = default;
};

[[nodiscard]] constexpr CompactBlockMetrics CompactBlockMetricsDefault()
    noexcept {
  return {};
}

// Resolves the visual state of a compact block from its `compact_status`.
// An empty status is treated as `running`, exactly like `bind`.
[[nodiscard]] CompactProgressPresentation
PresentCompactProgress(std::string_view compact_status) noexcept;

// `ConversationTimeline` (ui/model `ConversationTimeline.java:95-99`) flushes
// the running tool group and gives the compact block a block of its own, so a
// message carrying a compact status never renders as an assistant reply.
[[nodiscard]] bool
IsCompactTimelineBlock(const domain::ChatMessage &message) noexcept;

[[nodiscard]] CompactProgressPresentation
PresentCompactProgress(const domain::ChatMessage &message) noexcept;

} // namespace linecode::presentation
