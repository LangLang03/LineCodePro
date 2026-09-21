#include "application/theme_settings_migration.h"

#include "gtest_support.h"
#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/theme_settings.h"

namespace {

using linecode::application::AsyncSettingsStore;
using linecode::application::SettingsResult;
using linecode::application::SettingsStore;
using linecode::application::SettingsStoreError;
using linecode::application::ThemeSettingsKeys;
using linecode::application::ThemeSettingsMigration;

class MemoryThemeStore final : public SettingsStore {
public:
  std::optional<std::string> Read(std::string_view key) const override {
    const auto found = values.find(key);
    return found == values.end() ? std::nullopt
                                 : std::optional<std::string>{found->second};
  }

  void Write(std::string_view key, std::string value) override {
    values.insert_or_assign(std::string{key}, std::move(value));
  }

  std::map<std::string, std::string, std::less<>> values;
};

class MemoryAsyncStore final : public AsyncSettingsStore {
public:
  huxerui::Task<SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override {
    if (fail_reads)
      co_return std::unexpected(SettingsStoreError{"read failed"});
    const auto found = strings.find(key);
    co_return found == strings.end() ? std::move(fallback) : found->second;
  }

  huxerui::Task<SettingsResult<bool>>
  GetBoolean(std::string, bool fallback) override {
    co_return fallback;
  }

  huxerui::Task<SettingsResult<std::int64_t>>
  GetInteger(std::string, std::int64_t fallback) override {
    co_return fallback;
  }

  huxerui::Task<SettingsResult<void>>
  SetString(std::string key, std::string value) override {
    strings.insert_or_assign(std::move(key), std::move(value));
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> SetBoolean(std::string, bool) override {
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>>
  SetInteger(std::string, std::int64_t) override {
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> Remove(std::string key) override {
    strings.erase(key);
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>> ClearLineCodeSettings() override {
    strings.clear();
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return strings;
  }

  std::map<std::string, std::string, std::less<>> strings;
  bool fail_reads{};
};

struct Scenario final {
  std::shared_ptr<MemoryThemeStore> destination;
  std::shared_ptr<MemoryAsyncStore> source;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      scenario->source->strings[std::string{ThemeSettingsKeys::mode}] = "dark";
      scenario->source->strings[std::string{ThemeSettingsKeys::custom_colors}] =
          R"({"background":"#010203"})";

      auto imported = co_await ThemeSettingsMigration::ImportIfMissing(
          scenario->destination, scenario->source);
      EXPECT_EXPRESSION(imported && *imported);
      EXPECT_EXPRESSION(scenario->destination->Read(ThemeSettingsKeys::mode) == "dark");
      EXPECT_EXPRESSION(scenario->destination->Read(ThemeSettingsKeys::custom_colors) ==
             R"({"background":"#010203"})");

      scenario->source->strings[std::string{ThemeSettingsKeys::mode}] = "light";
      imported = co_await ThemeSettingsMigration::ImportIfMissing(
          scenario->destination, scenario->source);
      EXPECT_EXPRESSION(imported && !*imported);
      EXPECT_EXPRESSION(scenario->destination->Read(ThemeSettingsKeys::mode) == "dark");

      auto failing_destination = std::make_shared<MemoryThemeStore>();
      scenario->source->fail_reads = true;
      auto failed = co_await ThemeSettingsMigration::ImportIfMissing(
          failing_destination, scenario->source);
      EXPECT_EXPRESSION(!failed);
      EXPECT_EXPRESSION(failing_destination->values.empty());
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("theme-settings-migration-probe");
}

} // namespace

TEST(theme_settings_migration_tests, LegacySuite) {
  active = std::make_shared<Scenario>();
  active->destination = std::make_shared<MemoryThemeStore>();
  active->source = std::make_shared<MemoryAsyncStore>();
  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
