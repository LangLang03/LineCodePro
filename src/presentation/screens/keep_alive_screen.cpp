#include "presentation/screens/keep_alive_screen.h"

#if defined(__ANDROID__)

#include <functional>
#include <memory>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ports/keep_alive.h"
#include "domain/app_state.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {
using namespace huxerui;
using application::KeepAlivePreferences;
using application::KeepAliveService;
using application::KeepAliveSystemState;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return Row{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Text(app::strings::screen_keep_alive_title)
          .Style(Label(17.0F, FontWeight::Bold))
          .Align(TextAlign::Center)
          .With(Grow()),
      Spacer().With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::background));
}

View SwitchRow(ImageResource icon, StringResource title,
               StringResource description, bool checked,
               std::function<void(bool)> changed) {
  auto row_changed = changed;
  return Row{
      Glyph(std::move(icon), 20.0F, colors::secondary)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
      Column{Text(title).Style(Label(16.0F, FontWeight::Medium)),
             Text(description)
                 .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))}
          .With(Spacing(2.0F), Grow()),
      Switch(checked).OnChanged(std::move(changed)),
  }
      .OnClick(
          [checked, changed = std::move(row_changed)] { changed(!checked); })
      .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View Section(StringResource title, std::vector<View> rows) {
  std::vector<View> children;
  for (std::size_t index = 0; index < rows.size(); ++index) {
    children.push_back(std::move(rows[index]));
    if (index + 1 < rows.size())
      children.push_back(Divider().With(Padding(EdgeInsets{.left = 48.0F})));
  }
  return Column{
      Text(title)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 20.0F, .right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      Column{Column(std::move(children))
                 .With(CornerRadius(12.0F), Background(colors::elevated),
                       CrossAlign(CrossAxisAlignment::Stretch))}
          .With(Padding(EdgeInsets::Symmetric(16.0F, 0.0F))),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}
} // namespace

[[huxerui::composable]] View KeepAliveSettingsScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto service = UseService<KeepAliveService>();
  const auto toast = UseToast();
  auto preferences = UseState(service->LoadPreferences());
  auto system_state = UseState(KeepAliveSystemState{});
  auto mounted = std::make_shared<bool>(true);
  Lifecycle([service, preferences, system_state, mounted] {
    service->RefreshPreferences(
        [preferences, mounted](KeepAliveService::PreferencesResult value) {
          if (*mounted && value)
            preferences = *value;
        });
    service->RefreshSystemState(
        [system_state, mounted](KeepAliveService::SystemStateResult value) {
          if (*mounted && value)
            system_state = *value;
        });
    return [mounted] { *mounted = false; };
  });
  auto save = [service, preferences](bool KeepAlivePreferences::*field,
                                     bool enabled) {
    auto next = preferences.Get();
    next.*field = enabled;
    preferences = next;
    static_cast<void>(service->SavePreferences(next));
  };

  std::vector<View> coding;
  coding.push_back(SwitchRow(
      app::images::zap, app::strings::screen_keep_alive_wake_lock_label,
      app::strings::screen_keep_alive_wake_lock_desc,
      preferences->wake_lock_enabled, [save](bool enabled) {
        save(&KeepAlivePreferences::wake_lock_enabled, enabled);
      }));
  coding.push_back(SwitchRow(
      app::images::bell, app::strings::screen_keep_alive_foreground_label,
      app::strings::screen_keep_alive_foreground_desc,
      preferences->foreground_service_enabled,
      [save, service, system_state, toast](bool enabled) {
        save(&KeepAlivePreferences::foreground_service_enabled, enabled);
        if (enabled && !system_state->notifications_granted) {
          service->RequestNotificationPermission();
          toast.Show(
              app::strings::screen_keep_alive_notification_permission_hint);
        }
      }));
  coding.push_back(SwitchRow(
      app::images::music, app::strings::screen_keep_alive_silent_audio_label,
      app::strings::screen_keep_alive_silent_audio_desc,
      preferences->silent_audio_enabled,
      [save, service, system_state, toast](bool enabled) {
        save(&KeepAlivePreferences::silent_audio_enabled, enabled);
        if (enabled && !system_state->notifications_granted) {
          service->RequestNotificationPermission();
          toast.Show(
              app::strings::screen_keep_alive_notification_permission_hint);
        }
      }));
  std::vector<View> system;
  system.push_back(
      SwitchRow(app::images::battery_charging,
                app::strings::screen_keep_alive_ignore_battery_label,
                app::strings::screen_keep_alive_ignore_battery_desc,
                system_state->battery_optimization_ignored,
                [service, system_state](bool enabled) {
                  if (enabled && !system_state->battery_optimization_ignored)
                    service->RequestIgnoreBatteryOptimizations();
                }));

  return Column{
      Header(navigation), Divider(),
      ScrollView(Column{Section(app::strings::screen_keep_alive_section_coding,
                                std::move(coding)),
                        Section(app::strings::screen_keep_alive_section_system,
                                std::move(system)),
                        Spacer().With(Frame{.height = 100.0F})}
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow(), ScrollBar())}
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}
} // namespace linecode::presentation

#endif
