#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application { class AiBehaviorSettingsRepository; }

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View LlmSettingsScreen(
    std::shared_ptr<application::AiBehaviorSettingsRepository> repository);

} // namespace linecode::presentation
