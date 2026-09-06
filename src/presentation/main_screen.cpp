#include "presentation/main_screen.h"

#include <algorithm>
#include <cstddef>
#include <expected>
#include <iterator>
#include <memory>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>

#include "application/chat_session.h"
#include "application/ports/project_workspace_store.h"
#include "domain/app_state.h"
#include "presentation/components/chat_screen.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"
#include "presentation/screens/about_screen.h"
#include "presentation/screens/browser_screen.h"
#include "presentation/screens/licenses_screen.h"
#include "presentation/screens/model_management_screen.h"
#include "presentation/screens/settings_screen.h"
#include "presentation/screens/theme_settings_screen.h"
#if defined(__ANDROID__)
#include "presentation/screens/keep_alive_screen.h"
#endif

namespace linecode::presentation {
namespace {

using WorkspaceResult =
    std::expected<domain::ProjectWorkspace, application::ProjectWorkspaceError>;

DrawerFileNode ToDrawerNode(const domain::ProjectFileNode &node) {
  std::vector<DrawerFileNode> children;
  children.reserve(node.children.size());
  std::ranges::transform(node.children, std::back_inserter(children),
                         ToDrawerNode);
  return DrawerFileNode{
      .name = node.name,
      .path = node.path,
      .directory = node.directory,
      .expanded = node.expanded,
      .children = std::move(children),
  };
}

std::vector<DrawerConversation>
ToDrawerConversations(const application::ChatSession &session) {
  std::vector<DrawerConversation> conversations;
  conversations.reserve(session.Conversations().size());
  std::ranges::transform(
      session.Conversations(), std::back_inserter(conversations),
      [](const application::ConversationSummary &summary) {
        return DrawerConversation{
            .id = summary.id,
            .title = summary.title,
            .updated_at_millis = summary.updated_at_millis,
        };
      });
  return conversations;
}

bool ToggleDirectory(DrawerFileNode &node, std::string_view path) {
  if (node.path == path && node.directory) {
    node.expanded = !node.expanded;
    return true;
  }
  return std::ranges::any_of(node.children, [path](DrawerFileNode &child) {
    return ToggleDirectory(child, path);
  });
}

#if defined(__ANDROID__)
[[huxerui::composable]] huxerui::View PlatformKeepAliveDestination() {
  return KeepAliveSettingsScreen();
}
#else
[[huxerui::composable]] huxerui::View PlatformKeepAliveDestination() {
  return SettingsScreen();
}
#endif

huxerui::Task<void>
LoadWorkspace(std::shared_ptr<application::ProjectWorkspaceStore> store,
              huxerui::State<DrawerModel> model) {
  WorkspaceResult loaded = co_await store->Load();
  if (!loaded) {
    co_return;
  }
  DrawerModel next = model.Get();
  next.project_label = loaded->label;
  next.project_path = loaded->path;
  next.file_tree = ToDrawerNode(loaded->root);
  model = std::move(next);
}

[[huxerui::composable]]
huxerui::View
HomeScreen(std::shared_ptr<application::ChatSession> initial_session,
           std::shared_ptr<application::ProjectWorkspaceStore> project_store) {
  using namespace huxerui;

  auto drawer_open = UseState(false);
  auto selected_drawer_tab = UseState(std::size_t{0});
  auto draft = UseState(TextEditingValue::FromText(""));
  auto revision = UseState(std::size_t{0});
  auto session = UseState(std::move(initial_session));
  auto drawer_model = UseState(DrawerModel{});
  const auto tasks = UseTaskScope();

  Lifecycle([tasks, project_store, drawer_model] {
    tasks.Launch([project_store, drawer_model]() -> Task<void> {
      co_await LoadWorkspace(project_store, drawer_model);
    });
  });

  const DrawerActions drawer_actions{
      .on_new_conversation =
          [session, revision] {
            session.Get()->StartNewConversation();
            revision += 1;
          },
      .on_conversation_selected =
          [session, revision](std::string_view id) {
            session.Get()->SelectConversation(id);
            revision += 1;
          },
      .on_conversation_deleted =
          [session, revision](std::string_view id) {
            session.Get()->DeleteConversation(id);
            revision += 1;
          },
      .on_file_node_selected =
          [drawer_model](DrawerFileTarget target) {
            if (!target.directory || !drawer_model->file_tree) {
              return;
            }
            DrawerModel next = drawer_model.Get();
            if (ToggleDirectory(*next.file_tree, target.path)) {
              drawer_model = std::move(next);
            }
          },
      .on_file_tree_activated =
          [tasks, project_store, drawer_model] {
            if (!drawer_model->file_tree) {
              tasks.Launch([project_store, drawer_model]() -> Task<void> {
                co_await LoadWorkspace(project_store, drawer_model);
              });
            }
          },
      .on_file_tree_refresh =
          [tasks, project_store, drawer_model] {
            tasks.Launch([project_store, drawer_model]() -> Task<void> {
              co_await LoadWorkspace(project_store, drawer_model);
            });
          },
  };

  DrawerModel visible_drawer = drawer_model.Get();
  visible_drawer.conversations = ToDrawerConversations(*session.Get());
  visible_drawer.selected_conversation_id =
      std::string{session.Get()->CurrentConversationId()};

  View centered_chat =
      Stack{
          ChatScreen([drawer_open] { drawer_open = true; }, draft,
                     session.Get(), revision, drawer_model)
              .With(Frame{.max_width = 792.0F}),
      }
          .With(Align(HorizontalAlignment::Center, VerticalAlignment::Stretch),
                Background(colors::background), SafeAreaPadding{});

  return DrawerLayout(
      centered_chat,
      StartDrawer(Drawer(drawer_open, selected_drawer_tab, visible_drawer,
                         drawer_actions))
          .Open(drawer_open.Get())
          .OnOpenChanged([drawer_open](bool open) { drawer_open = open; }));
}

} // namespace

[[huxerui::composable]]
huxerui::View
MainScreen(std::shared_ptr<application::ChatSession> initial_session,
           std::shared_ptr<application::ProjectWorkspaceStore> project_store,
           std::shared_ptr<application::ModelStore> model_store,
           std::shared_ptr<application::ModelCatalogGateway> model_catalog,
           std::shared_ptr<application::ThemeSettingsService> theme_service,
           huxerui::State<application::ThemeSettingsState> theme_settings) {
  using namespace huxerui;

  auto navigation_path = UseState(NavigationPath<domain::AppRoute>{});

  auto root = [initial_session = std::move(initial_session),
               project_store = std::move(project_store)]() -> View {
    return HomeScreen(initial_session, project_store);
  };

  auto destination = [model_store = std::move(model_store),
                      model_catalog = std::move(model_catalog),
                      theme_service = std::move(theme_service),
                      theme_settings](domain::AppRoute route) -> View {
    if (const auto *browser = route.BrowserValue()) {
      return BrowserScreen(*browser);
    }
    if (route == domain::AppRoute::settings) {
      return SettingsScreen();
    }
    if (route == domain::AppRoute::keep_alive) {
      return PlatformKeepAliveDestination();
    }
    if (route == domain::AppRoute::models) {
      return ModelManagementScreen(model_store, model_catalog);
    }
    if (route == domain::AppRoute::theme) {
      return ThemeSettingsScreen(theme_service, theme_settings);
    }
    if (route == domain::AppRoute::about) {
      return AboutScreen(domain::AppRoute::licenses);
    }
    if (route == domain::AppRoute::licenses) {
      return LicensesScreen();
    }
    return PendingScreen(route);
  };

  return Stack{
      NavigationStack(std::move(root), navigation_path, std::move(destination)),
  }
      .With(Align(HorizontalAlignment::Stretch, VerticalAlignment::Stretch));
}

} // namespace linecode::presentation
