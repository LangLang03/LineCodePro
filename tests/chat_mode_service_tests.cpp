#include "gtest_support.h"
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/chat_mode_service.h"
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
  std::shared_ptr<application::ChatModeService> modes;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto state = co_await scenario->modes->Load();
      EXPECT_EXPRESSION(state && state->chat_mode == domain::ChatMode::agent);
      EXPECT_EXPRESSION(state->permission_mode ==
             domain::ToolPermissionMode::automatic);

      state = co_await scenario->modes->SetChatMode(domain::ChatMode::chat);
      EXPECT_EXPRESSION(state && state->chat_mode == domain::ChatMode::chat);
      EXPECT_EXPRESSION(state->permission_mode == domain::ToolPermissionMode::read_only);
      EXPECT_EXPRESSION(scenario->store->values.at("@linecode_chat_mode") == "chat");

      state = co_await scenario->modes->SetChatMode(domain::ChatMode::plan);
      EXPECT_EXPRESSION(state && state->chat_mode == domain::ChatMode::plan);
      EXPECT_EXPRESSION(state->permission_mode ==
             domain::ToolPermissionMode::automatic);

      state = co_await scenario->modes->SetPermissionMode(
          domain::ToolPermissionMode::read_only);
      EXPECT_EXPRESSION(state && state->chat_mode == domain::ChatMode::chat);

      state = co_await scenario->modes->SetPermissionMode(
          domain::ToolPermissionMode::confirm);
      EXPECT_EXPRESSION(state && state->chat_mode == domain::ChatMode::agent);
      EXPECT_EXPRESSION(state->permission_mode == domain::ToolPermissionMode::confirm);

      scenario->store->values["@linecode_chat_mode"] = "control";
      state = co_await scenario->modes->Load();
      EXPECT_EXPRESSION(state && state->chat_mode == domain::ChatMode::agent);
      EXPECT_EXPRESSION(scenario->store->values.at("@linecode_chat_mode") == "agent");
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("chat-mode-probe");
}
} // namespace

TEST(chat_mode_service_tests, LegacySuite) {
  active = std::make_shared<Scenario>();
  active->store = std::make_shared<MemorySettings>();
  auto permissions =
      std::make_shared<linecode::application::ToolPermissionService>(
          active->store);
  active->modes = std::make_shared<linecode::application::ChatModeService>(
      active->store, std::move(permissions));
  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
