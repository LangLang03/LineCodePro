#pragma once

#include <memory>

#include <huxerui/view.h>

#include "application/output_settings.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View SecuritySettingsScreen(
    std::shared_ptr<application::OutputSettingsService> service);

} // namespace linecode::presentation
