#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <variant>
#include <vector>

namespace linecode::domain {

struct TutorialInline final {
  std::string text;
  bool strong = false;
  bool emphasis = false;
  bool code = false;
  std::optional<std::string> link;

  bool operator==(const TutorialInline&) const = default;
};

using TutorialInlineLine = std::vector<TutorialInline>;

struct TutorialHeading final {
  std::size_t level = 1;
  TutorialInlineLine content;

  bool operator==(const TutorialHeading&) const = default;
};

struct TutorialParagraph final {
  TutorialInlineLine content;

  bool operator==(const TutorialParagraph&) const = default;
};

// A quote and a list item are block containers in Markdown, so both keep the
// block-level children the legacy `MarkdownRenderer.renderBlockQuote()` and
// `MarkdownRenderer.addList()` rendered inside them (a fenced code block, a
// second paragraph, a nested list, a table, ...). `TutorialBlock` is a variant
// and a variant cannot hold an incomplete type, so the recursive sequence is
// reached through a shared pointer; the value is immutable once parsed and the
// pointers deduplicate when a document is copied.
struct TutorialBlockSequence;

struct TutorialQuote final {
  std::shared_ptr<const TutorialBlockSequence> blocks = nullptr;

  [[nodiscard]] bool operator==(const TutorialQuote& other) const;
};

struct TutorialListItem final {
  std::string marker;
  std::size_t depth = 0;
  /// Inline content of the marker line itself.
  TutorialInlineLine content;
  /// Block-level content indented under the marker line.
  std::shared_ptr<const TutorialBlockSequence> blocks = nullptr;

  [[nodiscard]] bool operator==(const TutorialListItem& other) const;
};

struct TutorialList final {
  bool ordered = false;
  std::vector<TutorialListItem> items;

  bool operator==(const TutorialList&) const = default;
};

struct TutorialCodeBlock final {
  std::string language;
  std::string code;

  bool operator==(const TutorialCodeBlock&) const = default;
};

// A validated, bounded standalone Markdown data image. Keeping immutable
// encoded bytes in the document avoids retaining the original data URI and
// lets the platform renderer defer pixel decoding.
struct TutorialImageBlock final {
  std::string alternative_text;
  std::string mime_type;
  std::vector<std::byte> encoded;
  std::uint32_t pixel_width{};
  std::uint32_t pixel_height{};

  bool operator==(const TutorialImageBlock &) const = default;
};

struct TutorialTable final {
  std::vector<TutorialInlineLine> header;
  std::vector<std::vector<TutorialInlineLine>> rows;

  bool operator==(const TutorialTable&) const = default;
};

struct TutorialThematicBreak final {
  bool operator==(const TutorialThematicBreak&) const = default;
};

using TutorialBlock =
    std::variant<TutorialHeading, TutorialParagraph, TutorialQuote,
                 TutorialList, TutorialCodeBlock, TutorialImageBlock, TutorialTable,
                 TutorialThematicBreak>;

/// Owned block children of a quote or a list item.
///
/// Defined after `TutorialBlock` so the variant is complete; quotes and list
/// items refer to it through `std::shared_ptr<const TutorialBlockSequence>`
/// and compare by value.
struct TutorialBlockSequence final {
  std::vector<TutorialBlock> blocks;

  bool operator==(const TutorialBlockSequence&) const = default;
};

inline bool TutorialQuote::operator==(const TutorialQuote& other) const {
  if (blocks == other.blocks)
    return true;
  return blocks != nullptr && other.blocks != nullptr &&
         *blocks == *other.blocks;
}

inline bool
TutorialListItem::operator==(const TutorialListItem& other) const {
  if (marker != other.marker || depth != other.depth ||
      content != other.content)
    return false;
  if (blocks == other.blocks)
    return true;
  return blocks != nullptr && other.blocks != nullptr &&
         *blocks == *other.blocks;
}

struct TutorialSection final {
  std::string title;
  std::size_t block_index = 0;

  bool operator==(const TutorialSection&) const = default;
};

struct TutorialDocument final {
  std::vector<TutorialBlock> blocks;
  std::vector<TutorialSection> sections;

  bool operator==(const TutorialDocument&) const = default;
};

} // namespace linecode::domain
