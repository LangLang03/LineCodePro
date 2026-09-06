#pragma once

#include <string_view>

namespace linecode::application {

[[nodiscard]] bool IsValidErrorLogEntryId(std::string_view entry_id) noexcept;

} // namespace linecode::application
