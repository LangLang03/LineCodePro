#include <array>
#include "gtest_support.h"
#include <chrono>
#include <filesystem>
#include <iostream>
#include <memory>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

#include <sqlite3.h>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/theme_settings.h"
#include "application/theme_settings_migration.h"
#include "domain/model_config.h"
#include "domain/theme_palette.h"
#include "infrastructure/sqlite_model_store.h"
#include "infrastructure/sqlite_settings_store.h"
#include "infrastructure/theme_file_settings_store.h"

namespace {

namespace application = linecode::application;
namespace domain = linecode::domain;
namespace infrastructure = linecode::infrastructure;

constexpr std::string_view kLegacyModelId{"legacy-selected-model"};
constexpr std::string_view kLegacyCustomColors{
    R"({"bg":"#112233","accent":"#445566","text":"#FAFAFA"})"};

class TemporaryDirectory final {
public:
  TemporaryDirectory() {
    const auto nonce =
        std::chrono::steady_clock::now().time_since_epoch().count();
    path_ = std::filesystem::temp_directory_path() /
            ("linecode-legacy-upgrade-" + std::to_string(nonce));
    if (!std::filesystem::create_directories(path_))
      throw std::runtime_error("cannot create temporary upgrade directory");
  }

  ~TemporaryDirectory() {
    std::error_code ignored;
    std::filesystem::remove_all(path_, ignored);
  }

  [[nodiscard]] const std::filesystem::path &Path() const noexcept {
    return path_;
  }

private:
  std::filesystem::path path_;
};

class Database final {
public:
  explicit Database(const std::filesystem::path &path) {
    const auto utf8_path = path.string();
    if (sqlite3_open(utf8_path.c_str(), &database_) != SQLITE_OK) {
      const std::string message =
          database_ == nullptr ? "cannot open SQLite database"
                               : sqlite3_errmsg(database_);
      if (database_ != nullptr)
        sqlite3_close(database_);
      database_ = nullptr;
      throw std::runtime_error(message);
    }
  }

  ~Database() {
    if (database_ != nullptr)
      sqlite3_close(database_);
  }

  Database(const Database &) = delete;
  Database &operator=(const Database &) = delete;

  void Execute(std::string_view sql) {
    char *error{};
    const std::string statement{sql};
    if (sqlite3_exec(database_, statement.c_str(), nullptr, nullptr, &error) ==
        SQLITE_OK) {
      return;
    }
    const std::string message =
        error == nullptr ? sqlite3_errmsg(database_) : std::string{error};
    sqlite3_free(error);
    throw std::runtime_error(message);
  }

  [[nodiscard]] std::vector<std::string>
  Columns(std::string_view table) const {
    sqlite3_stmt *statement{};
    const std::string sql = "PRAGMA table_info(" + std::string{table} + ")";
    if (sqlite3_prepare_v2(database_, sql.c_str(), -1, &statement, nullptr) !=
        SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(database_));
    }

    std::vector<std::string> columns;
    while (sqlite3_step(statement) == SQLITE_ROW) {
      const auto *text = sqlite3_column_text(statement, 1);
      columns.emplace_back(text == nullptr
                               ? ""
                               : reinterpret_cast<const char *>(text));
    }
    sqlite3_finalize(statement);
    return columns;
  }

private:
  sqlite3 *database_{};
};

void CreateLegacyDatabase(const std::filesystem::path &path) {
  Database database(path);
  database.Execute(R"sql(
CREATE TABLE model_configs (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  protocol_type TEXT NOT NULL,
  provider_label TEXT NOT NULL,
  base_url TEXT,
  api_key TEXT,
  model_id TEXT NOT NULL,
  selected INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'string',
  updated_at INTEGER NOT NULL
);
INSERT INTO model_configs
  (id, name, protocol_type, provider_label, base_url, api_key, model_id,
   selected, raw_json, created_at, updated_at)
VALUES
  ('legacy-selected-model', 'Legacy Model', 'OPENAI_COMPATIBLE', 'Legacy',
   'https://legacy.example/v1', 'legacy-key', 'legacy-model-id', 1, NULL,
   1000, 2000);
INSERT INTO settings (key, value, type, updated_at)
VALUES ('@lineai_theme_mode', 'custom', 'string', 2000);
INSERT INTO settings (key, value, type, updated_at)
VALUES (
  '@lineai_custom_theme_colors',
  '{"bg":"#112233","accent":"#445566","text":"#FAFAFA"}',
  'string',
  2000
);
)sql");

  const auto legacy_columns = database.Columns("model_configs");
  constexpr std::array new_columns{
      "tool_call_limit", "compression_model_enabled",
      "compression_model_auto", "compression_model_id", "context_size"};
  for (const std::string_view column : new_columns)
    EXPECT_EXPRESSION(!std::ranges::contains(legacy_columns, column));
}

class FixedSystemTheme final : public application::SystemThemeSource {
public:
  [[nodiscard]] bool IsDarkModeEnabled() const override { return false; }
};

struct UpgradeScenario final {
  std::shared_ptr<infrastructure::SqliteModelStore> models;
  std::shared_ptr<infrastructure::SQLiteSettingsStore> legacy_settings;
  std::shared_ptr<infrastructure::ThemeFileSettingsStore> theme_destination;
  bool done{};
  std::string failure;
};

std::shared_ptr<UpgradeScenario> active_scenario;

void Fail(const std::shared_ptr<UpgradeScenario> &scenario,
          std::string message) {
  scenario->failure = std::move(message);
  scenario->done = true;
}

huxerui::View UpgradeProbe() {
  const auto scenario = active_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto models = co_await scenario->models->List();
      if (!models) {
        Fail(scenario, "model list failed: " + models.error().message);
        co_return;
      }
      if (models->size() != 1U) {
        Fail(scenario, "legacy model count was not preserved");
        co_return;
      }
      const auto &model = models->front();
      if (model.id != kLegacyModelId || model.name != "Legacy Model" ||
          model.model_id != "legacy-model-id" ||
          model.tool_call_limit != domain::ModelConfig::default_tool_call_limit ||
          model.compression_model_enabled || !model.compression_model_auto ||
          !model.compression_model_id.empty() ||
          model.context_size != domain::ModelConfig::context_size_unset) {
        Fail(scenario, "legacy model or migrated column defaults changed");
        co_return;
      }

      auto selected_model_id = co_await scenario->models->SelectedId();
      if (!selected_model_id || *selected_model_id != kLegacyModelId) {
        Fail(scenario, selected_model_id
                           ? "selected model id was not preserved"
                           : "selected model read failed: " +
                                 selected_model_id.error().message);
        co_return;
      }

      auto migrated = co_await application::ThemeSettingsMigration::ImportIfMissing(
          scenario->theme_destination, scenario->legacy_settings);
      if (!migrated || !*migrated) {
        Fail(scenario, migrated ? "legacy theme was not imported"
                                : "theme migration failed: " +
                                      migrated.error().message);
        co_return;
      }
      const auto copied_colors = scenario->theme_destination->Read(
          application::ThemeSettingsKeys::custom_colors);
      if (!copied_colors || *copied_colors != kLegacyCustomColors) {
        Fail(scenario, "legacy custom color JSON was not copied verbatim");
        co_return;
      }

      application::ThemeSettingsRepository theme_repository(
          scenario->theme_destination, std::make_shared<FixedSystemTheme>());
      const auto theme = theme_repository.Load();
      if (theme.selected_mode != domain::ThemeMode::custom ||
          !theme.has_saved_custom_colors ||
          theme.palette[domain::ThemeColorRole::background] != 0xFF112233U ||
          theme.palette[domain::ThemeColorRole::accent] != 0xFF445566U ||
          theme.palette[domain::ThemeColorRole::text] != 0xFFFAFAFAU) {
        Fail(scenario, "migrated legacy theme did not load through repository");
        co_return;
      }

      auto second_import =
          co_await application::ThemeSettingsMigration::ImportIfMissing(
              scenario->theme_destination, scenario->legacy_settings);
      if (!second_import || *second_import) {
        Fail(scenario, second_import
                           ? "theme migration was not idempotent"
                           : "second theme migration failed: " +
                                 second_import.error().message);
        co_return;
      }

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("legacy-upgrade-integration-probe");
}

void ProductionStoresUpgradeLegacyDatabase() {
  TemporaryDirectory temporary;
  const auto database_path = temporary.Path() / "linecode.db";
  CreateLegacyDatabase(database_path);

  active_scenario = std::make_shared<UpgradeScenario>();
  active_scenario->models =
      std::make_shared<infrastructure::SqliteModelStore>(
          huxerui::File{database_path.string()});
  active_scenario->legacy_settings =
      std::make_shared<infrastructure::SQLiteSettingsStore>(
          huxerui::File{database_path.string()});
  active_scenario->theme_destination =
      std::make_shared<infrastructure::ThemeFileSettingsStore>(
          huxerui::File{(temporary.Path() / "settings").string()});

  {
    const huxerui::Application application(UpgradeProbe,
                                           {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(application);
    for (int attempt = 0; attempt < 2'000 && !active_scenario->done; ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    EXPECT_EXPRESSION(active_scenario->done);
    if (!active_scenario->failure.empty())
      std::cerr << active_scenario->failure << '\n';
    EXPECT_EXPRESSION(active_scenario->failure.empty());
  }
  active_scenario.reset();

  Database upgraded(database_path);
  const auto upgraded_columns = upgraded.Columns("model_configs");
  constexpr std::array expected_columns{
      "tool_call_limit", "compression_model_enabled",
      "compression_model_auto", "compression_model_id", "context_size"};
  for (const std::string_view column : expected_columns)
    EXPECT_EXPRESSION(std::ranges::contains(upgraded_columns, column));
}

} // namespace

TEST(legacy_upgrade_integration_tests, LegacySuite) {
  ProductionStoresUpgradeLegacyDatabase();
  std::cout << "legacy upgrade integration tests passed\n";
}
