#include "application/error_log_policy.h"

#include <algorithm>

namespace linecode::application {

bool IsValidErrorLogEntryId(std::string_view entry_id) noexcept {
  constexpr std::string_view extension = ".log";
  return !entry_id.empty() && entry_id.size() <= 255U &&
         entry_id != "." && entry_id != ".." &&
         entry_id.find('\0') == std::string_view::npos &&
         entry_id.find('/') == std::string_view::npos &&
         entry_id.find('\\') == std::string_view::npos &&
         entry_id.size() > extension.size() &&
         entry_id.ends_with(extension);
}

std::string SafeTemporaryErrorLogFileName(std::string_view title) {
  constexpr std::size_t maximum_stem_size = 96U;
  std::string stem;
  stem.reserve(std::min(title.size(), maximum_stem_size));
  for (const unsigned char value : title) {
    if (stem.size() == maximum_stem_size) {
      break;
    }
    const bool ascii_alphanumeric =
        (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z') ||
        (value >= '0' && value <= '9');
    if (ascii_alphanumeric || value == '.' || value == '_' || value == '-') {
      stem.push_back(static_cast<char>(value));
    } else {
      stem.push_back('_');
    }
  }
  while (!stem.empty() && stem.front() == '.') {
    stem.erase(stem.begin());
  }
  if (stem.empty()) {
    stem = "linecode-error";
  }
  if (!stem.ends_with(".log")) {
    stem.append(".log");
  }
  return stem;
}

} // namespace linecode::application
