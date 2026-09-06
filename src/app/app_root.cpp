#include "app/app_root.h"

#include <cstddef>
#include <memory>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/http.h>

#include "app/bootstrap.h"
#include "application/behavior_settings_repository.h"
#include "application/error_log_service.h"
#include "application/output_settings.h"
#include "application/prompt_template_repository.h"
#include "application/theme_settings.h"
#if defined(__ANDROID__)
#include "application/ports/keep_alive.h"
#endif
#include "infrastructure/hux_completion_gateway.h"
#include "infrastructure/hux_error_log_store.h"
#include "infrastructure/hux_model_catalog_gateway.h"
#include "infrastructure/hux_storage_stats_repository.h"
#include "infrastructure/sqlite_model_store.h"
#include "infrastructure/sqlite_settings_store.h"
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
  return huxerui::Stack{}.With(
      huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#else
huxerui::View PlatformServicesHost() {
  return huxerui::Stack{}.With(
      huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#endif

} // namespace

[[huxerui::composable]] huxerui::View AppRoot() {
  auto file_system = huxerui::UseService<huxerui::FileSystem>();
  auto http = huxerui::UseService<huxerui::HttpClient>();
  auto error_log_platform =
      huxerui::UseService<application::ErrorLogPlatformActions>();
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
  const auto data_directory = file_system->Directories().data_directory;
  const auto database_file = data_directory.Child("linecode.db");
  auto model_store = huxerui::UseState(std::shared_ptr<application::ModelStore>{
      std::make_shared<infrastructure::SqliteModelStore>(database_file)});
  auto model_catalog =
      huxerui::UseState(std::shared_ptr<application::ModelCatalogGateway>{
          std::make_shared<infrastructure::HuxModelCatalogGateway>(http)});
  auto settings_store =
      huxerui::UseState(std::shared_ptr<application::AsyncSettingsStore>{
          std::make_shared<infrastructure::SQLiteSettingsStore>(database_file)});
  auto ai_behavior_settings = huxerui::UseState(
      std::make_shared<application::AiBehaviorSettingsRepository>(
          settings_store.Get()));
  auto input_settings = huxerui::UseState(
      std::make_shared<application::InputSettingsRepository>(
          settings_store.Get()));
  auto prompt_templates = huxerui::UseState(
      std::make_shared<application::PromptTemplateRepository>(
          settings_store.Get()));
  auto output_settings_service =
      huxerui::UseState(std::shared_ptr<application::OutputSettingsService>{
          std::make_shared<application::PersistedOutputSettings>(
              settings_store.Get())});
  auto completion_gateway =
      huxerui::UseState(std::shared_ptr<application::CompletionGateway>{
          std::make_shared<infrastructure::HuxCompletionGateway>(http)});
  auto storage_stats =
      huxerui::UseState(std::shared_ptr<application::StorageStatsRepository>{
          std::make_shared<infrastructure::HuxStorageStatsRepository>(
              database_file,
              std::vector<huxerui::File>{data_directory.Child("settings")},
              data_directory.Child(".linecode").Child("home"))});
  auto error_logs =
      huxerui::UseState(std::shared_ptr<application::ErrorLogService>{
          std::make_shared<application::ErrorLogService>(
              std::make_shared<infrastructure::HuxErrorLogStore>(
                  data_directory),
              error_log_platform)});
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
       ai_behavior_settings = ai_behavior_settings.Get(),
       input_settings = input_settings.Get(),
       prompt_templates = prompt_templates.Get(),
       output_settings_service = output_settings_service.Get(),
       completion_gateway = completion_gateway.Get(),
       theme_service = theme_service.Get(), theme_settings,
       storage_stats = storage_stats.Get(),
       error_logs = error_logs.Get()] {
        return presentation::MainScreen(initial_session, project_store,
                                        model_store, model_catalog,
                                        ai_behavior_settings, input_settings,
                                        prompt_templates,
                                        completion_gateway,
                                        output_settings_service,
                                        theme_service, theme_settings,
                                        storage_stats, error_logs);
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
