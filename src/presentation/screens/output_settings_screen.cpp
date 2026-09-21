#include "presentation/screens/output_settings_screen.h"

#include <functional>
#include <memory>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/components/legacy_settings_page.h"
#include "presentation/components/legacy_switch.h"
#include "presentation/components/tutorial_markdown.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using application::BrowserMode;
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
      LegacySwitch(checked, std::move(changed)),
  }
      .OnClick(
          [checked, changed = std::move(row_changed)] { changed(!checked); })
      .With(Frame{.min_height = 72.4F}, Spacing(12.0F),
            Padding(EdgeInsets::All(16.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View BrowserOptionRow(ImageResource icon, StringResource title,
                      StringResource description, bool selected,
                      std::function<void()> select) {
  return Row{
      Glyph(std::move(icon), 20.0F,
            selected ? colors::accent : colors::secondary),
      Column{
          Text(title).Style(
              Label(16.0F, selected ? FontWeight::Medium : FontWeight::Regular,
                    selected ? colors::accent : colors::text)),
          Text(description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
  }
      .OnClick(std::move(select))
      .With(Frame{.min_height = 64.75F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(selected ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View MarkdownPreview(std::string_view markdown, bool code_wrap_enabled) {
  const infrastructure::TutorialMarkdownParser parser;
  return TutorialMarkdownDocumentView(parser.Parse(markdown), code_wrap_enabled,
                                      1.0F, {}, {})
      .With(Padding(16.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

View PreviewNavigationRow(std::function<void()> open_preview) {
  return Row{
      Stack{Glyph(app::images::file_code, 20.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(app::strings::screen_output_toolcall_preview_label)
              .Style(Label(16.0F, FontWeight::Medium)),
          Text(app::strings::screen_output_toolcall_preview_desc)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Stack{Glyph(app::images::chevron_right, 17.0F, colors::tertiary)}.With(
          Frame{.width = 20.0F, .height = 20.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
  }
      .OnClick(std::move(open_preview))
      .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

} // namespace

[[huxerui::composable]] View
OutputSettingsScreen(std::shared_ptr<OutputSettingsService> service) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
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

  std::vector<View> content;
  content.reserve(7);
  content.push_back(LegacySettingsSection(
      app::strings::screen_output_section_conversation,
      {SwitchRow(app::images::expand,
                 app::strings::screen_output_process_auto_expand_label,
                 app::strings::screen_output_process_auto_expand_desc,
                 state->process_auto_expand_enabled, [change](bool enabled) {
                   change(OutputBooleanChange{
                       OutputBooleanSetting::process_auto_expand, enabled});
                 })}));
  content.push_back(LegacySettingsSection(
      app::strings::screen_output_section_code,
      {SwitchRow(app::images::scroll_text,
                 app::strings::screen_output_code_wrap_label,
                 app::strings::screen_output_code_wrap_desc,
                 state->code_wrap_enabled, [change](bool enabled) {
                   change(OutputBooleanChange{OutputBooleanSetting::code_wrap,
                                              enabled});
                 })}));
  content.push_back(LegacySettingsSection(
      app::strings::screen_output_section_browser,
      {BrowserOptionRow(app::images::globe,
                        app::strings::screen_output_browser_internal_label,
                        app::strings::screen_output_browser_internal_desc,
                        state->browser_mode == BrowserMode::builtin,
                        [change] {
                          change(application::BrowserModeChange{
                              BrowserMode::builtin});
                        }),
       Divider().With(Padding(EdgeInsets{.left = 52.0F})),
       BrowserOptionRow(app::images::external_link,
                        app::strings::screen_output_browser_external_label,
                        app::strings::screen_output_browser_external_desc,
                        state->browser_mode == BrowserMode::external, [change] {
                          change(application::BrowserModeChange{
                              BrowserMode::external});
                        })}));
  content.push_back(
      LegacySettingsSection(app::strings::screen_output_section_preview,
                            {MarkdownPreview(
                                UseString(app::strings::
                                              screen_output_preview_markdown),
                                state->code_wrap_enabled)}));
  content.push_back(LegacySettingsSection(
      app::strings::screen_output_section_toolcall,
      {PreviewNavigationRow([navigation] {
        navigation.Push(domain::AppRoute::tool_call_preview);
      })}));
  if (persistence_failed.Get()) {
    content.push_back(
        Text(app::strings::screen_settings_persistence_failed)
            .Style(Label(11.0F, FontWeight::Regular, colors::danger))
            .With(Padding(16.0F)));
  }

  return LegacySettingsPage(
      app::strings::screen_output_title, [navigation] { navigation.Pop(); },
      std::move(content));
}

} // namespace linecode::presentation
