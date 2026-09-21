#pragma once

#include <cstddef>
#include <memory>

#include <huxerui/view.h>

#include "application/ports/model_store.h"
#include "application/tool_settings_service.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View ToolSettingsScreen(
    std::shared_ptr<application::ToolSettingsService> service,
    std::shared_ptr<application::ModelStore> models,
    std::size_t reload_revision = 0);

} // namespace linecode::presentation
