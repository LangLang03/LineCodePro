#pragma once

#include <cstddef>
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

struct TutorialQuote final {
  TutorialInlineLine content;

  bool operator==(const TutorialQuote&) const = default;
};

struct TutorialListItem final {
  std::string marker;
  std::size_t depth = 0;
  TutorialInlineLine content;

  bool operator==(const TutorialListItem&) const = default;
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
                 TutorialList, TutorialCodeBlock, TutorialTable,
                 TutorialThematicBreak>;

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
