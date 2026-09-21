#include "presentation/screens/licenses_screen.h"

#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{
          Image(app::images::chevron_left)
              .Tint(colors::text)
              .With(Frame{.width = 22.0F, .height = 22.0F}),
      }
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_licenses_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View LicenseCard(StringResource name, StringResource metadata,
                 StringResource description) {
  return Column{
      Text(name).Style(Label(16.0F)),
      Text(metadata).Style(
          Label(13.0F, FontWeight::Regular, colors::secondary)),
      Text(description)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
  }
      .With(Padding(12.0F), CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(12.0F));
}

} // namespace

[[huxerui::composable]] View LicensesScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> content;
  content.reserve(6);
  content.push_back(LicenseCard(app::strings::screen_licenses_huxerui,
                                app::strings::screen_licenses_huxerui_meta,
                                app::strings::screen_licenses_huxerui_desc));
  content.push_back(LicenseCard(app::strings::screen_licenses_sqlite,
                                app::strings::screen_licenses_sqlite_meta,
                                app::strings::screen_licenses_sqlite_desc));
  content.push_back(LicenseCard(app::strings::screen_licenses_webview,
                                app::strings::screen_licenses_webview_meta,
                                app::strings::screen_licenses_webview_desc));
  content.push_back(LicenseCard(app::strings::screen_licenses_lucide,
                                app::strings::screen_licenses_lucide_meta,
                                app::strings::screen_licenses_lucide_desc));
  content.push_back(LicenseCard(app::strings::screen_licenses_libssh2,
                                app::strings::screen_licenses_libssh2_meta,
                                app::strings::screen_licenses_libssh2_desc));
  content.push_back(LicenseCard(app::strings::screen_licenses_mbedtls,
                                app::strings::screen_licenses_mbedtls_meta,
                                app::strings::screen_licenses_mbedtls_desc));

  return Column{
      Header(navigation),
      LegacyScreenHeaderDivider(),
      ScrollView(Column(std::move(content))
                     .With(Padding(EdgeInsets{.top = 12.0F,
                                              .right = 12.0F,
                                              .bottom = 108.0F,
                                              .left = 12.0F}),
                           Spacing(8.0F),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
