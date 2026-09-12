#include "presentation/components/tutorial_markdown.h"

#include <algorithm>
#include <cstddef>
#include <functional>
#include <stdexcept>
#include <string>
#include <utility>
#include <variant>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"
#include "presentation/markdown_link_policy.h"
#include "presentation/markdown_linkify.h"

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

/// Appends the runs of one inline part, turning bare web URLs into link-styled
/// spans the way `MarkdownLinks.apply()` did with
/// `Linkify.addLinks(builder, Linkify.WEB_URLS)` (`MarkdownLinks.java:17-18`).
///
/// Parts that already carry an explicit `[text](url)` destination keep their
/// own link and are never split again, and inline code is left alone because a
/// code sample must stay readable.
void AppendRuns(std::vector<TextSpan>& spans, const domain::TutorialInline& part,
                float scale) {
  const auto part_style = [&part, scale] {
    TextSpanStyle style;
    if (part.strong)
      style.font_weight = FontWeight::Bold;
    if (part.emphasis)
      style.font_slant = FontSlant::Italic;
    if (part.code) {
      style.font = Font::Monospace(16.0F * scale);
      style.background = colors::surface_light;
    }
    return style;
  };
  const auto has_part_style =
      part.strong || part.emphasis || part.code;
  for (const auto& run : LinkifyWebUrls(part.text)) {
    if (run.text.empty())
      continue;
    TextSpan span(run.text);
    TextSpanStyle style = part_style();
    if (run.url) {
      style.foreground = colors::accent;
      style.decoration = TextDecoration::Underline;
    }
    if (has_part_style || run.url)
      span = std::move(span).Style(std::move(style));
    if (run.url) {
      // Same navigability gate as explicit Markdown links: a recognized URL
      // stays link-styled, but only a navigable target becomes clickable.
      if (auto target = ParseNavigableMarkdownLink(*run.url))
        span = std::move(span).Link(std::move(*target));
    }
    spans.push_back(std::move(span));
  }
}

AttributedText RichText(const domain::TutorialInlineLine& line, float scale) {
  std::vector<TextSpan> spans;
  spans.reserve(line.size());
  for (const auto& part : line) {
    if (!LinkifiesBareUrls(part.code, part.link.has_value())) {
      TextSpan span(part.text);
      TextSpanStyle style;
      if (part.strong)
        style.font_weight = FontWeight::Bold;
      if (part.emphasis)
        style.font_slant = FontSlant::Italic;
      if (part.code) {
        style.font = Font::Monospace(16.0F * scale);
        style.background = colors::surface_light;
      }
      if (part.link) {
        style.foreground = colors::accent;
        style.decoration = TextDecoration::Underline;
      }
      if (part.strong || part.emphasis || part.code || part.link)
        span = std::move(span).Style(std::move(style));
      if (part.link) {
        if (auto target = ParseNavigableMarkdownLink(*part.link))
          span = std::move(span).Link(std::move(*target));
      }
      spans.push_back(std::move(span));
      continue;
    }
    AppendRuns(spans, part, scale);
  }
  if (spans.empty())
    return AttributedText();
  return AttributedText(std::span<const TextSpan>(spans));
}

View RichLabel(const domain::TutorialInlineLine& content, float size,
               float scale,
               const TutorialMarkdownLinkHandler& on_link,
               FontWeight weight = FontWeight::Regular,
               Color color = colors::text) {
  return Text(RichText(content, scale))
      .Style(Label(size * scale, weight, color))
      .On<TextEvents::LinkActivated>(
          [on_link](const TextLinkActivation& activation) {
            if (on_link)
              on_link(activation.target);
          });
}

View HeadingBlock(const domain::TutorialHeading& heading, std::size_t depth,
                  float scale,
                  const TutorialMarkdownLinkHandler& on_link) {
  const float size = heading.level <= 1 ? 28.0F
                     : heading.level == 2 ? 24.0F
                     : heading.level == 3 ? 20.0F
                                          : 16.0F;
  // `MarkdownRenderer.java:95`: nested headings use the 4/10 container margins.
  return RichLabel(heading.content, size, scale, on_link, FontWeight::Medium)
      .With(Padding(EdgeInsets{.top = depth == 0 ? 8.0F : 4.0F,
                               .right = 0.0F,
                               .bottom = depth == 0 ? 20.0F : 10.0F,
                               .left = 0.0F}));
}

View ParagraphBlock(const domain::TutorialParagraph& paragraph,
                    std::size_t depth, float scale,
                    const TutorialMarkdownLinkHandler& on_link) {
  // `MarkdownRenderer.java:110`: nested paragraphs drop the outer spacing.
  return RichLabel(paragraph.content, 16.0F, scale, on_link)
      .With(Padding(EdgeInsets{.top = depth == 0 ? 2.0F : 0.0F,
                               .right = 0.0F,
                               .bottom = depth == 0 ? 18.0F : 8.0F,
                               .left = 0.0F}));
}

