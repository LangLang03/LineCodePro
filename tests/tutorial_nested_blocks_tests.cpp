// Tests that the tutorial Markdown parser keeps block-level children inside
// block quotes and list items, the way CommonMark (and therefore the legacy
// `MarkdownRenderer.renderBlockQuote()` / `MarkdownRenderer.addList()`) does.
#include "gtest_support.h"
#include <cstddef>
#include <string>
#include <string_view>
#include <variant>

#include "domain/tutorial_document.h"
#include "infrastructure/tutorial_markdown_parser.h"

namespace {

using linecode::domain::TutorialBlock;
using linecode::domain::TutorialCodeBlock;
using linecode::domain::TutorialList;
using linecode::domain::TutorialListItem;
using linecode::domain::TutorialParagraph;
using linecode::domain::TutorialQuote;
using linecode::infrastructure::TutorialMarkdownParser;

const TutorialMarkdownParser kParser;

const TutorialQuote& OnlyQuote(const std::vector<TutorialBlock>& blocks) {
  EXPECT_EXPRESSION(blocks.size() == 1);
  EXPECT_EXPRESSION(std::holds_alternative<TutorialQuote>(blocks.front()));
  return std::get<TutorialQuote>(blocks.front());
}

std::string InlineText(
    const linecode::domain::TutorialInlineLine& line) {
  return TutorialMarkdownParser::PlainText(line);
}

} // namespace

