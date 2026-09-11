#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::domain {

// One rendered diff line.
struct DiffLine final {
  enum class Kind : std::int8_t { removed = -1, unchanged = 0, added = 1 };

  Kind kind{Kind::unchanged};
  // 1-based line number in the file this line belongs to.
  int number{};
  // Line text without its trailing newline.
  std::string text;
  // False when the source line had no terminating newline, which the write
  // card reports separately.
  bool terminated{true};

  bool operator==(const DiffLine &) const = default;
};

struct DiffLines final {
  std::vector<DiffLine> lines;
  int added{};
  int removed{};

  bool operator==(const DiffLines &) const = default;
};

// Port of the legacy `DiffLines.calculate`: a bounded-memory line diff.
//
// The common prefix and suffix are matched first, then the remaining region
// uses an LCS table when it fits the legacy 1,000,000-cell budget. Larger
// replacement regions degrade to "all removed then all added", which stays
// valid (if non-minimal) instead of allocating an unbounded table.
[[nodiscard]] DiffLines CalculateDiffLines(std::string_view old_text,
                                           std::string_view new_text);

} // namespace linecode::domain
