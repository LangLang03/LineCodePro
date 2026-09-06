#include "presentation/screens/security_settings_screen.h"

#include <functional>
#include <memory>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "presentation/components/legacy_settings_page.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using application::OutputBooleanChange;
using application::OutputBooleanSetting;
using application::OutputSettingsChange;
using application::OutputSettingsService;
using application::OutputSettingsState;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

Task<void> LoadSettings(std::shared_ptr<OutputSettingsService> service,
                        State<OutputSettingsState> state,
                        State<bool> persistence_failed) {
  auto loaded = co_await service->Load();
  if (!loaded) {
    persistence_failed = true;
    co_return;
  }
  state = std::move(*loaded);
  persistence_failed = false;
}

Task<void> PersistSettings(std::shared_ptr<OutputSettingsService> service,
                           State<OutputSettingsState> state,
                           State<bool> persistence_failed,
                           OutputSettingsState previous,
                           OutputSettingsState optimistic,
                           OutputSettingsChange change) {
  auto persisted = co_await service->Persist(std::move(change));
  if (persisted) {
    persistence_failed = false;
    co_return;
  }
  if (state.Get() == optimistic)
    state = previous;
  persistence_failed = true;
}

void ApplyAndPersist(TaskScope tasks,
                     const std::shared_ptr<OutputSettingsService> &service,
                     State<OutputSettingsState> state,
                     State<bool> persistence_failed,
                     OutputSettingsChange change) {
  const OutputSettingsState previous = state.Get();
  const OutputSettingsState optimistic =
      application::ApplyOutputSettingsChange(previous, change);
  if (optimistic == previous)
    return;
  state = optimistic;
  tasks.Launch([service, state, persistence_failed, previous, optimistic,
                change = std::move(change)]() mutable {
    return PersistSettings(service, state, persistence_failed, previous,
                           optimistic, std::move(change));
  });
}

View SwitchRow(ImageResource icon, StringResource title,
               StringResource description, bool checked,
               std::function<void(bool)> changed) {
  auto row_changed = changed;
  return Row{
      Glyph(std::move(icon), 20.0F, colors::secondary),
      Column{
          Text(title).Style(Label(16.0F, FontWeight::Medium)),
          Text(description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Switch(checked).OnChanged(std::move(changed)),
  }
      .OnClick(
          [checked, changed = std::move(row_changed)] { changed(!checked); })
      .With(Spacing(12.0F), Padding(EdgeInsets::All(16.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

} // namespace

[[huxerui::composable]] View
SecuritySettingsScreen(std::shared_ptr<OutputSettingsService> service) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto dialogs = UseDialog();
  auto state = UseState(OutputSettingsState{});
  auto persistence_failed = UseState(false);

  Lifecycle([tasks, service, state, persistence_failed] {
    tasks.Launch([service, state, persistence_failed] {
      return LoadSettings(service, state, persistence_failed);
    });
  });

  auto change = [tasks, service, state,
                 persistence_failed](OutputSettingsChange update) {
    ApplyAndPersist(tasks, service, state, persistence_failed,
                    std::move(update));
  };
  auto change_boolean = [change](OutputBooleanSetting setting, bool value) {
    change(OutputBooleanChange{setting, value});
  };

  std::vector<View> content;
  content.reserve(4);
  content.push_back(LegacySettingsSection(
      app::strings::screen_security_section_http,
      {SwitchRow(app::images::shield_check,
                 app::strings::settings_row_security_allow_any_http_title,
                 app::strings::settings_row_security_allow_any_http_desc,
                 state->allow_any_http, [change_boolean](bool enabled) {
                   change_boolean(OutputBooleanSetting::allow_any_http,
                                  enabled);
                 })}));
  content.push_back(LegacySettingsSection(
      app::strings::screen_security_section_browser,
      {SwitchRow(
          app::images::code, app::strings::screen_output_browser_js_label,
          app::strings::screen_output_browser_js_desc,
          state->browser_javascript_enabled, [change_boolean](bool enabled) {
            change_boolean(OutputBooleanSetting::browser_javascript, enabled);
          })}));
  content.push_back(LegacySettingsSection(
      app::strings::screen_security_section_path,
      {SwitchRow(
          app::images::shield,
          app::strings::settings_row_security_bypass_path_title,
          app::strings::settings_row_security_bypass_path_desc,
          state->bypass_path_protection,
          [dialogs, change_boolean,
           bypassed = state->bypass_path_protection](bool enabled) {
            if (!enabled || bypassed) {
              change_boolean(OutputBooleanSetting::bypass_path_protection,
                             enabled);
              return;
            }
            dialogs.Show(
                app::strings::settings_row_security_bypass_path_warning_title,
                app::strings::settings_row_security_bypass_path_warning_message,
                app::strings::common_confirm, app::strings::common_cancel,
                [change_boolean] {
                  change_boolean(OutputBooleanSetting::bypass_path_protection,
                                 true);
                });
          })}));
  if (persistence_failed.Get()) {
    content.push_back(
        Text(app::strings::screen_settings_persistence_failed)
            .Style(Label(11.0F, FontWeight::Regular, colors::danger))
            .With(Padding(16.0F)));
  }

  return LegacySettingsPage(
      app::strings::screen_security_title, [navigation] { navigation.Pop(); },
      std::move(content));
}

} // namespace linecode::presentation
