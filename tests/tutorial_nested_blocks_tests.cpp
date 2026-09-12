// Tests that the tutorial Markdown parser keeps block-level children inside
// block quotes and list items, the way CommonMark (and therefore the legacy
// `MarkdownRenderer.renderBlockQuote()` / `MarkdownRenderer.addList()`) does.
#include <cassert>
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
  assert(blocks.size() == 1);
  assert(std::holds_alternative<TutorialQuote>(blocks.front()));
  return std::get<TutorialQuote>(blocks.front());
}

std::string InlineText(
    const linecode::domain::TutorialInlineLine& line) {
  return TutorialMarkdownParser::PlainText(line);
}

} // namespace

int main() {
  // A fenced code block inside `>` keeps its own block node inside the quote
  // (`MarkdownRenderer.java:124-128` renders every child of the BlockQuote).
  {
    const auto document = kParser.Parse("> before\n"
                                        "> ```cpp\n"
                                        "> int main() {}\n"
                                        "> ```\n"
                                        "> after\n");
    const auto& quote = OnlyQuote(document.blocks);
    assert(quote.blocks != nullptr);
    const auto& children = quote.blocks->blocks;
    assert(children.size() == 3);
    assert(std::holds_alternative<TutorialParagraph>(children[0]));
    assert(InlineText(std::get<TutorialParagraph>(children[0]).content) ==
           "before");
    assert(std::holds_alternative<TutorialCodeBlock>(children[1]));
    assert(std::get<TutorialCodeBlock>(children[1]).language == "cpp");
    assert(std::get<TutorialCodeBlock>(children[1]).code == "int main() {}");
    assert(std::holds_alternative<TutorialParagraph>(children[2]));
    assert(InlineText(std::get<TutorialParagraph>(children[2]).content) ==
           "after");
  }

  // A quote that only holds text still produces one nested paragraph, and the
  // inline parse of that paragraph is unchanged.
  {
    const auto document = kParser.Parse("> **bold** quote\n");
    const auto& quote = OnlyQuote(document.blocks);
    assert(quote.blocks != nullptr);
    assert(quote.blocks->blocks.size() == 1);
    const auto& paragraph =
        std::get<TutorialParagraph>(quote.blocks->blocks.front());
    assert(paragraph.content.size() == 2);
    assert(paragraph.content.front().text == "bold");
    assert(paragraph.content.front().strong);
    assert(paragraph.content.back().text == " quote");
  }

  // A nested quote keeps nesting.
  {
    const auto document = kParser.Parse("> > inner\n");
    const auto& outer = OnlyQuote(document.blocks);
    assert(outer.blocks != nullptr);
    const auto& inner = OnlyQuote(outer.blocks->blocks);
    assert(inner.blocks != nullptr);
    assert(inner.blocks->blocks.size() == 1);
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
    assert(document.blocks.size() == 1);
    const auto& list = std::get<TutorialList>(document.blocks.front());
    assert(list.items.size() == 2);
    assert(list.items[0].marker == "-");
    assert(list.items[0].depth == 0);
    assert(InlineText(list.items[0].content) == "item");
    assert(list.items[0].blocks != nullptr);
    const auto& nested = list.items[0].blocks->blocks;
    assert(nested.size() == 1);
    assert(std::holds_alternative<TutorialCodeBlock>(nested.front()));
    assert(std::get<TutorialCodeBlock>(nested.front()).language == "sh");
    assert(std::get<TutorialCodeBlock>(nested.front()).code == "echo hi");
    assert(list.items[1].blocks == nullptr);
    assert(InlineText(list.items[1].content) == "second");
  }

  // A dash inside a fenced block is code, not the next list item.
  {
    const auto document = kParser.Parse("- item\n"
                                        "  ```\n"
                                        "  - not an item\n"
                                        "  ```\n");
    const auto& list = std::get<TutorialList>(document.blocks.front());
    assert(list.items.size() == 1);
    assert(list.items[0].blocks != nullptr);
    const auto& nested = list.items[0].blocks->blocks;
    assert(nested.size() == 1);
    assert(std::holds_alternative<TutorialCodeBlock>(nested.front()));
    assert(std::get<TutorialCodeBlock>(nested.front()).code == "- not an item");
  }

  // Nested list markers keep the flat item run this parser has always
  // produced, so `depth` still describes the indentation level.
  {
    const auto document = kParser.Parse("- first\n"
                                        "  - child\n");
    const auto& list = std::get<TutorialList>(document.blocks.front());
    assert(list.items.size() == 2);
    assert(list.items[0].blocks == nullptr);
    assert(list.items[1].depth == 1);
    assert(InlineText(list.items[1].content) == "child");
  }

  // An ordered item's continuation is dedented by its content column (3), so
  // the nested code block loses exactly the container indentation.
  {
    const auto document = kParser.Parse("1. item\n"
                                        "   ```py\n"
                                        "   pass\n"
                                        "   ```\n");
    const auto& list = std::get<TutorialList>(document.blocks.front());
    assert(list.items.size() == 1);
    assert(list.items[0].blocks != nullptr);
    const auto& nested = list.items[0].blocks->blocks;
    assert(nested.size() == 1);
    assert(std::get<TutorialCodeBlock>(nested.front()).code == "pass");
  }

  // Unindented following content still ends the list, exactly as before.
  {
    const auto document = kParser.Parse("- item\n"
                                        "## heading\n");
    assert(document.blocks.size() == 2);
    assert(std::holds_alternative<TutorialList>(document.blocks[0]));
    assert(std::holds_alternative<linecode::domain::TutorialHeading>(
        document.blocks[1]));
  }

  // Equality is by value, including the nested sequences.
  {
    const auto left = kParser.Parse("> a\n> ```\n> b\n> ```\n");
    const auto right = kParser.Parse("> a\n> ```\n> b\n> ```\n");
    const auto other = kParser.Parse("> a\n> ```\n> c\n> ```\n");
    assert(left == right);
    assert(!(left == other));
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
    assert(item.blocks != nullptr);
    assert(std::get<TutorialCodeBlock>(item.blocks->blocks.front()).code ==
           "body");
  }
}
