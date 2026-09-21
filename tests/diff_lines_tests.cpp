// Contract tests for the ported `DiffLines.calculate` line diff.

#include "gtest_support.h"
#include <iostream>
#include <string>

#include "domain/diff_lines.h"

namespace {

using linecode::domain::CalculateDiffLines;
using linecode::domain::DiffLine;

void IdenticalTextHasNoChanges() {
  const auto diff = CalculateDiffLines("a\nb\n", "a\nb\n");
  EXPECT_EXPRESSION(diff.added == 0);
  EXPECT_EXPRESSION(diff.removed == 0);
  EXPECT_EXPRESSION(diff.lines.size() == 2);
  for (const auto &line : diff.lines)
    EXPECT_EXPRESSION(line.kind == DiffLine::Kind::unchanged);
}

void CommonPrefixAndSuffixAreUnchanged() {
  const auto diff = CalculateDiffLines("a\nb\nc\n", "a\nX\nc\n");
  EXPECT_EXPRESSION(diff.added == 1);
  EXPECT_EXPRESSION(diff.removed == 1);
  // First and last lines stay unchanged; the middle is replaced.
  EXPECT_EXPRESSION(diff.lines.front().kind == DiffLine::Kind::unchanged);
  EXPECT_EXPRESSION(diff.lines.front().text == "a");
  EXPECT_EXPRESSION(diff.lines.back().kind == DiffLine::Kind::unchanged);
  EXPECT_EXPRESSION(diff.lines.back().text == "c");
}

void TrailingNewlineIsTrackedPerLine() {
  // Adding or removing the final newline is a real content change: the legacy
  // splitter keeps each line's terminator, so the two forms differ.
  const auto added_newline = CalculateDiffLines("abc", "abc\n");
  EXPECT_EXPRESSION(added_newline.added == 1);
  EXPECT_EXPRESSION(added_newline.removed == 1);

  const auto removed_newline = CalculateDiffLines("abc\n", "abc");
  EXPECT_EXPRESSION(removed_newline.added == 1);
  EXPECT_EXPRESSION(removed_newline.removed == 1);

  // The flag is what lets the card say "No newline at end of file" for an
  // otherwise unchanged last line.
  const auto unchanged = CalculateDiffLines("abc", "abc");
  EXPECT_EXPRESSION(unchanged.lines.size() == 1);
  EXPECT_EXPRESSION(unchanged.lines.front().kind == DiffLine::Kind::unchanged);
  EXPECT_EXPRESSION(!unchanged.lines.front().terminated);
  EXPECT_EXPRESSION(unchanged.lines.front().text == "abc");

  const auto terminated = CalculateDiffLines("abc\n", "abc\n");
  EXPECT_EXPRESSION(terminated.lines.size() == 1);
  EXPECT_EXPRESSION(terminated.lines.front().terminated);
}

void CrlfIsNormalized() {
  // A pure line-ending change is not a content change.
  const auto diff = CalculateDiffLines("a\r\nb\r\n", "a\nb\n");
  EXPECT_EXPRESSION(diff.added == 0);
  EXPECT_EXPRESSION(diff.removed == 0);
}

void EmptyFileTransitions() {
  const auto created = CalculateDiffLines("", "hello\n");
  EXPECT_EXPRESSION(created.added == 1);
  EXPECT_EXPRESSION(created.removed == 0);

  const auto deleted = CalculateDiffLines("hello\n", "");
  EXPECT_EXPRESSION(deleted.added == 0);
  EXPECT_EXPRESSION(deleted.removed == 1);

  const auto both_empty = CalculateDiffLines("", "");
  EXPECT_EXPRESSION(both_empty.lines.empty());
}

void PureInsertAndDelete() {
  const auto inserted = CalculateDiffLines("a\nc\n", "a\nb\nc\n");
  EXPECT_EXPRESSION(inserted.added == 1);
  EXPECT_EXPRESSION(inserted.removed == 0);

  const auto removed = CalculateDiffLines("a\nb\nc\n", "a\nc\n");
  EXPECT_EXPRESSION(removed.added == 0);
  EXPECT_EXPRESSION(removed.removed == 1);
}

void BlankLinesAreRealLines() {
  const auto diff = CalculateDiffLines("a\n\nb\n", "a\n\nb\n");
  EXPECT_EXPRESSION(diff.lines.size() == 3);

  const auto added = CalculateDiffLines("a\nb\n", "a\n\nb\n");
  EXPECT_EXPRESSION(added.added == 1);
  EXPECT_EXPRESSION(added.removed == 0);
}

void LineNumbersFollowTheOwningSide() {
  // Removed lines carry the old numbering, added lines the new one.
  const auto diff = CalculateDiffLines("a\nb\n", "a\nc\n");
  for (const auto &line : diff.lines) {
    if (line.kind == DiffLine::Kind::removed) {
      EXPECT_EXPRESSION(line.number == 2);
    } else if (line.kind == DiffLine::Kind::added) {
      EXPECT_EXPRESSION(line.number == 2);
    }
    else
      EXPECT_EXPRESSION(line.number == 1);
  }
}

void LargeReplacementStaysBoundedButValid() {
  // Above the 1,000,000-cell budget the legacy code degrades to a full
  // replacement. Build two disjoint blocks large enough to cross it.
  std::string before;
  std::string after;
  for (int index = 0; index < 1'200; ++index) {
    before += "old" + std::to_string(index) + "\n";
    after += "new" + std::to_string(index) + "\n";
  }
  const auto diff = CalculateDiffLines(before, after);
  EXPECT_EXPRESSION(diff.added == 1'200);
  EXPECT_EXPRESSION(diff.removed == 1'200);
}

void LargeFileWithSmallEditStaysMinimal() {
  // A big file with one changed line still finds the common prefix/suffix,
  // so the expensive region stays tiny.
  std::string before;
  std::string after;
  for (int index = 0; index < 5'000; ++index) {
    const auto line = "line" + std::to_string(index) + "\n";
    before += line;
    after += index == 2'500 ? "changed\n" : line;
  }
  const auto diff = CalculateDiffLines(before, after);
  EXPECT_EXPRESSION(diff.added == 1);
  EXPECT_EXPRESSION(diff.removed == 1);
}

} // namespace

TEST(diff_lines_tests, LegacySuite) {
  IdenticalTextHasNoChanges();
  CommonPrefixAndSuffixAreUnchanged();
  TrailingNewlineIsTrackedPerLine();
  CrlfIsNormalized();
  EmptyFileTransitions();
  PureInsertAndDelete();
  BlankLinesAreRealLines();
  LineNumbersFollowTheOwningSide();
  LargeReplacementStaysBoundedButValid();
  LargeFileWithSmallEditStaysMinimal();
  std::cout << "diff_lines_tests passed\n";
  return;
}
