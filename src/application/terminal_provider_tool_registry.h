#pragma once

#include <memory>
#include <optional>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/terminal_provider.h"
#include "application/ports/tool_registry.h"

namespace linecode::application {

inline constexpr std::string_view kTerminalShellToolName = "shell_execute";

// Publishes the terminal-provider shell only while that execution mode and
// tool group are enabled. Provider selection and transport remain behind
// injected ports, so additional remote strategies do not alter the loop.
class TerminalProviderToolRegistry final : public ToolRegistry {
public:
  TerminalProviderToolRegistry(
      std::shared_ptr<McpExecutionSettingsService> settings,
      std::shared_ptr<TerminalProviderStore> providers,
      std::shared_ptr<TerminalProviderGateway> gateway);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool>
  Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<TerminalProviderStore> providers_;
  std::shared_ptr<TerminalProviderGateway> gateway_;
  std::optional<domain::TerminalProviderConfig> active_provider_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
