#include "app/app_root.h"

#include <utility>

#include <huxerui/theme.h>

#include "app/bootstrap.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/main_screen.h"

namespace linecode::app {

huxerui::View AppRoot() {
  auto theme = presentation::LineCoffeeThemeDefinition();
  theme.Set(presentation::LegacyDrawerStyle());
  return huxerui::Theme(std::move(theme), presentation::MainScreen(CreateChatSession()));
}

} // namespace linecode::app
