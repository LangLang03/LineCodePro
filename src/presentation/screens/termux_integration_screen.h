#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class SshSettingsService;
class TermuxIntegrationGateway;
} // namespace linecode::application

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View TermuxIntegrationScreen(
    std::shared_ptr<application::TermuxIntegrationGateway> gateway,
    std::shared_ptr<application::SshSettingsService> ssh_settings);

} // namespace linecode::presentation
