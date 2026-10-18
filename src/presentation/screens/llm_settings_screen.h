#pragma once

#include <functional>
#include <memory>

#include <huxerui/state.h>
#include <huxerui/view.h>

#include "presentation/components/behavior_settings_state.h"

namespace linecode::application { class AiBehaviorSettingsRepository; }

namespace linecode::presentation {

// `behavior_state` is the shared cell owned by the main screen, and `reload`
// re-reads it from the store (used to recover from a failed load). The screen
// never loads a private copy and never renders the struct defaults as if they
// were stored values.
[[huxerui::composable]] huxerui::View LlmSettingsScreen(
    std::shared_ptr<application::AiBehaviorSettingsRepository> repository,
    huxerui::State<BehaviorSettingsState> behavior_state,
    std::function<void()> reload);

} // namespace linecode::presentation
