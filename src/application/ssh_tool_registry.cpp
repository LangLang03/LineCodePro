#include "application/ssh_tool_registry.h"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <utility>

#include "application/shell_result_content.h"
#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

constexpr std::string_view kShellSchema =
    R"({"properties":{"command":{"description":"The shell command to execute","type":"string"},"cwd":{"description":"Optional working directory; the command runs after cd into it","type":"string"},"timeoutMs":{"description":"Optional timeout in milliseconds, default 30000, max 300000","type":"number"}},"required":["command"],"type":"object"})";

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

bool ShellEnabled(const domain::McpExecutionSettings &settings) {
  const auto found =
      std::ranges::find(settings.groups, std::string_view{"shell"},
                        [](const domain::McpToolGroupState &group) {
                          return std::string_view{group.id};
                        });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

std::expected<SshCommandRequest, ToolRegistryError>
ParseArguments(std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!object) {
    return std::unexpected(
        Error(ToolRegistryErrorCode::invalid_arguments,
              "shell_execute arguments must be a JSON object"));
  }
  const auto *command = json::AsString(json::Find(*object, "command"));
  if (!command || command->empty()) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 "shell_execute requires a non-empty command"));
  }
  SshCommandRequest request{
      .command = *command,
      .working_directory = {},
      .timeout = std::chrono::milliseconds{30'000},
      .maximum_output_bytes = 4U * 1024U * 1024U,
  };
  if (const auto *cwd = json::AsString(json::Find(*object, "cwd")))
    request.working_directory = *cwd;
  if (const auto *timeout = json::Find(*object, "timeoutMs")) {
    std::optional<std::int64_t> value;
    if (const auto *integer = std::get_if<std::int64_t>(timeout))
      value = *integer;
    else if (const auto *number = std::get_if<double>(timeout))
      value = static_cast<std::int64_t>(*number);
    if (!value) {
      return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                   "shell_execute timeoutMs must be a number"));
    }
    request.timeout = std::chrono::milliseconds{
        std::clamp(*value, std::int64_t{1'000}, std::int64_t{300'000})};
  }
  return request;
}

} // namespace

SshToolRegistry::SshToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<SshSettingsService> ssh_settings,
    std::shared_ptr<SshExecutionService> execution,
    const ToolTextLanguage language)
    : settings_(std::move(settings)), ssh_settings_(std::move(ssh_settings)),
      execution_(std::move(execution)), language_(language) {
  if (!settings_ || !ssh_settings_ || !execution_)
    throw std::invalid_argument(
        "SshToolRegistry requires execution and settings services");
}

huxerui::Task<std::expected<void, ToolRegistryError>>
SshToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  std::optional<domain::SshConfig> next_config;
  std::vector<RegisteredTool> next_tools;
  if (settings->mode == domain::McpExecutionMode::ssh &&
      ShellEnabled(*settings)) {
    auto config = co_await ssh_settings_->Load();
    if (!config) {
      co_return std::unexpected(
          Error(ToolRegistryErrorCode::load_failed, config.error().message));
    }
    next_config = domain::NormalizeSshConfig(std::move(*config));
    next_tools.push_back(RegisteredTool{
        .name = std::string{kSshShellToolName},
        .description =
            "Execute a shell command through the configured SSH target. The "
            "command requires user confirmation before execution.",
        .parameters_json = std::string{kShellSchema},
        .allowed_in_read_only = true,
        .permanent_grant_arguments = {{.name = "command", .required = true},
                                      {.name = "cwd", .required = false}},
        .agent_category = AgentToolCategory::system,
        .category = "shell",
        .presentation =
            {.english_name = "Run SSH command",
             .english_description =
                 "Execute a shell command on the configured SSH host.",
             .chinese_name = "运行 SSH 命令",
             .chinese_description = "在已配置的 SSH 主机上执行 Shell 命令。"},
    });
  }
  active_config_ = std::move(next_config);
  tools_ = std::move(next_tools);
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool> SshToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
SshToolRegistry::Invoke(std::string name, std::string arguments_json) {
  if (name != kSshShellToolName) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown SSH tool: " + name));
  }
  // Say what is actually missing. Reporting "not configured" to someone who has
  // just saved the form sends them back to a screen that is already filled in,
  // when the real cause is usually the missing password or key.
  const auto gap_message = [this]() -> std::optional<ToolTextKey> {
    if (!active_config_)
      return ToolTextKey::tool_ssh_not_configured;
    switch (active_config_->Gap()) {
    case domain::SshConfigGap::none:
      return std::nullopt;
    case domain::SshConfigGap::host:
      return ToolTextKey::tool_ssh_missing_host;
    case domain::SshConfigGap::port:
      return ToolTextKey::tool_ssh_missing_port;
    case domain::SshConfigGap::username:
      return ToolTextKey::tool_ssh_missing_username;
    case domain::SshConfigGap::credentials:
      return ToolTextKey::tool_ssh_missing_credentials;
    }
    return ToolTextKey::tool_ssh_not_configured;
  }();
  if (gap_message.has_value()) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::unavailable,
              std::string{ToolText(*gap_message, {}, language_)}));
  }
  auto arguments = ParseArguments(arguments_json);
  if (!arguments)
    co_return std::unexpected(std::move(arguments.error()));
  auto invoked =
      co_await execution_->Execute(*active_config_, std::move(*arguments));
  if (!invoked) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invocation_failed,
                                    std::move(invoked.error().message)));
  }
  co_return ToolInvocationResult{
      .content =
          ShellResultContent(invoked->standard_output, invoked->standard_error),
      .error = invoked->exit_status != 0,
  };
}

} // namespace linecode::application
