#include "presentation/components/chat_generation_state.h"

#include <utility>

namespace linecode::presentation {

std::string FormatRetryNotice(const RetryLabels &labels, int attempt,
                              std::string_view error) {
  auto text = labels.attempt;
  for (std::size_t at = text.find(kAttemptMarker); at != std::string::npos;
       at = text.find(kAttemptMarker, at)) {
    text.replace(at, kAttemptMarker.size(), std::to_string(attempt));
    at += 1;
  }
  for (std::size_t at = text.find(kErrorMarker); at != std::string::npos;
       at = text.find(kErrorMarker, at)) {
    text.replace(at, kErrorMarker.size(), error);
    at += error.size();
  }
  return text;
}

std::string FormatModelFailed(std::string_view template_text,
                              std::string_view error) {
  std::string text{template_text};
  for (std::size_t at = text.find(kErrorMarker); at != std::string::npos;
       at = text.find(kErrorMarker, at)) {
    text.replace(at, kErrorMarker.size(), error);
    at += error.size();
  }
  return text;
}

void AutoCompactionUiState::Begin(std::uint64_t generation) {
  generation_id = generation;
  status = std::string{domain::compact_status_running};
  running = true;
}

void AutoCompactionUiState::Finish(std::string next_status) {
  status = std::move(next_status);
  running = false;
}

bool AutoCompactionUiState::RunningFor(
    std::uint64_t generation) const noexcept {
  return running && generation_id == generation;
}

} // namespace linecode::presentation
