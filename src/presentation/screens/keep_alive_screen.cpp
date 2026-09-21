#include "presentation/screens/keep_alive_screen.h"

#if defined(__ANDROID__)

#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ports/keep_alive.h"
#include "domain/app_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/components/legacy_switch.h"
#include "presentation/legacy_text_presentation.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {
using namespace huxerui;
using application::KeepAlivePreferences;
using application::KeepAliveService;
using application::KeepAliveSystemState;

struct KeepAlivePresentationState final {
  KeepAlivePreferences preferences;
  KeepAliveSystemState system;
  bool preferences_loaded{};
  bool system_loaded{};

  [[nodiscard]] bool Interactive() const noexcept {
    return preferences_loaded && system_loaded;
  }
};

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
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_keep_alive_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View SwitchRow(ImageResource icon, StringResource title,
               StringResource description, bool checked, bool interactive,
               std::function<void(bool)> changed) {
  auto row_changed = changed;
  return Row{
      Glyph(std::move(icon), 20.0F, colors::secondary)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
      Column{Text(title).Style(Label(16.0F, FontWeight::Medium)),
             Text(description)
                 .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))}
          .With(Spacing(2.0F), Grow()),
      LegacySwitch(checked, std::move(changed)),
  }
      .OnClick(
          [checked, changed = std::move(row_changed)] { changed(!checked); })
      .With(Spacing(12.0F), Padding(EdgeInsets::All(16.0F)),
            CrossAlign(CrossAxisAlignment::Center), Enabled(interactive),
            Focusable(),
            PointerCursor(interactive ? PointerCursorKind::Hand
                                      : PointerCursorKind::Default));
}

View Section(std::string title, std::vector<View> rows) {
  std::vector<View> children;
  for (std::size_t index = 0; index < rows.size(); ++index) {
    children.push_back(std::move(rows[index]));
    if (index + 1 < rows.size())
      children.push_back(Divider());
  }
  return Column{
      Text(title)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 20.0F, .right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      LegacySettingsCardFrame{
          Column(std::move(children))
              .With(CornerRadius(12.0F), Background(colors::elevated),
                    CrossAlign(CrossAxisAlignment::Stretch)),
      },
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}
} // namespace

[[huxerui::composable]] View KeepAliveSettingsScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto service = UseService<KeepAliveService>();
  const auto toast = UseToast();
  auto state = UseState(KeepAlivePresentationState{
      .preferences = service->LoadPreferences(),
  });
  auto mounted = std::make_shared<bool>(true);
  Lifecycle([service, state, toast, mounted] {
    service->RefreshPreferences(
        [state, toast, mounted](KeepAliveService::PreferencesResult value) {
          if (!*mounted)
            return;
          if (!value) {
            toast.Show(value.error());
            return;
          }
          state.Update([preferences = std::move(*value)](
                           KeepAlivePresentationState &next) mutable {
            next.preferences = std::move(preferences);
            next.preferences_loaded = true;
          });
        });
    service->RefreshSystemState(
        [state, toast, mounted](KeepAliveService::SystemStateResult value) {
          if (!*mounted)
            return;
          if (!value) {
            toast.Show(value.error());
            return;
          }
          state.Update([system = std::move(*value)](
                           KeepAlivePresentationState &next) mutable {
            next.system = std::move(system);
            next.system_loaded = true;
          });
        });
    return [mounted] { *mounted = false; };
  });
  const bool interactive = state->Interactive();
  const auto coding_title = LegacySectionTitle(
      UseString(app::strings::screen_keep_alive_section_coding));
  const auto system_title = LegacySectionTitle(
      UseString(app::strings::screen_keep_alive_section_system));
  auto save = [service, state, toast](bool KeepAlivePreferences::*field,
                                      bool enabled) {
    if (!state->Interactive())
      return false;
    auto next = state->preferences;
    next.*field = enabled;
    auto saved = service->SavePreferences(next);
    if (!saved) {
      toast.Show(saved.error());
      return false;
    }
    state.Update([preferences = std::move(next)](
                     KeepAlivePresentationState &value) mutable {
      value.preferences = std::move(preferences);
    });
    return true;
  };

  std::vector<View> coding;
  coding.push_back(SwitchRow(
      app::images::zap, app::strings::screen_keep_alive_wake_lock_label,
      app::strings::screen_keep_alive_wake_lock_desc,
      state->preferences.wake_lock_enabled, interactive, [save](bool enabled) {
        save(&KeepAlivePreferences::wake_lock_enabled, enabled);
      }));
  coding.push_back(SwitchRow(
      app::images::bell, app::strings::screen_keep_alive_foreground_label,
      app::strings::screen_keep_alive_foreground_desc,
      state->preferences.foreground_service_enabled, interactive,
      [save, service, state, toast](bool enabled) {
        if (!save(&KeepAlivePreferences::foreground_service_enabled, enabled))
          return;
        if (enabled && !state->system.notifications_granted) {
          service->RequestNotificationPermission();
          toast.Show(
              app::strings::screen_keep_alive_notification_permission_hint);
        }
      }));
  coding.push_back(SwitchRow(
      app::images::music, app::strings::screen_keep_alive_silent_audio_label,
      app::strings::screen_keep_alive_silent_audio_desc,
      state->preferences.silent_audio_enabled, interactive,
      [save, service, state, toast](bool enabled) {
        if (!save(&KeepAlivePreferences::silent_audio_enabled, enabled))
          return;
        if (enabled && !state->system.notifications_granted) {
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
                state->system.battery_optimization_ignored, interactive,
                [service, state](bool enabled) {
                  if (!state->Interactive())
                    return;
                  if (enabled && !state->system.battery_optimization_ignored)
                    service->RequestIgnoreBatteryOptimizations();
                }));

  return Column{
      Header(navigation), LegacyScreenHeaderDivider(),
      ScrollView(Column{Section(coding_title, std::move(coding)),
                        Section(system_title, std::move(system)),
                        Stack{}.With(Frame{.width = 1.0F, .height = 100.0F})}
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow())}
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}
} // namespace linecode::presentation

#endif
