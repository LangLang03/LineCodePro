#include "presentation/compaction_progress_presentation.h"

#include "domain/compaction_progress.h"

namespace linecode::presentation {

CompactProgressPresentation
PresentCompactProgress(const std::string_view compact_status) noexcept {
  // `ContextCompactBlockView.bind` line 53: a null/empty status is `running`.
  const auto status = compact_status.empty()
                          ? std::string{domain::compact_status_running}
                          : std::string{compact_status};
  const bool running = status == domain::compact_status_running;
  const bool error = status == domain::compact_status_error;
  CompactProgressPresentation presentation;
  presentation.running = running;
  presentation.failed = error;
  presentation.show_progress_bar = running;
  presentation.show_status_icon = !running;
  presentation.status_icon =
      error ? CompactStatusIcon::close : CompactStatusIcon::check;
  presentation.danger = error;
  presentation.status = status;
  return presentation;
}

bool IsCompactTimelineBlock(const domain::ChatMessage &message) noexcept {
  return domain::IsCompactBlock(message);
}

CompactProgressPresentation
PresentCompactProgress(const domain::ChatMessage &message) noexcept {
  return PresentCompactProgress(message.compact_status);
}

} // namespace linecode::presentation
