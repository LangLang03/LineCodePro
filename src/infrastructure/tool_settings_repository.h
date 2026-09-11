#pragma once

#include <memory>

#include "application/ports/settings_store.h"
#include "application/tool_settings_service.h"

namespace linecode::infrastructure {

class PersistedToolSettings final
    : public application::ToolSettingsService {
public:
  explicit PersistedToolSettings(
      std::shared_ptr<application::AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<
      application::SettingsResult<domain::ToolSettingsState>>
  Load() override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  Persist(application::ToolSettingsChange change) override;

private:
  std::shared_ptr<application::AsyncSettingsStore> store_;
};

} // namespace linecode::infrastructure
