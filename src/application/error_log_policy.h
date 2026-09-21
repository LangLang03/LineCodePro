#pragma once

#include <string>
#include <string_view>

namespace linecode::application {

[[nodiscard]] bool IsValidErrorLogEntryId(std::string_view entry_id) noexcept;
[[nodiscard]] std::string
SafeTemporaryErrorLogFileName(std::string_view title);

} // namespace linecode::application
