#include "application/terminal_provider_tool_registry.h"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <functional>
#include <stdexcept>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

using namespace std::chrono_literals;
namespace json = infrastructure::archive_json;

constexpr std::string_view kShellSchema =
    R"({"properties":{"command":{"description":"The shell command to execute","type":"string"},"cwd":{"description":"Optional working directory; the command runs after cd into it","type":"string"},"timeoutMs":{"description":"Optional timeout in milliseconds, default 30000, max 300000","type":"number"}},"required":["command"],"type":"object"})";

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

bool ShellEnabled(const domain::McpExecutionSettings &settings) {
  const auto found = std::ranges::find(
      settings.groups, std::string_view{"shell"},
      [](const domain::McpToolGroupState &group) {
        return std::string_view{group.id};
      });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

struct ParsedShellArguments final {
  TerminalShellRequest request;
};

std::expected<ParsedShellArguments, ToolRegistryError>
ParseShellArguments(std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!object) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 "shell_execute arguments must be a JSON object"));
  }
  const auto *command = json::AsString(json::Find(*object, "command"));
  if (!command || command->empty()) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 "shell_execute requires a non-empty command"));
  }

  TerminalShellRequest request{.command = *command,
                               .working_directory = {},
                               .timeout_milliseconds = 30'000};
  if (const auto *cwd = json::AsString(json::Find(*object, "cwd")))
    request.working_directory = *cwd;
  if (const auto *timeout = json::Find(*object, "timeoutMs")) {
    std::optional<std::int64_t> milliseconds;
    if (const auto *integer = std::get_if<std::int64_t>(timeout))
      milliseconds = *integer;
    else if (const auto *number = std::get_if<double>(timeout))
      milliseconds = static_cast<std::int64_t>(*number);
    if (!milliseconds) {
      return std::unexpected(Error(
          ToolRegistryErrorCode::invalid_arguments,
          "shell_execute timeoutMs must be a number"));
    }
    request.timeout_milliseconds =
        std::clamp(*milliseconds, std::int64_t{1'000},
                   std::int64_t{300'000});
  }
  return ParsedShellArguments{.request = std::move(request)};
}

template <class Value, class Start>
huxerui::Task<TerminalProviderResult<Value>> AwaitGateway(Start start) {
  auto result = std::make_shared<
      std::optional<TerminalProviderResult<Value>>>();
  std::invoke(std::move(start), [result](TerminalProviderResult<Value> value) {
    result->emplace(std::move(value));
  });
  while (!result->has_value())
    co_await huxerui::Delay(5ms);
  co_return std::move(**result);
}

std::string EncodeShellResult(const TerminalShellResult &result) {
  return json::Serialize(json::Object{
      {"exit_code", static_cast<std::int64_t>(result.exit_code)},
      {"stdout", result.standard_output},
      {"stderr", result.standard_error},
  });
}

} // namespace

TerminalProviderToolRegistry::TerminalProviderToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<TerminalProviderStore> providers,
    std::shared_ptr<TerminalProviderGateway> gateway)
    : settings_(std::move(settings)), providers_(std::move(providers)),
      gateway_(std::move(gateway)) {
  if (!settings_ || !providers_ || !gateway_)
    throw std::invalid_argument(
        "TerminalProviderToolRegistry requires settings, store and gateway");
}

huxerui::Task<std::expected<void, ToolRegistryError>>
TerminalProviderToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }

  std::optional<domain::TerminalProviderConfig> next_provider;
  std::vector<RegisteredTool> next_tools;
  if (settings->mode == domain::McpExecutionMode::terminal_provider &&
      ShellEnabled(*settings)) {
    auto providers = co_await providers_->ListTerminalProviders();
    if (!providers) {
      co_return std::unexpected(
          Error(ToolRegistryErrorCode::load_failed,
                std::move(providers.error().message)));
    }
    const auto enabled =
        std::ranges::find(*providers, true,
                          &domain::TerminalProviderConfig::enabled);
    if (enabled != providers->end()) {
      next_provider = *enabled;
      next_tools.push_back(RegisteredTool{
          .name = std::string{kTerminalShellToolName},
          .description =
              "Execute a shell command via the current execution target: SSH "
              "mode uses SSH, terminal provider mode uses IPC. The command "
              "requires user confirmation before execution.",
          .parameters_json = std::string{kShellSchema},
          // Legacy read-only policy permits shell on an isolated remote or
          // terminal-provider target, while local mutation tools remain denied.
          .allowed_in_read_only = true,
          .permanent_grant_supported = true,
      });
    }
  }

  active_provider_ = std::move(next_provider);
  tools_ = std::move(next_tools);
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool>
TerminalProviderToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
TerminalProviderToolRegistry::Invoke(std::string name,
                                     std::string arguments_json) {
  if (name != kTerminalShellToolName) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::unknown_tool,
              "Unknown terminal-provider tool: " + name));
  }
  if (!active_provider_) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::unavailable,
        "No enabled terminal provider is selected for shell execution"));
  }
  auto arguments = ParseShellArguments(arguments_json);
  if (!arguments)
    co_return std::unexpected(std::move(arguments.error()));

  auto invoked = co_await AwaitGateway<TerminalShellResult>(
      [gateway = gateway_, provider = *active_provider_,
       request = std::move(arguments->request)](auto completion) mutable {
        gateway->ExecuteShell(std::move(provider), std::move(request),
                              std::move(completion));
      });
  if (!invoked) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invocation_failed,
                                    std::move(invoked.error().message)));
  }
  co_return ToolInvocationResult{
      .content = EncodeShellResult(*invoked),
      .error = invoked->exit_code != 0,
  };
}

} // namespace linecode::application
