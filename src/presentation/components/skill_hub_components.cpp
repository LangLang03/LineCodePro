#include "presentation/components/skill_hub_components.h"

#include <utility>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {

using namespace huxerui;

TextStyle SkillHubLabel(const float size, const FontWeight weight,
                        Color color) {
  if (color == Color{})
    color = colors::text;
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View SkillHubGlyph(ImageVariant icon, const float size, const Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

View SkillHubIconSlot(ImageVariant icon, const float icon_size,
                      const float slot_size, const Color tint) {
  return Stack{SkillHubGlyph(std::move(icon), icon_size, tint)}.With(
      Frame{.width = slot_size, .height = slot_size},
      Align(HorizontalAlignment::Center, VerticalAlignment::Center));
}

View SkillHubHeader(StringVariant title, std::function<void()> on_back) {
  return LegacyScreenHeaderLayout{
      Stack{SkillHubGlyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick(std::move(on_back))
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{
          Text(std::move(title)).Style(SkillHubLabel(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View SkillHubScrollablePage(StringVariant title, std::function<void()> on_back,
                            std::vector<View> content) {
  content.push_back(Stack{}.With(Frame{.height = 100.0F}));
  return Column{
      SkillHubHeader(std::move(title), std::move(on_back)),
      Divider(),
      ScrollView(Column(std::move(content))
                     .With(Padding(16.0F),
                           CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

View SkillHubTag(StringVariant text, const Color color,
                 const Color background) {
  return Text(std::move(text))
      .Style(SkillHubLabel(11.0F, FontWeight::Medium, color))
      .With(Padding(EdgeInsets::Symmetric(8.0F, 3.0F)), Background(background),
            CornerRadius(8.0F));
}

View SkillHubMargin(View child, const EdgeInsets insets) {
  return Column{std::move(child)}.With(Padding(insets),
                                       CrossAlign(CrossAxisAlignment::Stretch));
}

View SkillHubTopMargin(View child, const float amount) {
  return SkillHubMargin(std::move(child), EdgeInsets{.top = amount});
}

View SkillHubSection(StringVariant title, const ImageResource icon,
                     std::vector<View> content) {
  std::vector<View> children;
  children.reserve(content.size() + 1);
  children.push_back(Row{
      SkillHubIconSlot(icon, 17.0F, 28.0F, colors::accent),
      Text(std::move(title)).Style(SkillHubLabel(16.0F, FontWeight::Medium)),
  }
                         .With(Frame{.min_height = 28.0F}, Spacing(8.0F),
                               CrossAlign(CrossAxisAlignment::Center)));
  for (auto &child : content)
    children.push_back(SkillHubTopMargin(std::move(child), 8.0F));
  return Column(std::move(children))
      .With(Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::elevated),
            Border{.color = colors::border, .width = 1.0F}, CornerRadius(12.0F),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View SkillHubDialogPanel(std::vector<View> content) {
  return Column(std::move(content))
      .With(Frame{.max_width = 560.0F}, Padding(16.0F),
            Background(colors::elevated),
            Border{.color = colors::border_light, .width = 1.0F},
            CornerRadius(16.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

View SkillHubDialogButton(StringVariant text, const bool primary,
                          std::function<void()> on_click, const bool enabled) {
  return Stack{Text(std::move(text))
                   .Style(SkillHubLabel(13.0F, FontWeight::Medium,
                                        primary ? Color(colors::text_on_color)
                                                : Color(colors::text)))
                   .Align(TextAlign::Center)}
      .OnClick(std::move(on_click))
      .With(Frame{.min_height = 44.0F}, Padding(12.0F), Grow(),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(primary ? Color(colors::accent)
                               : Color(colors::surface_light)),
            Border{.color = primary ? Color(colors::accent)
                                    : Color(colors::border_light),
                   .width = 1.0F},
            CornerRadius(10.0F), Enabled(enabled), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

} // namespace linecode::presentation
