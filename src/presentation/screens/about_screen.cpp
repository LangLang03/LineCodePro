#include "presentation/screens/about_screen.h"

#include <functional>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

constexpr std::string_view kProjectUrl =
    "https://github.com/LangLang03/LineCodePro";

// Android TextView includes different single-line font extents than HuxerUI's
// shaped text. These local spacings preserve the legacy About screen's painted
// gaps without changing the shared typography or theme.
constexpr float kNameTopSpacing = 8.5F;
constexpr float kVersionTopSpacing = 1.0F;
constexpr float kHeaderBottomSpacing = 18.75F;
constexpr float kGroupTitleTopSpacing = 17.0F;
constexpr float kRowTextSpacing = 12.25F;
constexpr float kFooterTopSpacing = 18.5F;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

std::string VersionValue(const AboutAppInfo &app_info) {
  return app_info.version_name + " (" + std::to_string(app_info.version_code) +
         ")";
}

View Header(StringResource title,
            const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{
          Glyph(app::images::chevron_left, 22.0F, colors::text),
      }
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
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

View IconTile(ImageResource icon) {
  return Stack{
      Glyph(std::move(icon), 20.0F, colors::accent),
  }
      .With(Frame{.width = 36.0F, .height = 36.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(colors::accent_muted), CornerRadius(8.0F));
}

View AboutRow(ImageResource icon, StringResource label, StringVariant value,
              std::function<void()> action = {}) {
  View row =
      Row{
          IconTile(std::move(icon)),
          Column{
              Text(label).Style(Label(16.0F, FontWeight::Medium)),
              Text(std::move(value))
                  .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
                  .With(Padding(EdgeInsets{.top = 2.0F})),
          }
              .With(Grow()),
          action ? Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
                       .With(Frame{.width = 20.0F, .height = 20.0F})
                 : Stack{}.With(Frame{.width = 0.0F, .height = 0.0F}),
      }
          .With(Frame{.min_height = 68.0F}, Spacing(kRowTextSpacing),
                Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::elevated), CornerRadius(12.0F),
                Focusable(static_cast<bool>(action)),
                PointerCursor(action ? PointerCursorKind::Hand
                                     : PointerCursorKind::Default));
  if (action) {
    row = std::move(row).OnClick(std::move(action));
  }
  return row;
}

void AppendGroupTitle(std::vector<View> &content, StringResource title) {
  content.push_back(
      Text(title)
          .Style(Label(13.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = kGroupTitleTopSpacing,
              .right = 0.0F,
              .bottom = 8.0F,
              .left = 4.0F,
          })));
}

void AppendRow(std::vector<View> &content, View row) {
  content.push_back(row);
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 8.0F}));
}

} // namespace

[[huxerui::composable]] View AboutScreen(domain::AppRoute licenses_route,
                                         AboutAppInfo app_info) {
  const auto navigation = UseNavigation<domain::AppRoute>();

  std::vector<View> content;
  content.reserve(16);
  content.push_back(Column{
      Stack{
          Glyph(app::images::code, 48.0F, colors::accent),
      }
          .With(Frame{.width = 88.0F, .height = 88.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Background(colors::accent_muted), CornerRadius(44.0F)),
      Text(app_info.app_name)
          .Style(Label(20.0F, FontWeight::Bold))
          .With(Padding(EdgeInsets{.top = kNameTopSpacing})),
      Text(UseString(app::strings::screen_about_apk_label) + " " +
           VersionValue(app_info))
          .Style(Label(16.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets{.top = kVersionTopSpacing})),
  }
                        .With(Padding(EdgeInsets{
                                  .top = 20.0F,
                                  .right = 0.0F,
                                  .bottom = kHeaderBottomSpacing,
                                  .left = 0.0F,
                              }),
                              CrossAlign(CrossAxisAlignment::Center)));

  AppendGroupTitle(content, app::strings::screen_about_section_version);
  AppendRow(content, AboutRow(app::images::package,
                              app::strings::screen_about_apk_version,
                              VersionValue(app_info)));
  AppendGroupTitle(content, app::strings::screen_about_section_developer);
  AppendRow(content, AboutRow(app::images::user,
                              app::strings::screen_about_developer_label,
                              app::strings::screen_about_developer_value));
  AppendRow(content, AboutRow(app::images::message_circle,
                              app::strings::screen_about_qq_label,
                              app::strings::screen_about_qq_value));
  AppendRow(content,
            AboutRow(app::images::git_branch,
                     app::strings::screen_about_github_label,
                     app::strings::screen_about_github_value, [navigation] {
                       navigation.Push(
                           domain::AppRoute::Browser(std::string(kProjectUrl)));
                     }));

  AppendGroupTitle(content, app::strings::screen_about_section_legal);
  AppendRow(content, AboutRow(app::images::file_text,
                              app::strings::screen_about_open_source_licenses,
                              app::strings::screen_about_legal_value,
                              [navigation, licenses_route] {
                                navigation.Push(licenses_route);
                              }));
  content.push_back(
      Text(app::strings::screen_about_copyright)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .Align(TextAlign::Center)
          .With(Padding(EdgeInsets{.top = kFooterTopSpacing})));

  return Column{
      Header(app::strings::screen_about_title, navigation),
      Divider(),
      ScrollView(Column(std::move(content))
                     .With(Padding(EdgeInsets{.top = 16.0F,
                                              .right = 16.0F,
                                              .bottom = 100.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
