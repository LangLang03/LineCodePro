#include "presentation/markdown_link_policy.h"

#include <algorithm>
#include <array>
#include <cctype>

namespace linecode::presentation {
namespace {

bool EqualAsciiCaseInsensitive(const std::string_view left,
                               const std::string_view right) {
  return std::ranges::equal(left, right, [](const unsigned char lhs,
                                            const unsigned char rhs) {
    return std::tolower(lhs) == std::tolower(rhs);
  });
}

} // namespace

std::optional<huxerui::Uri>
ParseNavigableMarkdownLink(const std::string_view value) {
  auto uri = huxerui::Uri::Parse(value);
  if (!uri)
    return std::nullopt;
  constexpr std::array schemes{std::string_view{"http"},
                               std::string_view{"https"}};
  const bool allowed = std::ranges::any_of(schemes, [&](const auto scheme) {
    return EqualAsciiCaseInsensitive(uri->Scheme(), scheme);
  });
  return allowed ? std::move(uri) : std::nullopt;
}

bool IsHttpsMarkdownLink(const huxerui::Uri& value) noexcept {
  return EqualAsciiCaseInsensitive(value.Scheme(), "https");
}

} // namespace linecode::presentation