View BlockView(const domain::TutorialBlock& block, std::size_t depth,
               bool wraps, float scale,
               const TutorialMarkdownLinkHandler& on_link,
               const TutorialMarkdownCopyHandler& on_copy);

View BlockSequenceView(const domain::TutorialBlockSequence* sequence,
                       std::size_t depth, bool wraps, float scale,
                       const TutorialMarkdownLinkHandler& on_link,
                       const TutorialMarkdownCopyHandler& on_copy);

View QuoteBlock(const domain::TutorialQuote& quote, std::size_t depth,
                bool wraps, float scale,
                const TutorialMarkdownLinkHandler& on_link,
                const TutorialMarkdownCopyHandler& on_copy) {
  // `MarkdownRenderer.java:124-128` renders every child block of the
  // `BlockQuote` inside the content column next to the rail, so a fenced code
  // block inside `>` keeps its own block layout.
  return Row {
    Stack {}.With(Frame{.width = 3.0F}, Background(colors::border_light),
                  CornerRadius(2.0F)),
    BlockSequenceView(quote.blocks.get(), depth + 1, wraps, scale, on_link,
                      on_copy)
        .With(Grow()),
  }.With(Spacing(12.0F), Padding(EdgeInsets{.top = 3.0F,
                                            .right = 0.0F,
                                            .bottom = 8.0F,
                                            .left = 0.0F}),
         CrossAlign(CrossAxisAlignment::Stretch));
}

View ListItem(const domain::TutorialListItem& item, bool wraps, float scale,
              const TutorialMarkdownLinkHandler& on_link,
              const TutorialMarkdownCopyHandler& on_copy) {
  // `MarkdownRenderer.java:154-155` renders the children of the `ListItem`
  // inside the item's content column, indented under the marker.
  std::vector<View> content;
  content.push_back(RichLabel(item.content, 16.0F, scale, on_link));
  if (item.blocks) {
    for (const auto& block : item.blocks->blocks)
      content.push_back(BlockView(block, item.depth + 1, wraps, scale, on_link,
                                  on_copy));
  }
  return Row {
    Text(item.marker)
        .Style(Label(16.0F * scale, FontWeight::Regular, colors::secondary))
        .Align(TextAlign::Trailing)
        .With(Frame{.width = 22.0F + static_cast<float>(item.depth) * 4.0F}),
    Column(std::move(content))
        .With(Grow(), CrossAlign(CrossAxisAlignment::Stretch)),
  }.With(Spacing(8.0F),
         Padding(EdgeInsets{.top = 0.0F,
                            .right = 0.0F,
                            .bottom = 3.0F,
                            .left = static_cast<float>(item.depth) * 8.0F}),
         CrossAlign(CrossAxisAlignment::Start));
}

