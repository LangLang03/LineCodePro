#pragma once

#include <string_view>

namespace linecode::infrastructure {

// Events are fixed identifiers. Never pass request bodies, paths, or secrets.
void ConfigureAppLogger(std::string_view error_log_directory) noexcept;
void LogAppError(std::string_view event) noexcept;

} // namespace linecode::infrastructure
