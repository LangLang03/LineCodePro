#include "app/app_root.h"

#include <cstddef>
#include <memory>
#include <utility>

#include <huxerui/huxerui.h>

#include "app/bootstrap.h"
#include "application/theme_settings.h"
#if defined(__ANDROID__)
#include "application/ports/keep_alive.h"
#endif
#include "infrastructure/fixed_model_catalog.h"
#include "infrastructure/sqlite_model_store.h"
#include "infrastructure/theme_file_settings_store.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/main_screen.h"

namespace linecode::app {
namespace {

class HostSystemThemeSource final : public application::SystemThemeSource {
public:
  explicit HostSystemThemeSource(bool dark) : dark_(dark) {}

  [[nodiscard]] bool IsDarkModeEnabled() const override { return dark_; }
  void Update(bool dark) { dark_ = dark; }

private:
  bool dark_{};
};

bool IsDark(const huxerui::Color &color) {
  const float luminance =
      color.red * 0.2126F + color.green * 0.7152F + color.blue * 0.0722F;
  return luminance < 0.5F;
}

#if defined(__ANDROID__)
[[huxerui::composable]] huxerui::View PlatformServicesHost() {
  auto keep_alive = huxerui::UseService<application::KeepAliveService>();
  huxerui::Lifecycle(
      [keep_alive] { keep_alive->RefreshPreferences([](auto) {}); });
  return huxerui::Spacer().With(huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#else
huxerui::View PlatformServicesHost() {
  return huxerui::Spacer().With(huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#endif

} // namespace

[[huxerui::composable]] huxerui::View AppRoot() {
  auto file_system = huxerui::UseService<huxerui::FileSystem>();
  const bool host_is_dark = IsDark(huxerui::UseTheme().colors.background);
  auto system_theme =
      huxerui::UseState(std::make_shared<HostSystemThemeSource>(host_is_dark));
  system_theme.Get()->Update(host_is_dark);
  auto theme_store = huxerui::UseState(
      std::make_shared<infrastructure::ThemeFileSettingsStore>(
          file_system->Directories().data_directory.Child("settings")));
  auto theme_service =
      huxerui::UseState(std::shared_ptr<application::ThemeSettingsService>{
          std::make_shared<application::ThemeSettingsRepository>(
              theme_store.Get(), system_theme.Get())});
  auto theme_settings = huxerui::UseState(theme_service.Get()->Load());
  auto tasks = huxerui::UseTaskScope();
  auto persistence_revision = huxerui::UseState(std::size_t{0});
  auto chat = huxerui::UseState(std::make_shared<ChatSessionBootstrap>(
      tasks, [persistence_revision] { persistence_revision += 1; }));
  const auto database_file =
      file_system->Directories().data_directory.Child("linecode.db");
  auto model_store = huxerui::UseState(std::shared_ptr<application::ModelStore>{
      std::make_shared<infrastructure::SqliteModelStore>(database_file)});
  auto model_catalog =
      huxerui::UseState(std::shared_ptr<application::ModelCatalogGateway>{
          std::make_shared<infrastructure::FixedModelCatalog>()});
  huxerui::Lifecycle([tasks, chat = chat.Get(), database_file] {
    tasks.Launch(
        [chat, database_file] { return chat->InitializeAsync(database_file); });
  });
  static_cast<void>(persistence_revision.Get());

  auto project_store =
      CreateProjectWorkspaceStore(file_system->Directories()
                                      .data_directory.Child(".linecode")
                                      .Child("home"));
  const auto line_colors =
      presentation::LineColorsForPalette(theme_settings->palette);
  auto theme = presentation::LineThemeDefinition(line_colors);
  auto drawer_style = presentation::LegacyDrawerStyle();
  drawer_style.background = line_colors.background;
  drawer_style.scrim = line_colors.overlay;
  theme.Set(std::move(drawer_style));
  auto main_content = huxerui::Scope(
      [initial_session = chat.Get()->Session(), project_store,
       model_store = model_store.Get(), model_catalog = model_catalog.Get(),
       theme_service = theme_service.Get(), theme_settings] {
        return presentation::MainScreen(initial_session, project_store,
                                        model_store, model_catalog,
                                        theme_service, theme_settings);
      });
  return huxerui::Stack{
      huxerui::ProvideEnvironment(
          line_colors,
          huxerui::Theme(std::move(theme), std::move(main_content))),
      PlatformServicesHost(),
  }
      .With(huxerui::SystemBarsAppearance{
          .status_bar_background = line_colors.background,
          .navigation_bar_background = line_colors.background,
          .status_bar_content = huxerui::SystemBarContentBrightness::Automatic,
          .navigation_bar_content =
              huxerui::SystemBarContentBrightness::Automatic,
      });
}

} // namespace linecode::app
