// Branch-by-branch port tests for `cn.lineai.ui.theme.InlineEmphasisParser`,
// the parser `ThinkingBlockView.styledContent()` used for streamed reasoning
// summaries.
#include <cassert>
#include <cstddef>
#include <string>
#include <string_view>
#include <vector>

#include "domain/inline_emphasis.h"

namespace {

using linecode::domain::InlineEmphasisSpan;
using linecode::domain::InlineEmphasisStyle;
using linecode::domain::ParseInlineEmphasis;
using linecode::domain::ParsedInlineEmphasis;

void AssertParsed(std::string_view source, std::string_view text,
                  const std::vector<InlineEmphasisSpan>& spans) {
  const auto parsed = ParseInlineEmphasis(source);
  assert(parsed.text == text);
  assert(parsed.spans == spans);
}

/// Convenience for the common case of one span that covers the whole text.
void AssertSingleSpan(std::string_view source, std::string_view text,
                      InlineEmphasisStyle style) {
  AssertParsed(source, text, {InlineEmphasisSpan{0, text.size(), style}});
}

} // namespace

int main() {
  // `InlineEmphasisParser.java:36-47`: plain text passes through untouched.
  AssertParsed("", "", {});
  AssertParsed("plain summary", "plain summary", {});

  // `markerAt()` (`:60-80`): each token maps to one of the three legacy style
  // constants, with the three-character tokens winning over the two-character
  // ones.
  AssertSingleSpan("**bold**", "bold", InlineEmphasisStyle::Bold);
  AssertSingleSpan("__bold__", "bold", InlineEmphasisStyle::Bold);
  AssertSingleSpan("*italic*", "italic", InlineEmphasisStyle::Italic);
  AssertSingleSpan("***both***", "both", InlineEmphasisStyle::BoldItalic);
  AssertSingleSpan("___both___", "both", InlineEmphasisStyle::BoldItalic);
  // `underscoreCanOpen()` (`:82-87`): an intraword underscore stays literal.
  AssertParsed("foo_bar_baz", "foo_bar_baz", {});
  // A word boundary on either side lets the underscore open emphasis.
  AssertSingleSpan("_italic_", "italic", InlineEmphasisStyle::Italic);
  AssertParsed("a _italic_", "a italic",
               {InlineEmphasisSpan{2, 8, InlineEmphasisStyle::Italic}});
  // `findClosing()` (`:100-113`): a closing underscore followed by a word
  // character is skipped, so the search continues to the next candidate.
  AssertParsed("_a_b_", "a_b",
               {InlineEmphasisSpan{0, 3, InlineEmphasisStyle::Italic}});

  // The `" | "` separator (`:53-55`) is appended between two adjacent `**`
  // runs and carries no span of its own.
  AssertParsed("**a****b**", "a | b",
               {InlineEmphasisSpan{0, 1, InlineEmphasisStyle::Bold},
                InlineEmphasisSpan{4, 5, InlineEmphasisStyle::Bold}});
  // The separator belongs to `**` only: `__` runs stay adjacent.
  AssertParsed("__a____b__", "ab",
               {InlineEmphasisSpan{0, 1, InlineEmphasisStyle::Bold},
                InlineEmphasisSpan{1, 2, InlineEmphasisStyle::Bold}});
  // The separator is only inserted when the next characters really are `**`;
  // a space between the two runs keeps them apart and parses both.
  AssertParsed("**a** **b**", "a b",
               {InlineEmphasisSpan{0, 1, InlineEmphasisStyle::Bold},
                InlineEmphasisSpan{2, 3, InlineEmphasisStyle::Bold}});

  // `:30-35`: a backslash escapes a marker character, which is emitted alone
  // and produces no span.
  AssertParsed("\\*not italic\\*", "*not italic*", {});
  AssertParsed("\\\\", "\\", {});
  // Only the three marker characters are escapable.
  AssertParsed("\\`x\\`", "\\`x\\`", {});

  // `:21-29` with `codeSpanEnd()` (`:89-98`): the whole code span is copied
  // verbatim, including the opening and closing backticks, and its contents are
  // never emphasis-parsed.
  AssertParsed("`**raw**`", "`**raw**`", {});
  AssertParsed("use `a_b` here", "use `a_b` here", {});
  // Multi-backtick fences match their own width and are kept verbatim too.
  AssertParsed("``a`b``", "``a`b``", {});
  AssertParsed("`a``b`", "`a``b`", {});
  // An unterminated backtick falls through to the literal path and advances a
  // single character (`codeSpanEnd()` returns -1, so `codeSpanEnd > offset`
  // fails in `:24`).
  AssertParsed("`unclosed", "`unclosed", {});
  AssertParsed("a `b *c*", "a `b c",
               {InlineEmphasisSpan{5, 6, InlineEmphasisStyle::Italic}});

  // `:43-47`: without a usable closing token only the marker's first character
  // is emitted and the offset advances by one, so `**` degrades to two literal
  // characters instead of being skipped as a unit.
  AssertParsed("**unclosed", "**unclosed", {});
  AssertParsed("an * unmatched", "an * unmatched", {});
  AssertParsed("****", "****", {});

  // Emphasis content is copied raw, so markers inside a span are not parsed
  // again (nesting is *not* recursive) and only the outer range is styled.
  AssertParsed("**a *b* c**", "a *b* c",
               {InlineEmphasisSpan{0, 7, InlineEmphasisStyle::Bold}});
  // A backslash-escaped closing token is skipped by `findClosing()`, but the
  // span body is copied raw, so the backslash inside it survives.
  AssertParsed("*a\\*b*", "a\\*b",
               {InlineEmphasisSpan{0, 4, InlineEmphasisStyle::Italic}});

  // Mixed content keeps span offsets aligned with the emitted text.
  AssertParsed("see **bold**, `code` and \\*literal\\*",
               "see bold, `code` and *literal*",
               {InlineEmphasisSpan{4, 8, InlineEmphasisStyle::Bold}});

  // Chinese reasoning text: `_` between two word characters stays literal
  // (`Character.isLetterOrDigit` is true for Han characters), while a `_` after
  // a full-width comma can still open emphasis.
  AssertParsed("变量_a_b 串联", "变量_a_b 串联", {});
  AssertParsed("说明，_强调_", "说明，强调",
               {InlineEmphasisSpan{9, 15, InlineEmphasisStyle::Italic}});

  // Adjacent spans of different styles stay ordered and non-overlapping.
  const auto mixed = ParseInlineEmphasis("**a***b*");
  assert(mixed.text == "ab");
  assert(mixed.spans.size() == 2);
  assert((mixed.spans[0] ==
          InlineEmphasisSpan{0, 1, InlineEmphasisStyle::Bold}));
  assert((mixed.spans[1] ==
          InlineEmphasisSpan{1, 2, InlineEmphasisStyle::Italic}));

  // Every span of a larger input stays inside the emitted text and is ordered.
  const ParsedInlineEmphasis document =
      ParseInlineEmphasis("**一**`二`*三* plain **四**");
  std::size_t previous_end = 0;
  for (const auto& span : document.spans) {
    assert(span.start >= previous_end);
    assert(span.end <= document.text.size());
    assert(span.start < span.end);
    previous_end = span.end;
  }
  assert(document.spans.size() == 3);
}
