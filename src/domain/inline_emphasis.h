#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::domain {

/// Emphasis weights produced by the streamed-reasoning inline parser.
///
/// The numeric values mirror the legacy constants
/// `cn.lineai.ui.theme.InlineEmphasisParser.BOLD/ITALIC/BOLD_ITALIC` so the
/// `typefaceStyle()` mapping keeps the same three-way split.
enum class InlineEmphasisStyle : std::uint8_t {
  Bold = 1,
  Italic = 2,
  BoldItalic = 3,
};

/// One styled range of `ParsedInlineEmphasis::text`.
///
/// Offsets are UTF-8 byte offsets into that text. The legacy parser produced
/// UTF-16 offsets for `SpannableString.setSpan`; because the parser only ever
/// splits on ASCII markers and always appends complete source slices, both
/// encodings describe exactly the same character ranges.
struct InlineEmphasisSpan final {
  std::size_t start = 0;
  std::size_t end = 0;
  InlineEmphasisStyle style = InlineEmphasisStyle::Bold;

  bool operator==(const InlineEmphasisSpan&) const = default;
};

/// Result of parsing one streamed reasoning summary.
struct ParsedInlineEmphasis final {
  /// Marker-stripped text with escapes resolved. Backtick code spans are kept
  /// verbatim, including their backticks.
  std::string text;
  /// Non-overlapping spans in ascending order; the separator inserted between
  /// adjacent `**` runs carries no span.
  std::vector<InlineEmphasisSpan> spans;

  bool operator==(const ParsedInlineEmphasis&) const = default;
};

/// Port of `cn.lineai.ui.theme.InlineEmphasisParser.parse()`.
[[nodiscard]] ParsedInlineEmphasis ParseInlineEmphasis(std::string_view value);

} // namespace linecode::domain
