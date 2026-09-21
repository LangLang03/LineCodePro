#include "gtest_support.h"
#include <chrono>
#include <filesystem>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>

#include <sqlite3.h>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "infrastructure/sqlite_archive_database.h"

namespace {

void Execute(sqlite3 *database, std::string_view sql) {
  char *message{};
  if (sqlite3_exec(database, std::string{sql}.c_str(), nullptr, nullptr,
                   &message) == SQLITE_OK)
    return;
  const std::string detail = message == nullptr ? "SQLite error" : message;
  sqlite3_free(message);
  throw std::runtime_error(detail);
}

void CreateSecretFixture(const std::filesystem::path &path) {
  sqlite3 *database{};
  if (sqlite3_open(path.c_str(), &database) != SQLITE_OK)
    throw std::runtime_error("cannot create redaction fixture");
  try {
    Execute(database,
            "CREATE TABLE model_configs(id TEXT, api_key TEXT, raw_json TEXT);");
    Execute(database,
            "INSERT INTO model_configs VALUES "
            "('safe-model','model-api-secret',"
            "'{\"token\":\"model-raw-secret\",\"safe\":\"model-safe\"}');");
    Execute(database,
            "CREATE TABLE settings(key TEXT, value TEXT, type TEXT);");
    Execute(database,
            "INSERT INTO settings VALUES "
            "('@lineai_ssh_config',"
            "'{\"host\":\"ssh-safe.test\",\"password\":\"ssh-secret\"}',"
            "'string'),"
            "('@lineai_access_token','setting-token-secret','string');");
    Execute(database,
            "CREATE TABLE extension_mcps(id TEXT, request_headers_json TEXT, "
            "raw_json TEXT);");
    Execute(database,
            "INSERT INTO extension_mcps VALUES "
            "('safe-mcp',"
            "'[{\"name\":\"Authorization\",\"value\":\"Bearer header-secret\"},"
            "{\"name\":\"Accept\",\"value\":\"application/json\"}]',"
            "'{\"cookie\":\"mcp-cookie-secret\",\"safe\":\"mcp-safe\"}');");
    Execute(database,
            "CREATE TABLE messages(id TEXT, content TEXT, reasoning_content "
            "TEXT, raw_json TEXT);");
    Execute(database,
            "INSERT INTO messages VALUES "
            "('legacy-message','conversation-safe','reasoning-safe',"
            "'{\"token\":\"legacy-message-secret\"}');");
    Execute(database,
            "CREATE TABLE message_text_chunks(id INTEGER, message_id TEXT, "
            "field_name TEXT, chunk_order INTEGER, content TEXT);");
    Execute(database,
            "INSERT INTO message_text_chunks VALUES "
            "(1,'legacy-message','raw_json',0,"
            "'{\"authorization\":\"chunk-secret\"}');");
  } catch (...) {
    sqlite3_close(database);
    throw;
  }
  sqlite3_close(database);
}

struct Scenario final {
  std::filesystem::path database_path;
  bool done{};
  std::string exported;
  std::string error;
};

std::shared_ptr<Scenario> active_scenario;

huxerui::View RedactionProbe() {
  const auto scenario = active_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto archive =
          std::make_shared<linecode::infrastructure::SqliteArchiveDatabase>(
              huxerui::File{scenario->database_path.string()});
      auto exported = co_await archive->ExportRedacted();
      if (exported)
        scenario->exported = std::move(exported->json);
      else
        scenario->error = std::move(exported.error().message);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("sqlite-archive-redaction-probe");
}

void AssertMissing(std::string_view output, std::string_view secret) {
  EXPECT_EXPRESSION(!output.contains(secret));
}

} // namespace

TEST(sqlite_archive_redaction_tests, LegacySuite) {
  const auto nonce =
      std::chrono::steady_clock::now().time_since_epoch().count();
  const auto temporary = std::filesystem::temp_directory_path() /
                         ("linecode-sqlite-redaction-" +
                          std::to_string(nonce));
  std::filesystem::create_directories(temporary);
  const auto database_path = temporary / "linecode.db";
  CreateSecretFixture(database_path);

  active_scenario =
      std::make_shared<Scenario>(Scenario{.database_path = database_path,
                                          .done = false,
                                          .exported = {},
                                          .error = {}});
  {
    const huxerui::Application app(RedactionProbe,
                                   {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (std::size_t attempt = 0; attempt < 4'000U && !active_scenario->done;
         ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    EXPECT_EXPRESSION(active_scenario->done);
    if (!active_scenario->error.empty())
      throw std::runtime_error(active_scenario->error);
  }

  const auto &output = active_scenario->exported;
  EXPECT_EXPRESSION(output.contains("model-safe"));
  EXPECT_EXPRESSION(output.contains("ssh-safe.test"));
  EXPECT_EXPRESSION(output.contains("application/json"));
  EXPECT_EXPRESSION(output.contains("mcp-safe"));
  EXPECT_EXPRESSION(output.contains("conversation-safe"));
  EXPECT_EXPRESSION(output.contains("reasoning-safe"));
  AssertMissing(output, "model-api-secret");
  AssertMissing(output, "model-raw-secret");
  AssertMissing(output, "ssh-secret");
  AssertMissing(output, "setting-token-secret");
  AssertMissing(output, "header-secret");
  AssertMissing(output, "mcp-cookie-secret");
  AssertMissing(output, "legacy-message-secret");
  AssertMissing(output, "chunk-secret");

  active_scenario.reset();
  std::error_code ignored;
  std::filesystem::remove_all(temporary, ignored);
}
