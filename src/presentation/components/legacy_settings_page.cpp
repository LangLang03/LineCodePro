#include "presentation/components/legacy_settings_page.h"

#include <utility>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

} // namespace

View LegacySettingsPageHeader(StringResource title,
                              std::function<void()> on_back) {
  using namespace huxerui;
  return LegacyScreenHeaderLayout{
      Stack{Image(app::images::chevron_left)
                .Tint(colors::text)
                .With(Frame{.width = 22.0F, .height = 22.0F})}
          .OnClick(std::move(on_back))
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(title).Style(Label(17.0F, FontWeight::Bold))}.With(
          Grow(),
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View LegacySettingsSection(StringResource title, std::vector<View> rows) {
  using namespace huxerui;
  return Column{
      Text(title)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Frame{.height = 47.625F},
                Padding(EdgeInsets{.top = 20.0F,
                                   .right = 16.0F,
                                   .bottom = 12.0F,
                                   .left = 16.0F})),
      LegacySettingsCardFrame{
          Column(std::move(rows))
              .With(CrossAlign(CrossAxisAlignment::Stretch),
                    Background(colors::elevated), CornerRadius(12.0F)),
      },
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View LegacySettingsPage(StringResource title, std::function<void()> on_back,
                        std::vector<View> content) {
  using namespace huxerui;
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}));
  return Column{
      LegacySettingsPageHeader(title, std::move(on_back)),
      Divider(),
      ScrollView(Column(std::move(content))
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
