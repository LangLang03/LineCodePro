#pragma once

#include <memory>
#include <optional>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/tool_registry.h"
#include "application/ssh_runtime_service.h"
#include "application/ssh_settings_service.h"

namespace linecode::application {

inline constexpr std::string_view kSshShellToolName = "shell_execute";

class SshToolRegistry final : public ToolRegistry {
public:
  SshToolRegistry(std::shared_ptr<McpExecutionSettingsService> settings,
                  std::shared_ptr<SshSettingsService> ssh_settings,
                  std::shared_ptr<SshExecutionService> execution);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<SshSettingsService> ssh_settings_;
  std::shared_ptr<SshExecutionService> execution_;
  std::optional<domain::SshConfig> active_config_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
