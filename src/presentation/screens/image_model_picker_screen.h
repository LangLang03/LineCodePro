#pragma once

#include <functional>
#include <memory>

#include <huxerui/view.h>

#include "application/ports/model_store.h"
#include "application/tool_settings_service.h"
#include "domain/tool_settings.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View ImageModelPickerScreen(
    domain::ImageModelPurpose purpose,
    std::shared_ptr<application::ToolSettingsService> settings,
    std::shared_ptr<application::ModelStore> models,
    std::function<void()> on_selected = {});

} // namespace linecode::presentation
