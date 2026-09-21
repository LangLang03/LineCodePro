#pragma once

#include <cstddef>
#include <string>
#include <string_view>

namespace linecode::application {

inline constexpr std::size_t kMaximumSafeErrorLogText = 1U << 20U;

// Applies the legacy secret patterns to a bounded prefix. The suffix makes
// truncation explicit without exposing bytes beyond the safety limit.
[[nodiscard]] std::string RedactErrorLogText(std::string_view value);

} // namespace linecode::application
