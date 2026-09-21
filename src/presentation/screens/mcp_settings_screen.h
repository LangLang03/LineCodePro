#pragma once

#include <functional>
#include <memory>

#include <huxerui/view.h>

#include "domain/mcp_execution_settings.h"
#include "presentation/platform_features.h"

namespace linecode::application {
class McpExecutionSettingsService;
}

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View McpSettingsScreen(
    std::shared_ptr<application::McpExecutionSettingsService> service,
    domain::McpExecutionCapabilities capabilities,
    PlatformCapabilities platform_capabilities,
    std::function<void()> on_mode_changed = {});

} // namespace linecode::presentation
