#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class TerminalProviderDiscovery;
class TerminalProviderStore;
}

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View TerminalProviderScreen(
    std::shared_ptr<application::TerminalProviderStore> store,
    std::shared_ptr<application::TerminalProviderDiscovery> discovery);

} // namespace linecode::presentation
