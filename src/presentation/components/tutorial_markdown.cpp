#include "presentation/components/tutorial_markdown.h"

#include <algorithm>
#include <cstddef>
#include <functional>
#include <string>
#include <utility>
#include <variant>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

template <class... Visitors> struct Overloaded final : Visitors... {
  using Visitors::operator()...;
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

AttributedText RichText(const domain::TutorialInlineLine& line) {
  std::vector<TextSpan> spans;
  spans.reserve(line.size());
  for (const auto& part : line) {
    TextSpan span(part.text);
    if (part.strong || part.emphasis || part.code || part.link) {
      TextSpanStyle style;
      if (part.strong)
        style.font_weight = FontWeight::Bold;
      if (part.emphasis)
        style.font_slant = FontSlant::Italic;
      if (part.code) {
        style.font = Font::Monospace(16.0F);
        style.background = colors::surface_light;
      }
      if (part.link) {
        style.foreground = colors::accent;
        style.decoration = TextDecoration::Underline;
      }
      span = std::move(span).Style(std::move(style));
    }
    spans.push_back(std::move(span));
  }
  return AttributedText(std::span<const TextSpan>(spans));
}

View RichLabel(const domain::TutorialInlineLine& content, float size,
               FontWeight weight = FontWeight::Regular,
               Color color = colors::text) {
  return Text(RichText(content)).Style(Label(size, weight, color));
}

View HeadingBlock(const domain::TutorialHeading& heading) {
  const float size = heading.level <= 1 ? 28.0F
                     : heading.level == 2 ? 24.0F
                     : heading.level == 3 ? 20.0F
                                          : 16.0F;
  return RichLabel(heading.content, size, FontWeight::Medium)
      .With(Padding(EdgeInsets{.top = 8.0F,
                               .right = 0.0F,
                               .bottom = 20.0F,
                               .left = 0.0F}));
}

View ParagraphBlock(const domain::TutorialParagraph& paragraph) {
  return RichLabel(paragraph.content, 16.0F)
      .With(Padding(EdgeInsets{.top = 2.0F,
                               .right = 0.0F,
                               .bottom = 18.0F,
                               .left = 0.0F}));
}

View QuoteBlock(const domain::TutorialQuote& quote) {
  return Row {
    Stack {}.With(Frame{.width = 3.0F}, Background(colors::border_light),
                  CornerRadius(2.0F)),
    RichLabel(quote.content, 16.0F).With(Grow()),
  }.With(Spacing(12.0F), Padding(EdgeInsets{.top = 3.0F,
                                            .right = 0.0F,
                                            .bottom = 8.0F,
                                            .left = 0.0F}),
         CrossAlign(CrossAxisAlignment::Stretch));
}

View ListItem(const domain::TutorialListItem& item) {
  return Row {
    Text(item.marker)
        .Style(Label(16.0F, FontWeight::Regular, colors::secondary))
        .Align(TextAlign::Trailing)
        .With(Frame{.width = 22.0F + static_cast<float>(item.depth) * 4.0F}),
    RichLabel(item.content, 16.0F).With(Grow()),
  }.With(Spacing(8.0F),
         Padding(EdgeInsets{.top = 0.0F,
                            .right = 0.0F,
                            .bottom = 3.0F,
                            .left = static_cast<float>(item.depth) * 8.0F}),
         CrossAlign(CrossAxisAlignment::Start));
}

View ListBlock(const domain::TutorialList& list) {
  std::vector<View> items;
  items.reserve(list.items.size());
  for (const auto& item : list.items)
    items.push_back(ListItem(item));
  return Column(std::move(items))
      .With(Padding(EdgeInsets{.top = 1.0F,
                               .right = 0.0F,
                               .bottom = 7.0F,
                               .left = 0.0F}),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View CodeBlock(const domain::TutorialCodeBlock& block, bool wraps) {
  View code = Text(block.code).Style(
      TextStyle{Font::Monospace(13.0F), colors::text});
  View body = code;
  if (!wraps) {
    body = ScrollView(std::move(code).With(Frame{.min_width = 760.0F}))
               .ScrollAxis(Axis::Horizontal);
  }
  View card = Column {
    Row {
      Text(block.language)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      Spacer(),
      Stack {
        Image(app::images::copy)
            .Tint(colors::tertiary)
            .With(Frame{.width = 18.0F, .height = 18.0F}),
      }.With(Frame{.width = 48.0F, .height = 48.0F},
             Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
    }.With(Frame{.min_height = 48.0F},
           CrossAlign(CrossAxisAlignment::Center)),
    body,
  }.With(Spacing(8.0F), Padding(EdgeInsets{.top = 8.0F,
                                           .right = 16.0F,
                                           .bottom = 16.0F,
                                           .left = 16.0F}),
         CrossAlign(CrossAxisAlignment::Stretch), Background(colors::code),
         CornerRadius(12.0F));
  return Stack {card}.With(Padding(EdgeInsets{.top = 4.0F,
                                              .right = 0.0F,
                                              .bottom = 8.0F,
                                              .left = 0.0F}));
}

View TableCell(const domain::TutorialInlineLine& content, bool header,
               bool alternate) {
  return RichLabel(content, 13.0F,
                   header ? FontWeight::Bold : FontWeight::Regular,
                   header ? colors::text : colors::secondary)
      .With(Frame{.width = 156.0F, .min_height = 38.0F}, Padding(8.0F),
            Background(header ? colors::surface_light
                              : alternate ? colors::code : colors::surface),
            Border(colors::border_light, 0.5F));
}

View TableRow(const std::vector<domain::TutorialInlineLine>& cells,
              bool header, bool alternate) {
  std::vector<View> views;
  views.reserve(cells.size());
  for (const auto& cell : cells)
    views.push_back(TableCell(cell, header, alternate));
  return Row(std::move(views));
}

View TableBlock(const domain::TutorialTable& table) {
  std::vector<View> rows;
  rows.reserve(table.rows.size() + 1);
  rows.push_back(TableRow(table.header, true, false));
  for (std::size_t index = 0; index < table.rows.size(); ++index)
    rows.push_back(TableRow(table.rows[index], false, index % 2 == 1));
  return ScrollView(Column(std::move(rows)))
      .ScrollAxis(Axis::Horizontal)
      .With(Padding(EdgeInsets{.top = 4.0F,
                               .right = 0.0F,
                               .bottom = 8.0F,
                               .left = 0.0F}),
            CornerRadius(12.0F));
}

View ThematicBreakBlock() {
  return Stack {
    Stack {}.With(Frame{.height = 1.0F},
                  Background(colors::border_light)),
  }.With(Padding(EdgeInsets::Symmetric(0.0F, 8.0F)),
         Align(HorizontalAlignment::Stretch, VerticalAlignment::Center));
}

} // namespace

View TutorialMarkdownBlockView(const domain::TutorialBlock& block,
                               bool code_wrap_enabled) {
  return std::visit(
      Overloaded{
          [](const domain::TutorialHeading& value) {
            return HeadingBlock(value);
          },
          [](const domain::TutorialParagraph& value) {
            return ParagraphBlock(value);
          },
          [](const domain::TutorialQuote& value) { return QuoteBlock(value); },
          [](const domain::TutorialList& value) { return ListBlock(value); },
          [code_wrap_enabled](const domain::TutorialCodeBlock& value) {
            return CodeBlock(value, code_wrap_enabled);
          },
          [](const domain::TutorialTable& value) { return TableBlock(value); },
          [](const domain::TutorialThematicBreak&) {
            return ThematicBreakBlock();
          },
      },
      block);
}

View TutorialMarkdownDocumentView(const domain::TutorialDocument& document,
                                  bool code_wrap_enabled) {
  std::vector<View> blocks;
  blocks.reserve(document.blocks.size());
  for (const auto& block : document.blocks)
    blocks.push_back(TutorialMarkdownBlockView(block, code_wrap_enabled));
  return SelectionArea(Column(std::move(blocks)).With(
      CrossAlign(CrossAxisAlignment::Stretch)));
}

} // namespace linecode::presentation
