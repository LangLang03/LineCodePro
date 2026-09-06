#pragma once

#include <string_view>

#include <huxerui/file.h>

#include "application/ports/error_log_store.h"

namespace linecode::infrastructure {

class HuxErrorLogStore final : public application::ErrorLogStore {
public:
  explicit HuxErrorLogStore(huxerui::File application_data_directory);

  [[nodiscard]] huxerui::Task<application::ErrorLogResult<
      std::vector<domain::ErrorLogEntry>>>
  List() override;
  [[nodiscard]] huxerui::Task<
      application::ErrorLogResult<std::string>>
  Read(std::string_view entry_id) override;
  [[nodiscard]] huxerui::Task<application::ErrorLogResult<void>>
  Clear() override;

private:
  huxerui::File directory_;
};

} // namespace linecode::infrastructure
