#include "presentation/screens/terminal_provider_screen.h"

#include <algorithm>
#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ports/terminal_provider.h"
#include "domain/app_state.h"
#include "domain/terminal_provider.h"
#include "presentation/components/legacy_settings_page.h"
#include "presentation/components/legacy_switch.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

struct TerminalProviderPageState final {
  std::vector<domain::TerminalProviderConfig> installed;
  std::vector<domain::ScannedTerminalProvider> scanned;
  std::string error;
  bool loading{true};
  bool scanning{};
  bool has_scanned{};
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(icon).Tint(tint).With(Frame{.width = size, .height = size});
}

View EmptyRow(StringVariant text, Color color = colors::tertiary) {
  return Stack{Text(std::move(text)).Style(Label(13.0F, FontWeight::Regular,
                                                color))}
      .With(Frame{.min_height = 52.0F}, Padding(16.0F),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center));
}

View ActionRow(StringVariant title, StringVariant description,
               ImageResource icon, std::function<void()> action) {
  return Row{
      Stack{Glyph(icon, 20.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(std::move(title)).Style(Label(16.0F, FontWeight::Medium)),
          Text(std::move(description))
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
  }
      .OnClick(std::move(action))
      .With(Frame{.min_height = 68.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)), Spacing(12.0F),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View ScannedRow(const domain::ScannedTerminalProvider &provider,
                std::function<void()> action) {
  return Row{
      Stack{Glyph(app::images::terminal, 16.0F, colors::accent)}.With(
          Frame{.width = 32.0F, .height = 32.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(16.0F)),
      Column{
          Text(provider.label).Style(Label(16.0F, FontWeight::Medium)),
          Text(provider.package_name)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Glyph(app::images::plus, 20.0F, colors::accent),
  }
      .OnClick(std::move(action))
      .With(Frame{.min_height = 48.0F},
            Padding(EdgeInsets::Symmetric(12.0F, 8.0F)), Spacing(8.0F),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(8.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View InstalledRow(const domain::TerminalProviderConfig &provider,
                  std::function<void(bool)> on_enabled,
                  std::function<void(const LongPressEvent &)> on_delete) {
  return Row{
      Glyph(app::images::terminal, 20.0F, colors::secondary),
      Column{
          Text(provider.name).Style(Label(16.0F, FontWeight::Medium)),
          Text(provider.package_name)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      LegacySwitch(provider.enabled, std::move(on_enabled)),
  }
      .With(LongPressGesture{})
      .On<LongPressEvents::Started>(std::move(on_delete))
      .With(Padding(16.0F), Spacing(12.0F),
            CrossAlign(CrossAxisAlignment::Center));
}

View SheetAction(StringVariant title,
                 std::optional<StringVariant> description, Color color,
                 std::function<void()> action) {
  std::vector<View> labels;
  labels.push_back(
      Text(std::move(title)).Style(Label(16.0F, FontWeight::Regular, color)));
  if (description) {
    labels.push_back(Text(std::move(*description))
                         .Style(Label(11.0F, FontWeight::Regular,
                                      colors::tertiary)));
  }
  return Column(std::move(labels))
      .OnClick(std::move(action))
      .With(Frame{.min_height = 52.0F}, Spacing(2.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 14.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View ConfirmationSheet(StringVariant title, StringVariant description,
                       std::vector<View> actions) {
  // The legacy Dialog reserves a 34dp content inset above the system
  // navigation area. HuxerUI already owns the safe-area surface, so this is
  // kept as content geometry rather than an Android-only window workaround.
  actions.push_back(Stack{}.With(Frame{.height = 34.0F}));
  return Column{
      Row{Spacer(),
          Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                       Background(colors::tertiary), CornerRadius(2.0F)),
          Spacer()}
          .With(Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F})),
      Text(std::move(title))
          .Style(Label(17.0F, FontWeight::Bold))
          .With(Padding(
              EdgeInsets{.right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      Text(std::move(description))
          .Style(Label(13.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(
              EdgeInsets{.right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      Divider(),
      Column(std::move(actions)).With(CrossAlign(CrossAxisAlignment::Stretch)),
  }
      .With(Frame{.max_width = 560.0F}, Background(colors::elevated),
            CornerRadius(CornerRadii::Top(16.0F)), ClipChildren(),
            CrossAlign(CrossAxisAlignment::Stretch));
}

Task<void> Reload(std::shared_ptr<application::TerminalProviderStore> store,
                  State<TerminalProviderPageState> state) {
  auto loaded = co_await store->ListTerminalProviders();
  state.Update([&](auto &next) {
    next.loading = false;
    if (!loaded) {
      next.error = loaded.error().message;
      return;
    }
    next.installed = std::move(*loaded);
    next.error.clear();
  });
}

bool AlreadyInstalled(const TerminalProviderPageState &state,
                      const domain::ScannedTerminalProvider &provider) {
  return std::ranges::any_of(state.installed, [&](const auto &installed) {
    return installed.package_name == provider.package_name &&
           installed.service_class == provider.service_class;
  });
}

} // namespace

[[huxerui::composable]] View TerminalProviderContent(
    std::shared_ptr<application::TerminalProviderStore> store,
    std::shared_ptr<application::TerminalProviderDiscovery> discovery) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto sheets = UseBottomSheet();
  const auto toast = UseToast();
  auto state = UseState(TerminalProviderPageState{});
  Lifecycle([tasks, store, state] { tasks.Launch(Reload(store, state)); });

  const auto scan = [discovery, state, toast] {
    if (state->scanning)
      return;
    state.Update([](auto &next) {
      next.scanning = true;
      next.error.clear();
    });
    discovery->Scan([state, toast](auto result) mutable {
      state.Update([&](auto &next) {
        next.scanning = false;
        next.has_scanned = true;
        if (!result) {
          next.error = result.error().message;
          toast.Show(app::strings::screen_terminal_provider_scan_failed);
          return;
        }
        next.scanned = std::move(*result);
        next.error.clear();
      });
    });
  };

  std::vector<View> content;
  content.push_back(LegacySettingsSection(
      app::strings::screen_terminal_provider_scan,
      {ActionRow(app::strings::screen_terminal_provider_scan,
                 app::strings::screen_terminal_provider_scan_desc,
                 app::images::search, scan)}));
  content.push_back(Stack{}.With(Frame{.height = 12.0F}));

  if (state->has_scanned) {
    std::vector<View> scan_rows;
    for (const auto &provider : state->scanned) {
      if (AlreadyInstalled(state.Get(), provider))
        continue;
      scan_rows.push_back(ScannedRow(
          provider, [provider, sheets, tasks, store, state, toast] {
            sheets.Show([provider, tasks, store, state,
                         toast](BottomSheetContext sheet) {
              std::vector<View> actions;
              actions.push_back(SheetAction(
                  app::strings::common_cancel, std::nullopt, colors::text,
                  [sheet] { sheet.Dismiss(); }));
              actions.push_back(SheetAction(
                  app::strings::screen_terminal_provider_add,
                  app::strings::screen_terminal_provider_add_confirm_desc,
                  colors::accent,
                  [provider, tasks, store, state, toast, sheet] {
                    sheet.Dismiss();
                    tasks.Launch([provider, store, state,
                                  toast]() -> Task<void> {
                      domain::TerminalProviderConfig config{
                          .enabled = true,
                          .provider_type = domain::kTerminalProviderType,
                          .name = provider.label,
                          .package_name = provider.package_name,
                          .service_class = provider.service_class,
                      };
                      auto saved = co_await store->SaveTerminalProvider(
                          std::move(config));
                      if (!saved) {
                        toast.Show(
                            app::strings::screen_terminal_provider_store_failed);
                        co_return;
                      }
                      co_await Reload(store, state);
                    });
                  }));
              return ConfirmationSheet(
                  StringVariant::Format(
                      app::strings::screen_terminal_provider_add_confirm,
                      provider.label),
                  app::strings::screen_terminal_provider_add_confirm_desc,
                  std::move(actions));
            });
          }));
    }
    if (scan_rows.empty())
      scan_rows.push_back(
          EmptyRow(app::strings::screen_terminal_provider_scan_empty));
    content.push_back(LegacySettingsSection(
        app::strings::screen_terminal_provider_scan_results,
        std::move(scan_rows)));
    content.push_back(Stack{}.With(Frame{.height = 12.0F}));
  }

  std::vector<View> installed_rows;
  if (state->loading) {
    installed_rows.push_back(
        EmptyRow(app::strings::screen_terminal_provider_loading));
  } else if (state->installed.empty()) {
    installed_rows.push_back(
        EmptyRow(app::strings::screen_terminal_provider_empty));
  } else {
    for (const auto &provider : state->installed) {
      installed_rows.push_back(
          InstalledRow(
              provider,
              [provider, tasks, store, state, toast](bool enabled) {
                tasks.Launch([provider, enabled, store, state,
                              toast]() -> Task<void> {
                  auto changed = co_await store->SetTerminalProviderEnabled(
                      provider.id, enabled);
                  if (!changed) {
                    toast.Show(
                        app::strings::screen_terminal_provider_store_failed);
                    co_return;
                  }
                  co_await Reload(store, state);
                });
              },
              [provider, sheets, tasks, store, state,
               toast](const LongPressEvent &) {
                sheets.Show([provider, tasks, store, state,
                             toast](BottomSheetContext sheet) {
                  std::vector<View> actions;
                  actions.push_back(SheetAction(
                      app::strings::common_cancel, std::nullopt, colors::text,
                      [sheet] { sheet.Dismiss(); }));
                  actions.push_back(SheetAction(
                      app::strings::screen_extension_delete, std::nullopt,
                      colors::danger,
                      [provider, tasks, store, state, toast, sheet] {
                        sheet.Dismiss();
                        tasks.Launch([provider, store, state,
                                      toast]() -> Task<void> {
                          auto deleted = co_await store->DeleteTerminalProvider(
                              provider.id);
                          if (!deleted) {
                            toast.Show(app::strings::
                                           screen_terminal_provider_store_failed);
                            co_return;
                          }
                          co_await Reload(store, state);
                        });
                      }));
                  return ConfirmationSheet(
                      app::strings::screen_terminal_provider_delete_title,
                      StringVariant::Format(
                          app::strings::screen_terminal_provider_delete_confirm,
                          provider.name),
                      std::move(actions));
                });
              })
              .Key(provider.id));
    }
  }
  content.push_back(LegacySettingsSection(
      app::strings::screen_terminal_provider_installed,
      std::move(installed_rows)));
  if (!state->error.empty())
    content.push_back(EmptyRow(state->error, colors::danger));
  content.push_back(Stack{}.With(Frame{.height = 100.0F}));

  return LegacySettingsPage(
      app::strings::screen_terminal_provider_title,
      [navigation] { navigation.Pop(); }, std::move(content));
}

[[huxerui::composable]] View TerminalProviderScreen(
    std::shared_ptr<application::TerminalProviderStore> store,
    std::shared_ptr<application::TerminalProviderDiscovery> discovery) {
  ThemeDefinition overrides;
  overrides.Set(LineDialogBottomSheetStyle(UseLineColors()));
  return Theme(
      std::move(overrides),
      Scope([store = std::move(store), discovery = std::move(discovery)] {
        return TerminalProviderContent(store, discovery);
      }));
}

} // namespace linecode::presentation
