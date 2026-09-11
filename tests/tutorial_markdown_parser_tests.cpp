#include <algorithm>
#include <array>
#include <cassert>
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
  assert(stream && "tutorial raw resource must exist");
  return {std::istreambuf_iterator<char>(stream),
          std::istreambuf_iterator<char>()};
}

void AssertSanitized(std::string_view source) {
  constexpr std::string_view forbidden[] = {
      "无障碍", "手机控制", "控制手机", "控制模式", "Phone Control",
      "Accessibility"};
  for (const auto token : forbidden)
    assert(!source.contains(token));
}

} // namespace

int main() {
  using linecode::domain::TutorialCodeBlock;
  using linecode::domain::TutorialHeading;
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
  assert(parsed.sections.size() == 1);

  constexpr std::string_view image_markdown =
      "![one pixel](data:image/png;base64,"
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8A"
      "AQUBAScY42YAAAAASUVORK5CYII=)";
  const auto image_document = parser.Parse(image_markdown);
  assert(image_document.blocks.size() == 1);
  assert(std::holds_alternative<TutorialImageBlock>(
      image_document.blocks.front()));
  const auto &image =
      std::get<TutorialImageBlock>(image_document.blocks.front());
  assert(image.alternative_text == "one pixel");
  assert(image.mime_type == "image/png");
  assert(image.pixel_width == 1);
  assert(image.pixel_height == 1);
  assert(!image.encoded.empty());

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
    assert(rejected_document.blocks.size() == 1);
    assert(!std::holds_alternative<TutorialImageBlock>(
        rejected_document.blocks.front()));
    assert(!TutorialMarkdownParser::PlainText(
                std::get<linecode::domain::TutorialParagraph>(
                    rejected_document.blocks.front())
                    .content)
                .contains("data:image"));
  }
  assert(parsed.sections.front().title == "1. 设置详解：模型管理");
  assert(TutorialMarkdownParser::ShortSectionTitle(
             parsed.sections.front().title) == "模型管理");
  assert(std::holds_alternative<TutorialHeading>(parsed.blocks.front()));

  const auto list = std::find_if(parsed.blocks.begin(), parsed.blocks.end(),
                                 [](const auto& block) {
                                   return std::holds_alternative<TutorialList>(block);
                                 });
  assert(list != parsed.blocks.end());
  assert(std::get<TutorialList>(*list).items.size() == 2);
  assert(std::get<TutorialList>(*list).items.back().depth == 1);

  const auto table = std::find_if(parsed.blocks.begin(), parsed.blocks.end(),
                                  [](const auto& block) {
                                    return std::holds_alternative<TutorialTable>(block);
                                  });
  assert(table != parsed.blocks.end());
  assert(std::get<TutorialTable>(*table).header.size() == 2);
  assert(std::get<TutorialTable>(*table).rows.size() == 1);

  const auto code = std::find_if(parsed.blocks.begin(), parsed.blocks.end(),
                                 [](const auto& block) {
                                   return std::holds_alternative<TutorialCodeBlock>(block);
                                 });
  assert(code != parsed.blocks.end());
  assert(std::get<TutorialCodeBlock>(*code).language == "cpp");
  assert(std::get<TutorialCodeBlock>(*code).code.contains("## 围栏里的标题"));
  assert(parsed.sections.size() == 1);

  const auto simple = ReadFile("resources/raw/tutorial_simple.md");
  const auto pro = ReadFile("resources/raw/tutorial_pro.md");
  AssertSanitized(simple);
  AssertSanitized(pro);

  const auto simple_document = parser.Parse(simple);
  const auto pro_document = parser.Parse(pro);
  assert(simple_document.blocks.size() > 100);
  assert(simple_document.sections.size() == 30);
  assert(pro_document.blocks.size() > 60);
  assert(pro_document.sections.size() == 13);
}
