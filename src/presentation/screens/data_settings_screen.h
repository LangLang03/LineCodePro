#pragma once

#include <expected>
#include <functional>
#include <memory>
#include <string>

#include <huxerui/task.h>
#include <huxerui/view.h>

namespace linecode::application {
class DataArchiveService;
}

namespace linecode::presentation {

using DataSettingsCallbackResult = std::expected<void, std::string>;

struct DataSettingsCallbacks final {
  std::function<huxerui::Task<DataSettingsCallbackResult>()>
      persist_before_export;
  std::function<void()> before_import;
  std::function<huxerui::Task<DataSettingsCallbackResult>()> after_import;
};

[[nodiscard]] huxerui::View
DataSettingsScreen(std::shared_ptr<application::DataArchiveService> service,
                   DataSettingsCallbacks callbacks = {});

} // namespace linecode::presentation
