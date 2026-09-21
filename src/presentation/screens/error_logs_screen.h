#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class ErrorLogService;
}

namespace linecode::presentation {

[[nodiscard]] huxerui::View
ErrorLogsScreen(std::shared_ptr<application::ErrorLogService> service);

} // namespace linecode::presentation
