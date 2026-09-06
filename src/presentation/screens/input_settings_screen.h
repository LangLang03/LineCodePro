#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application { class InputSettingsRepository; }

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View InputSettingsScreen(
    std::shared_ptr<application::InputSettingsRepository> repository);

} // namespace linecode::presentation
