#include "application/user_agreement.h"

#include "gtest_support.h"
#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

namespace {

using linecode::application::AsyncSettingsStore;
using linecode::application::SettingsResult;
using linecode::application::SettingsStoreError;
using linecode::application::UserAgreement;

class MemorySettings final : public AsyncSettingsStore {
public:
  huxerui::Task<SettingsResult<std::string>>
  GetString(std::string, std::string fallback) override {
    co_return std::move(fallback);
  }

  huxerui::Task<SettingsResult<bool>>
  GetBoolean(std::string key, bool fallback) override {
    if (fail_reads)
      co_return std::unexpected(SettingsStoreError{"read failed"});
    const auto found = booleans.find(key);
    co_return found == booleans.end() ? fallback : found->second;
  }

  huxerui::Task<SettingsResult<std::int64_t>>
  GetInteger(std::string key, std::int64_t fallback) override {
    if (fail_reads)
      co_return std::unexpected(SettingsStoreError{"read failed"});
    const auto found = integers.find(key);
    co_return found == integers.end() ? fallback : found->second;
  }

  huxerui::Task<SettingsResult<void>>
  SetString(std::string, std::string) override {
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>>
  SetBoolean(std::string key, bool value) override {
    if (fail_writes)
      co_return std::unexpected(SettingsStoreError{"write failed"});
    booleans.insert_or_assign(std::move(key), value);
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>>
  SetInteger(std::string key, std::int64_t value) override {
    if (fail_writes)
      co_return std::unexpected(SettingsStoreError{"write failed"});
    integers.insert_or_assign(std::move(key), value);
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> Remove(std::string) override {
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> ClearLineCodeSettings() override {
    booleans.clear();
    integers.clear();
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return std::map<std::string, std::string, std::less<>>{};
  }

  std::map<std::string, bool, std::less<>> booleans;
  std::map<std::string, std::int64_t, std::less<>> integers;
  bool fail_reads{};
  bool fail_writes{};
};

struct Scenario final {
  std::shared_ptr<MemorySettings> settings;
  std::shared_ptr<UserAgreement> agreement;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      EXPECT_EXPRESSION(co_await scenario->agreement->ShouldShow());
      EXPECT_EXPRESSION(co_await scenario->agreement->Accept());
      EXPECT_EXPRESSION(!co_await scenario->agreement->ShouldShow());

      scenario->settings->integers["@linecode_user_agreement_version"] = 0;
      EXPECT_EXPRESSION(co_await scenario->agreement->ShouldShow());

      scenario->settings->fail_reads = true;
      EXPECT_EXPRESSION(co_await scenario->agreement->ShouldShow());
      scenario->settings->fail_reads = false;
      scenario->settings->fail_writes = true;
      EXPECT_EXPRESSION(!co_await scenario->agreement->Accept());
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("user-agreement-probe");
}

} // namespace

TEST(user_agreement_tests, LegacySuite) {
  active = std::make_shared<Scenario>();
  active->settings = std::make_shared<MemorySettings>();
  active->agreement = std::make_shared<UserAgreement>(active->settings);
  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
