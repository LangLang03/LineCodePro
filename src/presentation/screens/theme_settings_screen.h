#pragma once

#include <memory>

#include <huxerui/state.h>
#include <huxerui/view.h>

#include "application/theme_settings.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View
ThemeSettingsScreen(std::shared_ptr<application::ThemeSettingsService> service,
                    huxerui::State<application::ThemeSettingsState> settings);

} // namespace linecode::presentation
