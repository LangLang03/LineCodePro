#include "presentation/components/chat_reasoning_view.h"

#include <algorithm>
#include <ranges>
#include <utility>

#include <app_resources.h>

#include "domain/inline_emphasis.h"
#include "presentation/line_theme.h"

namespace linecode::presentation::chat_timeline {
namespace {

using namespace huxerui;

TextStyle ChatTextStyle(float size, FontWeight weight = FontWeight::Regular,
                        Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

class LegacyMatchParentWidth final : public Layout<LegacyMatchParentWidth> {
public:
  using Layout::Layout;

  static LayoutResult Measure(LayoutContext &context, ViewNode &node,
                              Constraints constraints) {
    LayoutResult result;
    if (node.ChildCount() == 0)
      return result.SetSize(constraints.Constrain({0.0F, 0.0F}));

    auto child_constraints = constraints.Loose();
    if (constraints.HasBoundedWidth())
      child_constraints = child_constraints.TightWidth(constraints.max_width);
    auto &child = node.ChildAt(0);
    const Size child_size = context.Measure(child, child_constraints);
    const float width = constraints.HasBoundedWidth() ? constraints.max_width
                                                      : child_size.width;
    return result.Place(child, {}).SetSize(
        constraints.Constrain({width, child_size.height}));
  }
};

AttributedText ReasoningEmphasisText(std::string_view source) {
  const auto parsed = domain::ParseInlineEmphasis(source);
  if (parsed.spans.empty())
    return AttributedText(parsed.text);
  std::vector<TextSpan> spans;
  spans.reserve(parsed.spans.size() * 2U + 1U);
  std::size_t cursor = 0;
  for (const auto &span : parsed.spans) {
    if (span.start > cursor)
      spans.emplace_back(parsed.text.substr(cursor, span.start - cursor));
    TextSpanStyle style;
    if (span.style != domain::InlineEmphasisStyle::Italic)
      style.font_weight = FontWeight::Bold;
    if (span.style != domain::InlineEmphasisStyle::Bold)
      style.font_slant = FontSlant::Italic;
    TextSpan emphasised(parsed.text.substr(span.start, span.end - span.start));
    spans.push_back(std::move(emphasised).Style(std::move(style)));
    cursor = span.end;
  }
  if (cursor < parsed.text.size())
    spans.emplace_back(parsed.text.substr(cursor));
  return AttributedText(std::span<const TextSpan>(spans));
}

} // namespace

bool ToggleState(std::span<const std::string> toggled, std::string_view key,
                 bool default_value) {
  return std::ranges::contains(toggled, key) ? !default_value : default_value;
}

void ToggleKey(State<std::vector<std::string>> toggled, std::string key) {
  toggled.Update([key = std::move(key)](auto &keys) {
    const auto found = std::ranges::find(keys, key);
    if (found == keys.end())
      keys.push_back(key);
    else
      keys.erase(found);
  });
}

View ThinkingDisclosureBlock(std::string_view text, StringVariant label,
                             std::string key, bool auto_expand, bool scrollable,
                             State<std::vector<std::string>> toggled) {
  const bool expanded = ToggleState(toggled.Get(), key, auto_expand);
  View header = Row{
      Text(std::move(label))
          .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::secondary)),
      Stack{Image(expanded ? app::images::chevron_down
                           : app::images::chevron_right)
                .Tint(colors::tertiary)
                .With(Frame{.width = 16.0F, .height = 16.0F})}
          .With(Frame{.width = 28.0F, .height = 32.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
  };
  header = std::move(header)
               .OnClick([toggled, key] { ToggleKey(toggled, key); })
               .With(Frame{.min_height = 48.0F},
                     Padding(EdgeInsets::Symmetric(0.0F, 8.0F)),
                     CrossAlign(CrossAxisAlignment::Center),
                     PointerCursor(PointerCursorKind::Hand));
  if (!expanded)
    return header;

  View reasoning_text =
      Text(ReasoningEmphasisText(text))
          .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::tertiary));
  View body;
  if (scrollable) {
    // SelectionArea owns range-selection drags. In a capped nested scroll
    // viewport that steals the gesture from this ScrollView, so scrolling mode
    // deliberately owns the plain text view; the uncapped mode stays
    // selectable.
    body = ScrollView(std::move(reasoning_text))
               .ScrollAxis(Axis::Vertical)
               .With(Frame{.max_height = 180.0F}, ScrollBar());
  } else {
    body = SelectionArea(std::move(reasoning_text));
  }
  return Column{
      std::move(header),
      LegacyMatchParentWidth{Column{std::move(body)}.With(
          Padding(EdgeInsets{.top = 8.0F}),
          CrossAlign(CrossAxisAlignment::Stretch))},
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View ReasoningTimelineBlock(const domain::AssistantReasoningEvent &reasoning,
                            std::string key, bool live, bool auto_expand,
                            bool scrollable,
                            State<std::vector<std::string>> toggled) {
  return ThinkingDisclosureBlock(
      reasoning.text,
      reasoning.kind == domain::ReasoningKind::summary
          ? StringVariant{app::strings::chat_reasoning_summary}
      : live ? StringVariant{app::strings::chat_reasoning_thinking}
             : StringVariant{app::strings::thinking_done_label},
      std::move(key), auto_expand, scrollable, toggled);
}

} // namespace linecode::presentation::chat_timeline
