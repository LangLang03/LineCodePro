#include "presentation/screens/shell_command_screen.h"

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View
ShellCommandScreen(const domain::ShellCommandRoute &route) {
  using namespace huxerui;
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto text = route.command.empty()
                        ? StringVariant{app::strings::shell_command_empty}
                        : StringVariant{route.command};
  auto header = LegacyScreenHeaderLayout{
      Stack{Image(app::images::chevron_left)
                .Tint(colors::text)
                .With(Frame{.width = 20.0F, .height = 20.0F})}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::shell_command_title)
                .Style(TextStyle{Font::System(17.0F).WithWeight(
                                     FontWeight::Bold),
                                 colors::text})}
          .With(Grow(), Align(HorizontalAlignment::Center,
                              VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  };
  auto command = SelectionArea(
      Text(std::move(text))
          .Style(TextStyle{Font::Monospace(13.0F), colors::text})
          .With(Padding(16.0F), Background(colors::code),
                CornerRadius(12.0F), Border(colors::border_light, 1.0F)));
  return Column{
      std::move(header).With(
          Frame{.min_height = 60.0F},
          Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
          Background(colors::background)),
      Divider(),
      ScrollView(Column{std::move(command)}.With(
                     Padding(EdgeInsets{.top = 8.0F,
                                        .right = 28.0F,
                                        .bottom = 48.0F,
                                        .left = 28.0F}),
                     CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
