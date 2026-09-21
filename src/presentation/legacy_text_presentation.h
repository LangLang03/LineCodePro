#pragma once

#include <string>

namespace linecode::presentation {

/// Matches Locale.ROOT uppercasing for the ASCII section labels shipped by
/// LineCode while leaving localized UTF-8 text (including Chinese) untouched.
[[nodiscard]] inline std::string LegacySectionTitle(std::string title) {
  for (char &character : title) {
    if (character >= 'a' && character <= 'z')
      character = static_cast<char>(character - 'a' + 'A');
  }
  return title;
}

} // namespace linecode::presentation
