#include "application/mcp_execution_settings.h"

#include <stdexcept>
#include <utility>

namespace linecode::application {

McpExecutionSettingsRepository::McpExecutionSettingsRepository(
    std::shared_ptr<AsyncSettingsStore> store,
    domain::McpExecutionCapabilities capabilities)
    : store_(std::move(store)), capabilities_(capabilities) {
  if (!store_)
    throw std::invalid_argument("MCP settings store must not be empty");
}

huxerui::Task<SettingsResult<domain::McpExecutionSettings>>
McpExecutionSettingsRepository::Load() {
  auto stored_mode = co_await store_->GetString(
      mcp_execution_setting_keys::mode,
      std::string{domain::SerializeMcpExecutionMode(
          domain::McpExecutionMode::local)});
  if (!stored_mode)
    co_return std::unexpected(stored_mode.error());

  const auto mode = domain::NormalizeMcpExecutionMode(
      domain::ParseMcpExecutionMode(*stored_mode), capabilities_);
  auto settings = domain::DefaultMcpExecutionSettings(mode);
  for (auto& group : settings.groups) {
    auto enabled = co_await store_->GetBoolean(
        domain::McpEnabledSettingKey(mode, group.id), group.enabled);
    if (!enabled)
      co_return std::unexpected(enabled.error());
    group.enabled = *enabled;
  }
  co_return settings;
}

huxerui::Task<SettingsResult<void>>
McpExecutionSettingsRepository::SetMode(domain::McpExecutionMode mode) {
  mode = domain::NormalizeMcpExecutionMode(mode, capabilities_);
  co_return co_await store_->SetString(
      mcp_execution_setting_keys::mode,
      std::string{domain::SerializeMcpExecutionMode(mode)});
}

huxerui::Task<SettingsResult<void>>
McpExecutionSettingsRepository::SetToolGroupEnabled(
    domain::McpExecutionMode mode, std::string id, bool enabled) {
  mode = domain::NormalizeMcpExecutionMode(mode, capabilities_);
  co_return co_await store_->SetBoolean(
      domain::McpEnabledSettingKey(mode, id), enabled);
}

} // namespace linecode::application
