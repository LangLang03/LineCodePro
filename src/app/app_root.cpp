#include "app/app_root.h"

#include <cstddef>
#include <memory>
#include <utility>

#include <huxerui/huxerui.h>

#include "app/bootstrap.h"
#if defined(__ANDROID__)
#include "application/ports/keep_alive.h"
#endif
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/main_screen.h"

namespace linecode::app {
namespace {

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
  auto tasks = huxerui::UseTaskScope();
  auto persistence_revision = huxerui::UseState(std::size_t{0});
  auto chat = huxerui::UseState(std::make_shared<ChatSessionBootstrap>(
      tasks, [persistence_revision] { persistence_revision += 1; }));
  const auto database_file =
      file_system->Directories().data_directory.Child("linecode.db");
  huxerui::Lifecycle([tasks, chat = chat.Get(), database_file] {
    tasks.Launch(
        [chat, database_file] { return chat->InitializeAsync(database_file); });
  });
  static_cast<void>(persistence_revision.Get());

  auto project_store =
      CreateProjectWorkspaceStore(file_system->Directories()
                                      .data_directory.Child(".linecode")
                                      .Child("home"));
  auto theme = presentation::LineLightThemeDefinition();
  theme.Set(presentation::LegacyDrawerStyle());
  return huxerui::Stack{
      huxerui::Theme(std::move(theme),
                     presentation::MainScreen(chat.Get()->Session(),
                                              std::move(project_store))),
      PlatformServicesHost(),
  };
}

} // namespace linecode::app
