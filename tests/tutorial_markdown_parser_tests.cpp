#include <algorithm>
#include <array>
#include "gtest_support.h"
#include <fstream>
#include <iterator>
#include <string>
#include <string_view>
#include <variant>

#include "domain/tutorial_document.h"
#include "infrastructure/tutorial_markdown_parser.h"

namespace {

std::string ReadFile(std::string_view path) {
  std::ifstream stream(std::string(path), std::ios::binary);
  EXPECT_EXPRESSION(stream && "tutorial raw resource must exist");
  return {std::istreambuf_iterator<char>(stream),
          std::istreambuf_iterator<char>()};
}

void AssertSanitized(std::string_view source) {
  constexpr std::string_view forbidden[] = {
      "无障碍", "手机控制", "控制手机", "控制模式", "Phone Control",
      "Accessibility"};
  for (const auto token : forbidden)
    EXPECT_EXPRESSION(!source.contains(token));
}

} // namespace

TEST(tutorial_markdown_parser_tests, LegacySuite) {
  using linecode::domain::TutorialCodeBlock;
  using linecode::domain::TutorialAbsolutePathImage;
  using linecode::domain::TutorialEncodedImage;
  using linecode::domain::TutorialFileUriImage;
  using linecode::domain::TutorialHeading;
  using linecode::domain::TutorialHtmlBlock;
  using linecode::domain::TutorialImageBlock;
  using linecode::domain::TutorialList;
  using linecode::domain::TutorialTable;
  using linecode::infrastructure::TutorialMarkdownParser;

  constexpr std::string_view sample = R"md(# 标题

## 1. 设置详解：模型管理

普通 **粗体**、*斜体*、`代码` 和 [链接](https://example.com)。

- 第一项
  - 子项

| 名称 | 状态 |
| --- | --- |
| 模型 | 可用 |

```cpp
auto value = 23;
## 围栏里的标题不是章节
```
)md";

  const TutorialMarkdownParser parser;
  const auto parsed = parser.Parse(sample);
  EXPECT_EXPRESSION(parsed.sections.size() == 1);

  constexpr std::string_view image_markdown =
      "![one pixel](data:image/png;base64,"
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8A"
      "AQUBAScY42YAAAAASUVORK5CYII=)";
  const auto image_document = parser.Parse(image_markdown);
  EXPECT_EXPRESSION(image_document.blocks.size() == 1);
  EXPECT_EXPRESSION(std::holds_alternative<TutorialImageBlock>(
      image_document.blocks.front()));
  const auto &image =
      std::get<TutorialImageBlock>(image_document.blocks.front());
  EXPECT_EXPRESSION(image.alternative_text == "one pixel");
  EXPECT_EXPRESSION(std::holds_alternative<TutorialEncodedImage>(image.source));
  const auto& encoded = std::get<TutorialEncodedImage>(image.source);
  EXPECT_EXPRESSION(encoded.mime_type == "image/png");
  EXPECT_EXPRESSION(encoded.pixel_width == 1);
  EXPECT_EXPRESSION(encoded.pixel_height == 1);
  EXPECT_EXPRESSION(!encoded.encoded.empty());

  // The legacy image view accepted both absolute paths and file URIs. The
  // parser keeps them lazy so filesystem access remains presentation-owned.
  const auto path_document = parser.Parse("![local](/tmp/photo.png)");
  EXPECT_EXPRESSION(path_document.blocks.size() == 1);
  const auto& path_image =
      std::get<TutorialImageBlock>(path_document.blocks.front());
  EXPECT_EXPRESSION(std::holds_alternative<TutorialAbsolutePathImage>(path_image.source));
  EXPECT_EXPRESSION(std::get<TutorialAbsolutePathImage>(path_image.source).path ==
         "/tmp/photo.png");

  const auto uri_document =
      parser.Parse("![uri](file:///tmp/a%20photo.jpg)");
  EXPECT_EXPRESSION(uri_document.blocks.size() == 1);
  const auto& uri_image =
      std::get<TutorialImageBlock>(uri_document.blocks.front());
  EXPECT_EXPRESSION(std::holds_alternative<TutorialFileUriImage>(uri_image.source));
  EXPECT_EXPRESSION(std::get<TutorialFileUriImage>(uri_image.source).uri ==
         "file:///tmp/a%20photo.jpg");

  // MIME smuggling, unsupported media, malformed padding and oversized pixel
  // metadata are rejected without retaining the data URI in the document.
  constexpr std::array rejected{
      "![bad](data:image/jpeg;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC)",
      "![bad](data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==)",
      "![bad](data:image/png;base64,AAA=AAAA)",
      "![bad](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAQAEAAAABAQAAAAAA)",
  };
  for (const auto source : rejected) {
    const auto rejected_document = parser.Parse(source);
    EXPECT_EXPRESSION(rejected_document.blocks.size() == 1);
    EXPECT_EXPRESSION(!std::holds_alternative<TutorialImageBlock>(
        rejected_document.blocks.front()));
    EXPECT_EXPRESSION(!TutorialMarkdownParser::PlainText(
                std::get<linecode::domain::TutorialParagraph>(
                    rejected_document.blocks.front())
                    .content)
                .contains("data:image"));
  }
  EXPECT_EXPRESSION(parsed.sections.front().title == "1. 设置详解：模型管理");
  EXPECT_EXPRESSION(TutorialMarkdownParser::ShortSectionTitle(
             parsed.sections.front().title) == "模型管理");
  EXPECT_EXPRESSION(std::holds_alternative<TutorialHeading>(parsed.blocks.front()));

  const auto list = std::find_if(parsed.blocks.begin(), parsed.blocks.end(),
                                 [](const auto& block) {
                                   return std::holds_alternative<TutorialList>(block);
                                 });
  EXPECT_EXPRESSION(list != parsed.blocks.end());
  EXPECT_EXPRESSION(std::get<TutorialList>(*list).items.size() == 2);
  EXPECT_EXPRESSION(std::get<TutorialList>(*list).items.back().depth == 1);

  const auto table = std::find_if(parsed.blocks.begin(), parsed.blocks.end(),
                                  [](const auto& block) {
                                    return std::holds_alternative<TutorialTable>(block);
                                  });
  EXPECT_EXPRESSION(table != parsed.blocks.end());
  EXPECT_EXPRESSION(std::get<TutorialTable>(*table).header.size() == 2);
  EXPECT_EXPRESSION(std::get<TutorialTable>(*table).rows.size() == 1);

  const auto code = std::find_if(parsed.blocks.begin(), parsed.blocks.end(),
                                 [](const auto& block) {
                                   return std::holds_alternative<TutorialCodeBlock>(block);
                                 });
  EXPECT_EXPRESSION(code != parsed.blocks.end());
  EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(*code).language == "cpp");
  EXPECT_EXPRESSION(std::get<TutorialCodeBlock>(*code).code.contains("## 围栏里的标题"));
  EXPECT_EXPRESSION(parsed.sections.size() == 1);

  // CommonMark HtmlBlock nodes render as safe `html` source cards in the
  // legacy app. Comments and multi-line blocks must not leak into paragraphs.
  const auto html_document = parser.Parse(
      "before\n\n<!-- tutorial-v1 -->\n\n<div class=\"note\">\nbody\n</div>\n\nafter\n");
  EXPECT_EXPRESSION(html_document.blocks.size() == 4);
  EXPECT_EXPRESSION(std::holds_alternative<TutorialHtmlBlock>(html_document.blocks[1]));
  EXPECT_EXPRESSION(std::get<TutorialHtmlBlock>(html_document.blocks[1]).html ==
         "<!-- tutorial-v1 -->");
  EXPECT_EXPRESSION(std::holds_alternative<TutorialHtmlBlock>(html_document.blocks[2]));
  EXPECT_EXPRESSION(std::get<TutorialHtmlBlock>(html_document.blocks[2]).html ==
         "<div class=\"note\">\nbody\n</div>");

  const auto simple = ReadFile("resources/raw/tutorial_simple.md");
  const auto pro = ReadFile("resources/raw/tutorial_pro.md");
  AssertSanitized(simple);
  AssertSanitized(pro);

  const auto simple_document = parser.Parse(simple);
  const auto pro_document = parser.Parse(pro);
  EXPECT_EXPRESSION(simple_document.blocks.size() > 100);
  EXPECT_EXPRESSION(simple_document.sections.size() == 30);
  EXPECT_EXPRESSION(pro_document.blocks.size() > 60);
  EXPECT_EXPRESSION(pro_document.sections.size() == 13);
}
