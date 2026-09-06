#include "application/error_log_policy.h"

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

} // namespace linecode::application
