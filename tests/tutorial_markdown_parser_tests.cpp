#include <algorithm>
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
