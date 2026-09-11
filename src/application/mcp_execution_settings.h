#pragma once

#include <memory>
#include <string>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"
#include "domain/mcp_execution_settings.h"

namespace linecode::application {

namespace mcp_execution_setting_keys {
inline constexpr auto mode = "@lineai_mcp_execution_mode";
} // namespace mcp_execution_setting_keys

class McpExecutionSettingsService {
public:
  virtual ~McpExecutionSettingsService() = default;

  [[nodiscard]] virtual huxerui::Task<
      SettingsResult<domain::McpExecutionSettings>>
  Load() = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode mode, std::string id,
                      bool enabled) = 0;
};

class McpExecutionSettingsRepository final
    : public McpExecutionSettingsService {
public:
  explicit McpExecutionSettingsRepository(
      std::shared_ptr<AsyncSettingsStore> store,
      domain::McpExecutionCapabilities capabilities = {});

  [[nodiscard]] huxerui::Task<SettingsResult<domain::McpExecutionSettings>>
  Load() override;
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override;
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode mode, std::string id,
                      bool enabled) override;

private:
  std::shared_ptr<AsyncSettingsStore> store_;
  domain::McpExecutionCapabilities capabilities_;
};

} // namespace linecode::application
