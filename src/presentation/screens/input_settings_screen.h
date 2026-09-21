#pragma once

#include <memory>

#include <huxerui/state.h>
#include <huxerui/view.h>

#include "domain/behavior_settings.h"

namespace linecode::application { class InputSettingsRepository; }

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View InputSettingsScreen(
    std::shared_ptr<application::InputSettingsRepository> repository,
    huxerui::State<domain::InputSettings> settings);

} // namespace linecode::presentation
