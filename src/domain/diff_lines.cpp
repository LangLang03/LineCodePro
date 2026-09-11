#include "domain/diff_lines.h"

#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>

namespace linecode::domain {
namespace {

// Mirrors the legacy `DiffLines.split`: CRLF is normalized, a terminating
// newline does not create a trailing empty line, and every line keeps its own
// terminator so `DiffLine::terminated` can be derived from it.
std::vector<std::string> SplitLines(const std::string_view text) {
  std::vector<std::string> lines;
  if (text.empty())
    return lines;
  std::string normalized{text};
  for (std::size_t index = 0; index + 1 < normalized.size();) {
    if (normalized[index] == '\r' && normalized[index + 1] == '\n')
      normalized.erase(index, 1);
    else
      ++index;
  }
  std::size_t start = 0;
  while (true) {
    const auto newline = normalized.find('\n', start);
    if (newline == std::string::npos) {
      if (start < normalized.size())
        lines.push_back(normalized.substr(start));
      break;
    }
    // Include the newline so the caller can detect the terminator.
    lines.push_back(normalized.substr(start, newline - start + 1));
    start = newline + 1;
    if (start >= normalized.size())
      break;
  }
  return lines;
}

DiffLine MakeLine(const DiffLine::Kind kind, const int number,
                  std::string text) {
  const bool terminated = text.ends_with('\n');
  if (terminated)
    text.pop_back();
  return DiffLine{.kind = kind,
                  .number = number,
                  .text = std::move(text),
                  .terminated = terminated};
}

} // namespace

DiffLines CalculateDiffLines(const std::string_view old_text,
                             const std::string_view new_text) {
  const auto a = SplitLines(old_text);
  const auto b = SplitLines(new_text);
  const auto a_size = static_cast<int>(a.size());
  const auto b_size = static_cast<int>(b.size());

  int prefix = 0;
  while (prefix < a_size && prefix < b_size && a[prefix] == b[prefix])
    ++prefix;
  int suffix = 0;
  while (suffix < a_size - prefix && suffix < b_size - prefix &&
         a[a_size - suffix - 1] == b[b_size - suffix - 1])
    ++suffix;

  DiffLines result;
  for (int index = 0; index < prefix; ++index)
    result.lines.push_back(
        MakeLine(DiffLine::Kind::unchanged, index + 1, a[index]));

  const int m = a_size - prefix - suffix;
  const int n = b_size - prefix - suffix;
  const auto cells = static_cast<std::int64_t>(m + 1) * (n + 1);
  if (cells <= 1'000'000) {
    // lcs[i][j] = LCS length of a[prefix+i..] and b[prefix+j..].
    std::vector<int> lcs(static_cast<std::size_t>(m + 1) *
                             static_cast<std::size_t>(n + 1),
                         0);
    const auto at = [n, &lcs](const int i, const int j) -> int & {
      return lcs[static_cast<std::size_t>(i) *
                     static_cast<std::size_t>(n + 1) +
                 static_cast<std::size_t>(j)];
    };
    for (int i = m - 1; i >= 0; --i) {
      for (int j = n - 1; j >= 0; --j) {
        at(i, j) = a[prefix + i] == b[prefix + j]
                       ? 1 + at(i + 1, j + 1)
                       : std::max(at(i + 1, j), at(i, j + 1));
      }
    }
    int i = 0;
    int j = 0;
    while (i < m || j < n) {
      if (i < m && j < n && a[prefix + i] == b[prefix + j]) {
        result.lines.push_back(
            MakeLine(DiffLine::Kind::unchanged, prefix + j + 1, b[prefix + j]));
        ++i;
        ++j;
      } else if (i < m && (j == n || at(i + 1, j) >= at(i, j + 1))) {
        result.lines.push_back(
            MakeLine(DiffLine::Kind::removed, prefix + i + 1, a[prefix + i]));
        ++i;
      } else {
        result.lines.push_back(
            MakeLine(DiffLine::Kind::added, prefix + j + 1, b[prefix + j]));
        ++j;
      }
    }
  } else {
    for (int i = 0; i < m; ++i)
      result.lines.push_back(
          MakeLine(DiffLine::Kind::removed, prefix + i + 1, a[prefix + i]));
    for (int j = 0; j < n; ++j)
      result.lines.push_back(
          MakeLine(DiffLine::Kind::added, prefix + j + 1, b[prefix + j]));
  }

  for (int j = b_size - suffix; j < b_size; ++j)
    result.lines.push_back(MakeLine(DiffLine::Kind::unchanged, j + 1, b[j]));

  for (const auto &line : result.lines) {
    if (line.kind == DiffLine::Kind::added)
      ++result.added;
    else if (line.kind == DiffLine::Kind::removed)
      ++result.removed;
  }
  return result;
}

} // namespace linecode::domain
