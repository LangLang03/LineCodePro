#include "presentation/main_screen.h"

#include <cstddef>
#include <memory>
#include <utility>

#include <huxerui/huxerui.h>

#include "application/chat_session.h"
#include "domain/app_state.h"
#include "presentation/components/chat_screen.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"
#include "presentation/screens/settings_screen.h"

namespace linecode::presentation {

[[huxerui::composable]]
huxerui::View MainScreen(std::shared_ptr<application::ChatSession> initial_session) {
  using namespace huxerui;

  auto navigation_path = UseState(NavigationPath<domain::AppRoute>{});
  auto drawer_open = UseState(false);
  auto selected_drawer_tab = UseState(std::size_t{0});
  auto draft = UseState(TextEditingValue::FromText(""));
  auto revision = UseState(std::size_t{0});
  auto session = UseState(std::move(initial_session));

  auto root = [drawer_open, selected_drawer_tab, draft, revision, session]() mutable -> View {
    View centered_chat = Stack {
      ChatScreen(drawer_open, draft, session.Get(), revision).With(Frame{.max_width = 792.0F}),
    }.With(
        Align(HorizontalAlignment::Center, VerticalAlignment::Stretch),
        Background(colors::background)
    );

    return DrawerLayout(
        std::move(centered_chat),
        StartDrawer(Drawer(drawer_open, selected_drawer_tab))
            .Open(drawer_open)
            .OnOpenChanged([drawer_open](bool open) {
              drawer_open = open;
            })
    );
  };

  auto destination = [](domain::AppRoute route) -> View {
    if (route == domain::AppRoute::settings) {
      return SettingsScreen();
    }
    if (route == domain::AppRoute::keep_alive) {
      if constexpr (!FeatureAvailable<PlatformFeature::keep_alive>) {
        return SettingsScreen();
      }
    }
    return PendingScreen(route);
  };

  return NavigationStack(
      std::move(root),
      navigation_path,
      std::move(destination)
  );
}

} // namespace linecode::presentation
