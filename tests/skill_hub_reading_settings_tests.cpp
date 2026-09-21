#include "application/skill_hub_reading_settings.h"

#include "gtest_support.h"
#include <cmath>
#include <cstdint>
#include <limits>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

namespace {

using linecode::application::AsyncSettingsStore;
using linecode::application::SettingsResult;
using linecode::application::SkillHubReadingSettings;

bool Near(const float left, const float right) {
  return std::abs(left - right) < 0.0001F;
}

class MemorySettings final : public AsyncSettingsStore {
public:
  huxerui::Task<SettingsResult<std::string>>
  GetString(std::string, std::string fallback) override {
    co_return std::move(fallback);
  }

  huxerui::Task<SettingsResult<bool>>
  GetBoolean(std::string, const bool fallback) override {
    co_return fallback;
  }

  huxerui::Task<SettingsResult<std::int64_t>>
  GetInteger(std::string key, const std::int64_t fallback) override {
    const auto found = integers.find(key);
    co_return found == integers.end() ? fallback : found->second;
  }

  huxerui::Task<SettingsResult<void>>
  SetString(std::string, std::string) override {
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> SetBoolean(std::string, bool) override {
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>>
  SetInteger(std::string key, const std::int64_t value) override {
    integers.insert_or_assign(std::move(key), value);
    ++integer_writes;
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> Remove(std::string key) override {
    integers.erase(key);
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> ClearLineCodeSettings() override {
    integers.clear();
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return std::map<std::string, std::string, std::less<>>{};
  }

  std::map<std::string, std::int64_t, std::less<>> integers;
  int integer_writes{};
};

struct Scenario final {
  std::shared_ptr<MemorySettings> store;
  std::shared_ptr<SkillHubReadingSettings> reading;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      const float migrated = co_await scenario->reading->LoadScale(1.25F);
      EXPECT_EXPRESSION(Near(migrated, 1.25F));
      EXPECT_EXPRESSION(scenario->store->integers.at("markdown_text_scale") == 1'250);
      EXPECT_EXPRESSION(scenario->store->integer_writes == 1);

      const float preserved = co_await scenario->reading->LoadScale(0.75F);
      EXPECT_EXPRESSION(Near(preserved, 1.25F));
      EXPECT_EXPRESSION(scenario->store->integer_writes == 1);

      co_await scenario->reading->SaveScale(9.0F);
      EXPECT_EXPRESSION(scenario->store->integers.at("markdown_text_scale") == 1'600);
      const float clamped = co_await scenario->reading->LoadScale(0.75F);
      EXPECT_EXPRESSION(Near(clamped, 1.6F));

      scenario->store->integers.clear();
      const int writes_before_default = scenario->store->integer_writes;
      const float defaulted = co_await scenario->reading->LoadScale();
      EXPECT_EXPRESSION(Near(defaulted, 1.0F));
      EXPECT_EXPRESSION(scenario->store->integer_writes == writes_before_default);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("skill-hub-reading-settings-probe");
}

} // namespace

TEST(skill_hub_reading_settings_tests, LegacySuite) {
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(0.0F) == 0.5F);
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(0.5F) == 0.5F);
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(1.25F) == 1.25F);
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(1.6F) == 1.6F);
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(2.0F) == 1.6F);
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(
             std::numeric_limits<float>::infinity()) == 1.0F);
  EXPECT_EXPRESSION(SkillHubReadingSettings::NormalizeScale(
             std::numeric_limits<float>::quiet_NaN()) == 1.0F);

  active = std::make_shared<Scenario>();
  active->store = std::make_shared<MemorySettings>();
  active->reading = std::make_shared<SkillHubReadingSettings>(active->store);
  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
