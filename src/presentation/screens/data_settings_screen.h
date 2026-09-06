#pragma once

#include <functional>
#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class DataArchiveService;
}

namespace linecode::presentation {

struct DataSettingsCallbacks final {
  std::function<void()> persist_before_export;
  std::function<void()> before_import;
  std::function<void()> after_import;
};

[[nodiscard]] huxerui::View
DataSettingsScreen(std::shared_ptr<application::DataArchiveService> service,
                   DataSettingsCallbacks callbacks = {});

} // namespace linecode::presentation