View ListBlock(const domain::TutorialList& list, bool wraps, float scale,
               const TutorialMarkdownLinkHandler& on_link,
               const TutorialMarkdownCopyHandler& on_copy) {
  std::vector<View> items;
  items.reserve(list.items.size());
  for (const auto& item : list.items)
    items.push_back(ListItem(item, wraps, scale, on_link, on_copy));
  return Column(std::move(items))
      .With(Padding(EdgeInsets{.top = 1.0F,
                               .right = 0.0F,
                               .bottom = 7.0F,
                               .left = 0.0F}),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View CodeBlock(const domain::TutorialCodeBlock& block, bool wraps,
               float scale,
               const TutorialMarkdownCopyHandler& on_copy) {
  View code = Text(block.code).Style(
      TextStyle{Font::Monospace(13.0F * scale), colors::text});
  View body = code;
  if (!wraps) {
    body = ScrollView(std::move(code).With(Frame{.min_width = 760.0F}))
               .ScrollAxis(Axis::Horizontal);
  }
  View copy_icon = Stack {
    Image(app::images::copy)
        .Tint(colors::tertiary)
        .With(Frame{.width = 18.0F, .height = 18.0F}),
  }.With(Frame{.width = 48.0F, .height = 48.0F},
         Align(HorizontalAlignment::Center, VerticalAlignment::Center));
  if (on_copy) {
    // The legacy button copies that block's own source, not the whole message.
    copy_icon = std::move(copy_icon)
                    .OnClick([on_copy, source = block.code] {
                      std::invoke(on_copy, source);
                    })
                    .With(Semantics{.label = app::strings::markdown_code_copy_desc},
                          Focusable(), PointerCursor(PointerCursorKind::Hand));
  }
  View card = Column {
    Row {
      Text(block.language)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      Spacer(),
      std::move(copy_icon),
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

View ImageBlock(const domain::TutorialImageBlock &block, float scale) {
  try {
    const auto asset = ImageAsset::CopyEncoded(block.encoded, scale);
    return Stack{Image(asset)
                     .Fit(ImageFit::ScaleDown)
                     .Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center)
                     .With(Frame{.max_width = 684.0F,
                                 .max_height = 420.0F},
                           CornerRadius(12.0F), ClipChildren())}
        .With(Align(HorizontalAlignment::Start,
                    VerticalAlignment::Center),
              Padding(EdgeInsets{.top = 4.0F,
                                 .right = 0.0F,
                                 .bottom = 8.0F,
                                 .left = 0.0F}),
              Semantics{.label = block.alternative_text});
  } catch (const std::invalid_argument &) {
    return Text(block.alternative_text)
        .Style(Label(13.0F * scale, FontWeight::Regular, colors::danger));
  }
}

View TableCell(const domain::TutorialInlineLine& content, bool header,
               bool alternate, float scale,
               const TutorialMarkdownLinkHandler& on_link) {
  return RichLabel(content, 13.0F, scale, on_link,
                   header ? FontWeight::Bold : FontWeight::Regular,
                   header ? colors::text : colors::secondary)
      .With(Frame{.width = 156.0F, .min_height = 38.0F}, Padding(8.0F),
            Background(header ? colors::surface_light
                              : alternate ? colors::code : colors::surface),
            Border(colors::border_light, 0.5F));
}

View TableRow(const std::vector<domain::TutorialInlineLine>& cells,
              bool header, bool alternate, float scale,
              const TutorialMarkdownLinkHandler& on_link) {
  std::vector<View> views;
  views.reserve(cells.size());
  for (const auto& cell : cells)
    views.push_back(TableCell(cell, header, alternate, scale, on_link));
  return Row(std::move(views));
}

View TableBlock(const domain::TutorialTable& table, float scale,
                const TutorialMarkdownLinkHandler& on_link) {
  std::vector<View> rows;
  rows.reserve(table.rows.size() + 1);
  rows.push_back(TableRow(table.header, true, false, scale, on_link));
  for (std::size_t index = 0; index < table.rows.size(); ++index)
    rows.push_back(
        TableRow(table.rows[index], false, index % 2 == 1, scale, on_link));
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

View BlockSequenceView(const domain::TutorialBlockSequence* sequence,
                       std::size_t depth, bool wraps, float scale,
                       const TutorialMarkdownLinkHandler& on_link,
                       const TutorialMarkdownCopyHandler& on_copy) {
  std::vector<View> blocks;
  if (sequence) {
    blocks.reserve(sequence->blocks.size());
    for (const auto& block : sequence->blocks)
      blocks.push_back(BlockView(block, depth, wraps, scale, on_link, on_copy));
  }
  return Column(std::move(blocks))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View BlockView(const domain::TutorialBlock& block, std::size_t depth,
               bool wraps, float scale,
               const TutorialMarkdownLinkHandler& on_link,
               const TutorialMarkdownCopyHandler& on_copy) {
  return std::visit(
      Overloaded{
          [depth, scale, &on_link](const domain::TutorialHeading& value) {
            return HeadingBlock(value, depth, scale, on_link);
          },
          [depth, scale, &on_link](const domain::TutorialParagraph& value) {
            return ParagraphBlock(value, depth, scale, on_link);
          },
          [depth, wraps, scale, &on_link,
           &on_copy](const domain::TutorialQuote& value) {
            return QuoteBlock(value, depth, wraps, scale, on_link, on_copy);
          },
          [wraps, scale, &on_link, &on_copy](const domain::TutorialList& value) {
            return ListBlock(value, wraps, scale, on_link, on_copy);
          },
          [wraps, scale, &on_copy](const domain::TutorialCodeBlock& value) {
            return CodeBlock(value, wraps, scale, on_copy);
          },
          [scale](const domain::TutorialImageBlock& value) {
            return ImageBlock(value, scale);
          },
          [scale, &on_link](const domain::TutorialTable& value) {
            return TableBlock(value, scale, on_link);
          },
          [](const domain::TutorialThematicBreak&) {
            return ThematicBreakBlock();
          },
      },
      block);
}

} // namespace

View TutorialMarkdownBlockView(const domain::TutorialBlock& block,
                               bool code_wrap_enabled, float text_scale,
                               TutorialMarkdownLinkHandler on_link,
                               TutorialMarkdownCopyHandler on_copy) {
  return BlockView(block, 0U, code_wrap_enabled, text_scale, on_link, on_copy);
}

View TutorialMarkdownDocumentView(const domain::TutorialDocument& document,
                                  bool code_wrap_enabled, float text_scale,
                                  TutorialMarkdownLinkHandler on_link,
                                  TutorialMarkdownCopyHandler on_copy) {
  std::vector<View> blocks;
  blocks.reserve(document.blocks.size());
  for (const auto& block : document.blocks)
    blocks.push_back(
        BlockView(block, 0U, code_wrap_enabled, text_scale, on_link, on_copy));
  return SelectionArea(Column(std::move(blocks)).With(
      CrossAlign(CrossAxisAlignment::Stretch)));
}

} // namespace linecode::presentation
