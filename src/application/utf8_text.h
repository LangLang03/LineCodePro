#pragma once

#include <cstddef>
#include <string_view>

namespace linecode::application::utf8 {
namespace detail {

[[nodiscard]] constexpr bool IsContinuation(unsigned char byte) noexcept {
  return (byte & 0xC0U) == 0x80U;
}

[[nodiscard]] constexpr std::size_t
CodePointWidth(std::string_view value, std::size_t index) noexcept {
  const auto lead = static_cast<unsigned char>(value[index]);
  std::size_t width = 1U;
  if ((lead & 0xE0U) == 0xC0U)
    width = 2U;
  else if ((lead & 0xF0U) == 0xE0U)
    width = 3U;
  else if ((lead & 0xF8U) == 0xF0U)
    width = 4U;
  if (index + width > value.size())
    return 1U;
  for (std::size_t offset = 1U; offset < width; ++offset) {
    if (!IsContinuation(static_cast<unsigned char>(value[index + offset])))
      return 1U;
  }
  return width;
}

} // namespace detail

// Java String.length() counts UTF-16 code units: BMP code points cost one and
// a non-BMP code point costs two. Malformed bytes remain individually visible
// so this helper is total for externally supplied model text.
[[nodiscard]] constexpr std::size_t
Utf16CodeUnitLength(std::string_view value) noexcept {
  std::size_t units = 0U;
  for (std::size_t index = 0U; index < value.size();) {
    const auto width = detail::CodePointWidth(value, index);
    units += width == 4U ? 2U : 1U;
    index += width;
  }
  return units;
}

// Returns a byte prefix containing at most `units` UTF-16 code units while
// always ending on a complete UTF-8 code-point boundary.
[[nodiscard]] constexpr std::size_t
PrefixBytesForUtf16Units(std::string_view value, std::size_t units) noexcept {
  std::size_t index = 0U;
  std::size_t used = 0U;
  while (index < value.size()) {
    const auto width = detail::CodePointWidth(value, index);
    const auto cost = width == 4U ? 2U : 1U;
    if (used + cost > units)
      break;
    used += cost;
    index += width;
  }
  return index;
}

} // namespace linecode::application::utf8
