#include <algorithm>
#include <cassert>
#include <string_view>

#include "application/mcp_execution_settings.h"
#include "domain/mcp_execution_settings.h"

int main() {
  using namespace linecode;

  static_assert(domain::ParseMcpExecutionMode("local") ==
                domain::McpExecutionMode::local);
  static_assert(domain::ParseMcpExecutionMode("ssh") ==
                domain::McpExecutionMode::ssh);
  static_assert(domain::ParseMcpExecutionMode("terminal_provider") ==
                domain::McpExecutionMode::terminal_provider);
  static_assert(domain::ParseMcpExecutionMode("invalid") ==
                domain::McpExecutionMode::local);
  static_assert(domain::SerializeMcpExecutionMode(
                    domain::McpExecutionMode::terminal_provider) ==
                "terminal_provider");
  static_assert(domain::SupportsMcpExecutionMode(
      domain::McpExecutionModeMask::remote,
      domain::McpExecutionMode::ssh));
  static_assert(!domain::SupportsMcpExecutionMode(
      domain::McpExecutionModeMask::remote,
      domain::McpExecutionMode::local));
  static_assert(!domain::IsMcpExecutionModeAvailable(
      domain::McpExecutionMode::terminal_provider, {}));
  static_assert(domain::IsMcpExecutionModeAvailable(
      domain::McpExecutionMode::terminal_provider,
      {.terminal_provider = true}));
  static_assert(domain::NormalizeMcpExecutionMode(
                    domain::McpExecutionMode::terminal_provider, {}) ==
                domain::McpExecutionMode::local);

  assert(domain::McpEnabledSettingKey(domain::McpExecutionMode::local,
                                      "file_ops") ==
         "@linecode_mcp_enabled_file_ops");
  assert(domain::McpEnabledSettingKey(domain::McpExecutionMode::ssh,
                                      "shell") ==
         "@linecode_mcp_enabled_ssh_shell");
  assert(domain::McpEnabledSettingKey(
             domain::McpExecutionMode::terminal_provider, "shell") ==
         "@linecode_mcp_enabled_terminal_provider_shell");

  const auto settings = domain::DefaultMcpExecutionSettings();
  assert(settings.groups.size() == 8);
  assert(std::ranges::none_of(settings.groups, [](const auto& group) {
    return group.id == "phone_control" || group.id.starts_with("phone_");
  }));
  const auto file_ops =
      std::ranges::find(settings.groups, std::string_view{"file_ops"},
                        &domain::McpToolGroupState::id);
  assert(file_ops != settings.groups.end());
  assert(file_ops->enabled);
  assert(domain::SupportsMcpExecutionMode(file_ops->supported_modes,
                                          domain::McpExecutionMode::local));
  assert(!domain::SupportsMcpExecutionMode(file_ops->supported_modes,
                                           domain::McpExecutionMode::ssh));

  const auto shell = std::ranges::find(settings.groups,
                                       std::string_view{"shell"},
                                       &domain::McpToolGroupState::id);
  assert(shell != settings.groups.end());
  assert(!domain::SupportsMcpExecutionMode(shell->supported_modes,
                                           domain::McpExecutionMode::local));
  assert(domain::SupportsMcpExecutionMode(shell->supported_modes,
                                          domain::McpExecutionMode::ssh));
  assert(application::mcp_execution_setting_keys::mode ==
         std::string_view{"@lineai_mcp_execution_mode"});
}
