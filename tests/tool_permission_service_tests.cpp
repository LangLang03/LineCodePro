#include <cassert>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/tool_permission_service.h"

namespace {

using namespace linecode;

class MemorySettings final : public application::AsyncSettingsStore {
public:
  huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override {
    const auto found = values.find(key);
    co_return found == values.end() ? std::move(fallback) : found->second;
  }
  huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string, bool fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string, std::int64_t fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<void>>
  SetString(std::string key, std::string value) override {
    values.insert_or_assign(std::move(key), std::move(value));
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetBoolean(std::string, bool) override {
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetInteger(std::string, std::int64_t) override {
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  Remove(std::string key) override {
    values.erase(key);
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  ClearLineCodeSettings() override {
    values.clear();
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return values;
  }

  std::map<std::string, std::string, std::less<>> values;
};

struct Scenario final {
  std::shared_ptr<MemorySettings> store;
  std::shared_ptr<application::ToolPermissionService> permissions;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      const application::RegisteredTool shell{
          .name = "shell_execute",
          .description = "shell",
          .parameters_json = R"({"type":"object"})",
          .allowed_in_read_only = true,
          .permanent_grant_supported = true,
      };
      const application::RegisteredTool write{
          .name = "file_write",
          .description = "write",
          .parameters_json = R"({"type":"object"})",
      };
      const application::CompletionToolCall call{
          .id = "call-1",
          .name = "shell_execute",
          .arguments_json =
              R"({"command":"echo hi","cwd":"  /tmp  "})",
      };

      auto state = co_await scenario->permissions->Load();
      assert(state && state->mode == domain::ToolPermissionMode::automatic);
      auto decision = co_await scenario->permissions->Evaluate(
          shell, call, "/workspace");
      assert(decision && *decision ==
                             application::ToolPermissionDecision::execute);

      assert(co_await scenario->permissions->SetMode(
          domain::ToolPermissionMode::confirm));
      decision = co_await scenario->permissions->Evaluate(
          shell, call, "/workspace");
      assert(decision && *decision ==
                             application::ToolPermissionDecision::review);
      assert(co_await scenario->permissions->RememberPermanentGrant(
          shell, call, "/workspace"));
      assert(scenario->store->values.at("@linecode_command_grants_v1") ==
             R"(["7cfd9c6d86143c042be688a69dd04fa5132cccac02493c524fe3b1c97f778c4c"])"
      );
      decision = co_await scenario->permissions->Evaluate(
          shell, call, "/workspace");
      assert(decision && *decision ==
                             application::ToolPermissionDecision::execute);

      assert(co_await scenario->permissions->SetMode(
          domain::ToolPermissionMode::read_only));
      decision = co_await scenario->permissions->Evaluate(
          shell, call, "/workspace");
      assert(decision && *decision ==
                             application::ToolPermissionDecision::execute);
      decision = co_await scenario->permissions->Evaluate(
          write,
          application::CompletionToolCall{
              .id = "call-2", .name = "file_write", .arguments_json = "{}"},
          "/workspace");
      assert(decision && *decision == application::ToolPermissionDecision::deny);

      assert(co_await scenario->permissions->ClearPermanentGrants());
      state = co_await scenario->permissions->Load();
      assert(state && !state->has_permanent_grants);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("tool-permission-probe");
}

} // namespace

int main() {
  active = std::make_shared<Scenario>();
  active->store = std::make_shared<MemorySettings>();
  active->permissions =
      std::make_shared<application::ToolPermissionService>(active->store);
  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
