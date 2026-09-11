#include <cassert>
#include <cstddef>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/composite_tool_registry.h"
#include "application/terminal_provider_tool_registry.h"

namespace {

using namespace linecode;

class StubSettings final
    : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override {
    co_return value;
  }

  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override {
    value.mode = mode;
    co_return application::SettingsResult<void>{};
  }

  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string id,
                      bool enabled) override {
    const auto found =
        std::ranges::find(value.groups, id,
                          &domain::McpToolGroupState::id);
    if (found != value.groups.end())
      found->enabled = enabled;
    co_return application::SettingsResult<void>{};
  }

  domain::McpExecutionSettings value{
      domain::DefaultMcpExecutionSettings(
          domain::McpExecutionMode::terminal_provider)};
};

class StubProviders final : public application::TerminalProviderStore {
public:
  huxerui::Task<application::TerminalProviderResult<
      std::vector<domain::TerminalProviderConfig>>>
  ListTerminalProviders() override {
    co_return values;
  }

  huxerui::Task<application::TerminalProviderResult<
      domain::TerminalProviderConfig>>
  SaveTerminalProvider(domain::TerminalProviderConfig value) override {
    values.push_back(value);
    co_return value;
  }

  huxerui::Task<application::TerminalProviderResult<void>>
  SetTerminalProviderEnabled(std::string, bool) override {
    co_return application::TerminalProviderResult<void>{};
  }

  huxerui::Task<application::TerminalProviderResult<void>>
  DeleteTerminalProvider(std::string) override {
    co_return application::TerminalProviderResult<void>{};
  }

  std::vector<domain::TerminalProviderConfig> values{{
      .id = "provider-1",
      .enabled = true,
      .provider_type = "terminal",
      .name = "Terminal Provider",
      .package_name = "cn.lineai.terminalprovider",
      .service_class =
          "cn.lineai.terminalprovider.TerminalProviderService",
  }};
};

class StubGateway final : public application::TerminalProviderGateway {
public:
  void Scan(Completion completion) override { completion(scanned); }

  void ExecuteShell(domain::TerminalProviderConfig provider,
                    application::TerminalShellRequest request,
                    ShellCompletion completion) override {
    invoked_provider = std::move(provider);
    invoked_request = std::move(request);
    completion(application::TerminalShellResult{
        .exit_code = 0,
        .standard_output = "fixed terminal output\n",
        .standard_error = {},
    });
  }

  void ReadFile(domain::TerminalProviderConfig, std::string,
                BytesCompletion completion) override {
    completion(std::vector<std::byte>{});
  }
  void WriteFile(domain::TerminalProviderConfig, std::string,
                 std::vector<std::byte>, VoidCompletion completion) override {
    completion(application::TerminalProviderResult<void>{});
  }
  void DeleteFile(domain::TerminalProviderConfig, std::string,
                  VoidCompletion completion) override {
    completion(application::TerminalProviderResult<void>{});
  }
  void ListDirectory(domain::TerminalProviderConfig, std::string,
                     TextCompletion completion) override {
    completion(std::string{"[]"});
  }
  void GetProviderInfo(domain::TerminalProviderConfig,
                       InfoCompletion completion) override {
    completion(application::TerminalProviderInfo{
        .provider_type = "terminal",
        .raw_json = R"({"home":"/workspace"})",
        .home_path = "/workspace",
    });
  }
  void FileExists(domain::TerminalProviderConfig, std::string,
                  BooleanCompletion completion) override {
    completion(true);
  }
  void FileSize(domain::TerminalProviderConfig, std::string,
                SizeCompletion completion) override {
    completion(std::int64_t{0});
  }
  void ReadFileChunk(domain::TerminalProviderConfig, std::string,
                     std::int64_t, std::int32_t,
                     BytesCompletion completion) override {
    completion(std::vector<std::byte>{});
  }
  void WriteFileChunk(domain::TerminalProviderConfig, std::string,
                      std::int64_t, std::vector<std::byte>,
                      VoidCompletion completion) override {
    completion(application::TerminalProviderResult<void>{});
  }
  void GetFileSize(domain::TerminalProviderConfig, std::string,
                   SizeCompletion completion) override {
    completion(std::int64_t{0});
  }

  std::vector<domain::ScannedTerminalProvider> scanned;
  std::optional<domain::TerminalProviderConfig> invoked_provider;
  std::optional<application::TerminalShellRequest> invoked_request;
};

class StaticRegistry final : public application::ToolRegistry {
public:
  explicit StaticRegistry(std::string name)
      : tools_{{.name = std::move(name),
                .description = "static",
                .parameters_json = R"({"type":"object"})"}} {}

  huxerui::Task<std::expected<void, application::ToolRegistryError>>
  Refresh() override {
    co_return std::expected<void, application::ToolRegistryError>{};
  }

  std::span<const application::RegisteredTool>
  Tools() const noexcept override {
    return tools_;
  }

  huxerui::Task<std::expected<application::ToolInvocationResult,
                              application::ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override {
    calls.push_back(name + ":" + arguments_json);
    co_return application::ToolInvocationResult{.content = "static result"};
  }

  std::vector<std::string> calls;

private:
  std::vector<application::RegisteredTool> tools_;
};

struct Scenario final {
  std::shared_ptr<StubSettings> settings;
  std::shared_ptr<StubProviders> providers;
  std::shared_ptr<StubGateway> gateway;
  std::shared_ptr<StaticRegistry> static_tools;
  std::shared_ptr<application::TerminalProviderToolRegistry> terminal;
  std::shared_ptr<application::CompositeToolRegistry> composite;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto refreshed = co_await scenario->composite->Refresh();
      assert(refreshed);
      assert(scenario->composite->Tools().size() == 2U);
      assert(scenario->composite->Tools()[0].name == "static_tool");
      assert(scenario->composite->Tools()[1].name ==
             application::kTerminalShellToolName);

      auto invoked = co_await scenario->composite->Invoke(
          std::string{application::kTerminalShellToolName},
          R"({"command":"pwd","cwd":"/workspace","timeoutMs":12.5})");
      assert(invoked);
      assert(!invoked->error);
      assert(invoked->content.contains("fixed terminal output"));
      assert(scenario->gateway->invoked_provider);
      assert(scenario->gateway->invoked_request);
      assert(scenario->gateway->invoked_request->command == "pwd");
      assert(scenario->gateway->invoked_request->working_directory ==
             "/workspace");
      assert(scenario->gateway->invoked_request->timeout_milliseconds ==
             1'000);

      auto invalid = co_await scenario->composite->Invoke(
          std::string{application::kTerminalShellToolName}, "{}");
      assert(!invalid);
      assert(invalid.error().code ==
             application::ToolRegistryErrorCode::invalid_arguments);

      scenario->settings->value.mode = domain::McpExecutionMode::local;
      refreshed = co_await scenario->composite->Refresh();
      assert(refreshed);
      assert(scenario->composite->Tools().size() == 1U);

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("runtime-tool-registry-probe");
}

} // namespace

int main() {
  active = std::make_shared<Scenario>();
  active->settings = std::make_shared<StubSettings>();
  active->providers = std::make_shared<StubProviders>();
  active->gateway = std::make_shared<StubGateway>();
  active->static_tools = std::make_shared<StaticRegistry>("static_tool");
  active->terminal =
      std::make_shared<application::TerminalProviderToolRegistry>(
          active->settings, active->providers, active->gateway);
  active->composite = std::make_shared<application::CompositeToolRegistry>(
      std::vector<std::shared_ptr<application::ToolRegistry>>{
          active->static_tools, active->terminal});

  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
