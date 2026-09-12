#include "domain/inline_emphasis.h"

#include <cctype>
#include <cstdint>
#include <optional>
#include <string_view>
#include <utility>

namespace linecode::domain {
namespace {

// `findClosing()` and `codeSpanEnd()` use `String.indexOf()` sentinels in the
// legacy code; an absent match is modelled explicitly so it can never be
// mistaken for a large in-range offset.
constexpr std::size_t kNotMatched = static_cast<std::size_t>(-1);

struct Marker final {
  std::string_view token;
  InlineEmphasisStyle style;
};

/// True for the three characters the legacy escape branch accepts
/// (`InlineEmphasisParser.java:115-117`). A backtick is deliberately not one of
/// them: code spans are handled before the escape branch.
bool IsMarkerCharacter(const char value) noexcept {
  return value == '*' || value == '_' || value == '\\';
}

/// Approximates `Character.isLetterOrDigit()` for one decoded scalar.
///
/// ASCII uses the C classification. Non-ASCII scalars are treated as word
/// characters unless they belong to the punctuation and symbol blocks that
/// matter for prose written in Chinese; Java consults the full Unicode tables
/// here, which this port intentionally approximates.
bool IsUnicodeWordCharacter(const std::uint32_t value) noexcept {
  if (value < 0x80U)
    return std::isalnum(static_cast<unsigned char>(value)) != 0;
  const bool punctuation_or_symbol =
      (value >= 0x2000U && value <= 0x206FU) ||   // General Punctuation
      (value >= 0x2190U && value <= 0x2BFFU) ||   // Arrows, Math, Symbols
      (value >= 0x2E00U && value <= 0x2E7FU) ||   // Supplemental Punctuation
      (value >= 0x3000U && value <= 0x303FU) ||   // CJK Symbols and Punctuation
      (value >= 0xFE10U && value <= 0xFE1FU) ||   // Vertical Forms
      (value >= 0xFE30U && value <= 0xFE6FU) ||   // CJK Compatibility Forms
      (value >= 0xFF01U && value <= 0xFF20U) ||   // Fullwidth !..@
      (value >= 0xFF3BU && value <= 0xFF40U) ||   // Fullwidth [..`
      (value >= 0xFF5BU && value <= 0xFF65U) ||   // Fullwidth {..･
      (value >= 0x1F000U && value <= 0x1FAFFU);   // Emoji and pictographs
  return !punctuation_or_symbol;
}

/// Decodes the scalar that ends just before `offset`; `offset` is always the
/// start of a character because the parser only advances on decoded scalars.
std::uint32_t PreviousScalar(const std::string_view source,
                             const std::size_t offset) noexcept {
  std::size_t begin = offset - 1U;
  while (begin > 0U &&
         (static_cast<unsigned char>(source[begin]) & 0xC0U) == 0x80U)
    --begin;
  const auto lead = static_cast<unsigned char>(source[begin]);
  std::size_t length = 1U;
  if ((lead & 0xE0U) == 0xC0U)
    length = 2U;
  else if ((lead & 0xF0U) == 0xE0U)
    length = 3U;
  else if ((lead & 0xF8U) == 0xF0U)
    length = 4U;
  if (begin + length > source.size() || begin + length != offset)
    return lead;
  std::uint32_t value = lead & ((1U << (7U - length)) - 1U);
  for (std::size_t index = begin + 1U; index < begin + length; ++index)
    value = (value << 6U) |
            (static_cast<unsigned char>(source[index]) & 0x3FU);
  return value;
}

std::uint32_t NextScalar(const std::string_view source,
                         const std::size_t offset) noexcept {
  const auto lead = static_cast<unsigned char>(source[offset]);
  std::size_t length = 1U;
  if ((lead & 0xE0U) == 0xC0U)
    length = 2U;
  else if ((lead & 0xF0U) == 0xE0U)
    length = 3U;
  else if ((lead & 0xF8U) == 0xF0U)
    length = 4U;
  if (offset + length > source.size() || length == 1U)
    return lead;
  std::uint32_t value = lead & ((1U << (7U - length)) - 1U);
  for (std::size_t index = offset + 1U; index < offset + length; ++index)
    value = (value << 6U) |
            (static_cast<unsigned char>(source[index]) & 0x3FU);
  return value;
}

/// `InlineEmphasisParser.java:82-87`.
bool UnderscoreCanOpen(const std::string_view source,
                       const std::size_t offset) noexcept {
  const bool previous_is_word =
      offset > 0U && IsUnicodeWordCharacter(PreviousScalar(source, offset));
  const bool next_is_word = offset + 1U < source.size() &&
                            IsUnicodeWordCharacter(NextScalar(source, offset + 1U));
  return !(previous_is_word && next_is_word);
}

/// `InlineEmphasisParser.java:60-80`. The three-character tokens are tested
/// before the two-character ones, so `***` never degrades into `**` + `*`.
std::optional<Marker> MarkerAt(const std::string_view source,
                               const std::size_t offset) noexcept {
  const auto rest = source.substr(offset);
  if (rest.starts_with("***"))
    return Marker{"***", InlineEmphasisStyle::BoldItalic};
  if (rest.starts_with("___"))
    return Marker{"___", InlineEmphasisStyle::BoldItalic};
  if (rest.starts_with("**"))
    return Marker{"**", InlineEmphasisStyle::Bold};
  if (rest.starts_with("__"))
    return Marker{"__", InlineEmphasisStyle::Bold};
  if (source[offset] == '*')
    return Marker{"*", InlineEmphasisStyle::Italic};
  if (source[offset] == '_' && UnderscoreCanOpen(source, offset))
    return Marker{"_", InlineEmphasisStyle::Italic};
  return std::nullopt;
}

/// `InlineEmphasisParser.java:89-98`. The returned offset is one past the
/// *closing* fence, so a successful result covers the opening fence, the code
/// text and the closing fence, and the caller copies all of it verbatim.
std::size_t CodeSpanEnd(const std::string_view source,
                        const std::size_t offset) noexcept {
  std::size_t marker_length = 1U;
  while (offset + marker_length < source.size() &&
         source[offset + marker_length] == '`')
    ++marker_length;
  const auto marker = source.substr(offset, marker_length);
  const auto closing = source.find(marker, offset + marker_length);
  return closing == std::string_view::npos ? kNotMatched
                                           : closing + marker_length;
}

/// `InlineEmphasisParser.java:100-113`.
std::size_t FindClosing(const std::string_view source,
                        const std::string_view token,
                        const std::size_t from) noexcept {
  std::size_t closing = from <= source.size() ? source.find(token, from)
                                              : std::string_view::npos;
  while (closing != std::string_view::npos) {
    const bool escaped = closing > 0U && source[closing - 1U] == '\\';
    const bool invalid_underscore =
        token == "_" && closing + 1U < source.size() &&
        IsUnicodeWordCharacter(NextScalar(source, closing + 1U));
    if (!escaped && !invalid_underscore)
      return closing;
    const std::size_t next = closing + token.size();
    closing = next <= source.size() ? source.find(token, next)
                                    : std::string_view::npos;
  }
  return kNotMatched;
}

} // namespace

ParsedInlineEmphasis ParseInlineEmphasis(const std::string_view value) {
  ParsedInlineEmphasis parsed;
  std::string& text = parsed.text;
  std::size_t offset = 0U;
  while (offset < value.size()) {
    if (value[offset] == '`') {
      const auto code_span_end = CodeSpanEnd(value, offset);
      if (code_span_end != kNotMatched) {
        text.append(value.substr(offset, code_span_end - offset));
        offset = code_span_end;
        continue;
      }
    }
    if (value[offset] == '\\' && offset + 1U < value.size() &&
        IsMarkerCharacter(value[offset + 1U])) {
      text.push_back(value[offset + 1U]);
      offset += 2U;
      continue;
    }
    const auto marker = MarkerAt(value, offset);
    if (!marker) {
      text.push_back(value[offset]);
      ++offset;
      continue;
    }
    const std::size_t content = offset + marker->token.size();
    const auto closing = FindClosing(value, marker->token, content);
    if (closing == kNotMatched || closing <= content) {
      // No usable closing token: emit only the marker's first character and
      // advance a single byte, exactly like the legacy `charAt` + `offset++`.
      text.push_back(value[offset]);
      ++offset;
      continue;
    }
    const auto start = text.size();
    text.append(value.substr(content, closing - content));
    parsed.spans.push_back(
        InlineEmphasisSpan{start, text.size(), marker->style});
    offset = closing + marker->token.size();
    if (marker->token == "**" && value.substr(offset).starts_with("**"))
      text.append(" | ");
  }
  return parsed;
}

} // namespace linecode::domain
