#include "infrastructure/completion_end_marker.h"

#include <cctype>
#include <cstddef>
#include <string_view>
#include <utility>

namespace linecode::infrastructure {
namespace {

// Every non-alphanumeric byte (ASCII punctuation and any UTF-8 continuation or
// lead byte, i.e. `_`, `-`, `|`, `▁`, `｜`) becomes this placeholder, so the
// name can be compared without decoding UTF-8.
constexpr char kSeparator = '\x01';

[[nodiscard]] std::string CanonicalName(const std::string_view raw) {
  std::string canonical;
  canonical.reserve(raw.size());
  for (const char value : raw) {
    const auto byte = static_cast<unsigned char>(value);
    if (byte >= 0x80U || std::isalnum(byte) == 0) {
      canonical.push_back(kSeparator);
      continue;
    }
    canonical.push_back(static_cast<char>(std::tolower(byte)));
  }
  return canonical;
}

[[nodiscard]] std::string_view TrimSeparators(
    const std::string_view canonical) noexcept {
  const auto first = canonical.find_first_not_of(kSeparator);
  if (first == std::string_view::npos) {
    return {};
  }
  const auto last = canonical.find_last_not_of(kSeparator);
  return canonical.substr(first, last - first + 1U);
}

// True while `name` (the text between `<` and the end of the buffer) can still
// grow into an end marker, so the filter has to wait for the next delta.
[[nodiscard]] bool CouldStillBeMarker(const std::string_view name) {
  std::string letters;
  for (const char value : name) {
    const auto byte = static_cast<unsigned char>(value);
    if (byte >= 0x80U || std::isalnum(byte) == 0) {
      continue;
    }
    letters.push_back(static_cast<char>(std::tolower(byte)));
  }
  if (letters.empty()) {
    // "<", "<|", "<｜"
    return true;
  }
  constexpr std::string_view kEnd{"end"};
  constexpr std::string_view kEot{"eot"};
  constexpr std::string_view kIm{"im"};
  if (kEnd.starts_with(letters) || kEot.starts_with(letters) ||
      kIm.starts_with(letters)) {
    return true;
  }
  if (letters.starts_with(kEnd)) {
    // "end" already looks like a marker (end_of_tu…) or has diverged
    // ("endpoint" is ordinary markup and is emitted as text).
    return IsCompletionEndMarkerName(name);
  }
  return false;
}

} // namespace

bool IsCompletionEndMarkerName(const std::string_view name) noexcept {
  const auto canonical = CanonicalName(name);
  const auto trimmed = TrimSeparators(canonical);
  if (trimmed.empty()) {
    return false;
  }
  constexpr std::string_view kEnd{"end"};
  constexpr std::string_view kEndOf{"endof"};
  constexpr std::string_view kEndSeparator{"end" "\x01"};
  constexpr std::string_view kEot{"eot"};
  constexpr std::string_view kEotSeparator{"eot" "\x01"};
  constexpr std::string_view kImEnd{"im" "\x01" "end"};
  constexpr std::string_view kImEndJoined{"imend"};
  return trimmed == kEnd || trimmed == kEot || trimmed == kImEndJoined ||
         trimmed.starts_with(kEndSeparator) ||
         trimmed.starts_with(kEndOf) || trimmed.starts_with(kEotSeparator) ||
         trimmed.starts_with(kImEnd);
}

std::string CompletionEndMarkerFilter::Push(const std::string_view delta) {
  if (terminated_) {
    return {};
  }
  held_.append(delta);
  std::string visible;
  std::size_t index = 0U;
  while (index < held_.size()) {
    const auto open = held_.find('<', index);
    if (open == std::string::npos) {
      visible.append(held_, index, std::string::npos);
      index = held_.size();
      break;
    }
    visible.append(held_, index, open - index);
    const auto close = held_.find('>', open + 1U);
    if (close == std::string::npos) {
      const std::string_view candidate{held_.data() + open + 1U,
                                       held_.size() - open - 1U};
      if (CouldStillBeMarker(candidate)) {
        held_.erase(0U, open);
        return visible;
      }
      // Ordinary markup: emit the '<' and keep scanning after it.
      visible.push_back('<');
      index = open + 1U;
      continue;
    }
    const std::string_view candidate{held_.data() + open + 1U,
                                     close - open - 1U};
    if (IsCompletionEndMarkerName(candidate)) {
      terminated_ = true;
      held_.clear();
      return visible;
    }
    visible.append(held_, open, close - open + 1U);
    index = close + 1U;
  }
  held_.clear();
  return visible;
}

std::string CompletionEndMarkerFilter::Flush() {
  if (terminated_) {
    return {};
  }
  std::string tail = std::exchange(held_, {});
  return tail;
}

std::string StripCompletionEndMarkers(const std::string_view text) {
  CompletionEndMarkerFilter filter;
  std::string stripped = filter.Push(text);
  stripped += filter.Flush();
  return stripped;
}

} // namespace linecode::infrastructure
