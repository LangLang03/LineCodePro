#pragma once

#include <huxerui/view.h>

#include "domain/app_state.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View SettingsScreen();
[[huxerui::composable]] huxerui::View PendingScreen(domain::AppRoute current);

} // namespace linecode::presentation