TEST(tutorial_nested_blocks_tests, LegacySuite) {
  // A fenced code block inside `>` keeps its own block node inside the quote
  // (`MarkdownRenderer.java:124-128` renders every child of the BlockQuote).
  {
    const auto document = kParser.Parse("> before\n"
                                        "> ```cpp\n"
                                        "> int main() {}\n"
                                        "> ```\n"
                                        "> after\n");
    const auto& quote = OnlyQuote(document.blocks);
    EXPECT_EXPRESSION(quote.blocks != nullptr);
    const auto& children = quote.blocks->blocks;
    EXPECT_EXPRESSION(children.size() == 3);
    EXPECT_EXPRESSION(std::holds_alternative<TutorialParagraph>(children[0]));
    EXPECT_EXPRESSION(InlineText(std::get<TutorialParagraph>(children[0]).content) ==
           "before");
    EXPECT_EXPRESSION(std::holds_alternative<TutorialCodeBlock>(children[1]));
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(children[1]).language == "cpp");
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(children[1]).code == "int main() {}");
    EXPECT_EXPRESSION(std::holds_alternative<TutorialParagraph>(children[2]));
    EXPECT_EXPRESSION(InlineText(std::get<TutorialParagraph>(children[2]).content) ==
           "after");
  }

  // A quote that only holds text still produces one nested paragraph, and the
  // inline parse of that paragraph is unchanged.
  {
    const auto document = kParser.Parse("> **bold** quote\n");
    const auto& quote = OnlyQuote(document.blocks);
    EXPECT_EXPRESSION(quote.blocks != nullptr);
    EXPECT_EXPRESSION(quote.blocks->blocks.size() == 1);
    const auto& paragraph =
        std::get<TutorialParagraph>(quote.blocks->blocks.front());
    EXPECT_EXPRESSION(paragraph.content.size() == 2);
    EXPECT_EXPRESSION(paragraph.content.front().text == "bold");
    EXPECT_EXPRESSION(paragraph.content.front().strong);
    EXPECT_EXPRESSION(paragraph.content.back().text == " quote");
  }

  // A nested quote keeps nesting.
  {
    const auto document = kParser.Parse("> > inner\n");
    const auto& outer = OnlyQuote(document.blocks);
    EXPECT_EXPRESSION(outer.blocks != nullptr);
    const auto& inner = OnlyQuote(outer.blocks->blocks);
    EXPECT_EXPRESSION(inner.blocks != nullptr);
    EXPECT_EXPRESSION(inner.blocks->blocks.size() == 1);
  }

  // A code block indented under a list item stays inside that item instead of
  // escaping to the top level (`MarkdownRenderer.java:154-155`).
  {
    const auto document = kParser.Parse("- item\n"
                                        "\n"
                                        "  ```sh\n"
                                        "  echo hi\n"
                                        "  ```\n"
                                        "- second\n");
    EXPECT_EXPRESSION(document.blocks.size() == 1);
    const auto& list = std::get<TutorialList>(document.blocks.front());
    EXPECT_EXPRESSION(list.items.size() == 2);
    EXPECT_EXPRESSION(list.items[0].marker == "-");
    EXPECT_EXPRESSION(list.items[0].depth == 0);
    EXPECT_EXPRESSION(InlineText(list.items[0].content) == "item");
    EXPECT_EXPRESSION(list.items[0].blocks != nullptr);
    const auto& nested = list.items[0].blocks->blocks;
    EXPECT_EXPRESSION(nested.size() == 1);
    EXPECT_EXPRESSION(std::holds_alternative<TutorialCodeBlock>(nested.front()));
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(nested.front()).language == "sh");
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(nested.front()).code == "echo hi");
    EXPECT_EXPRESSION(list.items[1].blocks == nullptr);
    EXPECT_EXPRESSION(InlineText(list.items[1].content) == "second");
  }

  // A dash inside a fenced block is code, not the next list item.
  {
    const auto document = kParser.Parse("- item\n"
                                        "  ```\n"
                                        "  - not an item\n"
                                        "  ```\n");
    const auto& list = std::get<TutorialList>(document.blocks.front());
    EXPECT_EXPRESSION(list.items.size() == 1);
    EXPECT_EXPRESSION(list.items[0].blocks != nullptr);
    const auto& nested = list.items[0].blocks->blocks;
    EXPECT_EXPRESSION(nested.size() == 1);
    EXPECT_EXPRESSION(std::holds_alternative<TutorialCodeBlock>(nested.front()));
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(nested.front()).code == "- not an item");
  }

  // Nested list markers keep the flat item run this parser has always
  // produced, so `depth` still describes the indentation level.
  {
    const auto document = kParser.Parse("- first\n"
                                        "  - child\n");
    const auto& list = std::get<TutorialList>(document.blocks.front());
    EXPECT_EXPRESSION(list.items.size() == 2);
    EXPECT_EXPRESSION(list.items[0].blocks == nullptr);
    EXPECT_EXPRESSION(list.items[1].depth == 1);
    EXPECT_EXPRESSION(InlineText(list.items[1].content) == "child");
  }

  // An ordered item's continuation is dedented by its content column (3), so
  // the nested code block loses exactly the container indentation.
  {
    const auto document = kParser.Parse("1. item\n"
                                        "   ```py\n"
                                        "   pass\n"
                                        "   ```\n");
    const auto& list = std::get<TutorialList>(document.blocks.front());
    EXPECT_EXPRESSION(list.items.size() == 1);
    EXPECT_EXPRESSION(list.items[0].blocks != nullptr);
    const auto& nested = list.items[0].blocks->blocks;
    EXPECT_EXPRESSION(nested.size() == 1);
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(nested.front()).code == "pass");
  }

  // Unindented following content still ends the list, exactly as before.
  {
    const auto document = kParser.Parse("- item\n"
                                        "## heading\n");
    EXPECT_EXPRESSION(document.blocks.size() == 2);
    EXPECT_EXPRESSION(std::holds_alternative<TutorialList>(document.blocks[0]));
    EXPECT_EXPRESSION(std::holds_alternative<linecode::domain::TutorialHeading>(
        document.blocks[1]));
  }

  // Equality is by value, including the nested sequences.
  {
    const auto left = kParser.Parse("> a\n> ```\n> b\n> ```\n");
    const auto right = kParser.Parse("> a\n> ```\n> b\n> ```\n");
    const auto other = kParser.Parse("> a\n> ```\n> c\n> ```\n");
    EXPECT_EXPRESSION(left == right);
    EXPECT_EXPRESSION(!(left == other));
  }

  // A list item remembers its nested blocks when the document is copied.
  {
    const auto document = kParser.Parse("- item\n"
                                        "  ```\n"
                                        "  body\n"
                                        "  ```\n");
    const auto copy = document;
    const TutorialListItem& item =
        std::get<TutorialList>(copy.blocks.front()).items.front();
    EXPECT_EXPRESSION(item.blocks != nullptr);
    EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(item.blocks->blocks.front()).code ==
           "body");
  }
}
