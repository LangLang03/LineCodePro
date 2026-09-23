#include "gtest_support.h"

#include <string_view>
#include <variant>

#include "domain/tutorial_document.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/streaming_markdown_chunks.h"

namespace {

using linecode::presentation::SplitStreamingMarkdown;

TEST(streaming_markdown_chunks_tests, KeepsFencesAndContainerContinuations) {
  constexpr std::string_view source =
      "# Title\n\n```cpp\nfirst();\n\nsecond();\n```\n\n"
      "- item\n\n  continuation\n\n| Name | Value |\n"
      "| --- | --- |\n| a | b |\n\nFinal **text**";
  const auto chunks = SplitStreamingMarkdown(source);
  EXPECT_EXPRESSION(chunks.size() == 5U);
  EXPECT_EXPRESSION(source.substr(chunks[1].start, chunks[1].length) ==
                    "```cpp\nfirst();\n\nsecond();\n```\n");
  EXPECT_EXPRESSION(source.substr(chunks[2].start, chunks[2].length) ==
                    "- item\n\n  continuation\n");
  const linecode::infrastructure::TutorialMarkdownParser parser;
  const auto code = parser.Parse(source.substr(chunks[1].start, chunks[1].length));
  const auto list = parser.Parse(source.substr(chunks[2].start, chunks[2].length));
  const auto table = parser.Parse(source.substr(chunks[3].start, chunks[3].length));
  EXPECT_EXPRESSION(std::holds_alternative<linecode::domain::TutorialCodeBlock>(
      code.blocks.front()));
  EXPECT_EXPRESSION(std::holds_alternative<linecode::domain::TutorialList>(
      list.blocks.front()));
  EXPECT_EXPRESSION(std::get<linecode::domain::TutorialList>(list.blocks.front())
                        .items.front()
                        .blocks != nullptr);
  EXPECT_EXPRESSION(std::holds_alternative<linecode::domain::TutorialTable>(
      table.blocks.front()));
}

TEST(streaming_markdown_chunks_tests, CompletedChunksStayStableAsTailGrows) {
  constexpr std::string_view before = "First paragraph\n\nSecond **bold";
  constexpr std::string_view after = "First paragraph\n\nSecond **bold** text";
  const auto first = SplitStreamingMarkdown(before);
  const auto second = SplitStreamingMarkdown(after);
  EXPECT_EXPRESSION(first.size() == 2U && second.size() == 2U);
  EXPECT_EXPRESSION(first.front() == second.front());
  EXPECT_EXPRESSION(second.back().start == first.back().start);
}

TEST(streaming_markdown_chunks_tests, KeepsRawHtmlWithBlankLinesTogether) {
  constexpr std::string_view source =
      "Before\n\n<pre>\nline one\n\nline two\n</pre>\n\nAfter";
  const auto chunks = SplitStreamingMarkdown(source);
  EXPECT_EXPRESSION(chunks.size() == 3U);
  EXPECT_EXPRESSION(source.substr(chunks[1].start, chunks[1].length) ==
                    "<pre>\nline one\n\nline two\n</pre>\n");
}

} // namespace
