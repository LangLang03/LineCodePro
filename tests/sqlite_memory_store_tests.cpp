#include "gtest_support.h"

#include <array>
#include <chrono>
#include <filesystem>
#include <iostream>
#include <memory>
#include <string>
#include <string_view>
#include <thread>

#include <huxerui/huxerui.h>
#include <huxerui/sqlite.h>
#include <huxerui/testing/ui_test.h>

#include "infrastructure/sqlite_memory_store.h"

namespace {

namespace infrastructure = linecode::infrastructure;

constexpr std::string_view kLegacyMemoryId{"legacy-memory"};

// The schema entry the legacy Android build left behind for the FTS4 index it
// maintained beside `memories`. A database upgraded from that build still
// carries it, but this runtime's SQLite is built without an FTS module, so the
// table can neither be read nor written: every statement naming it fails with
// "no such module: fts4". The fixture writes the entry directly because
// creating the table the way the legacy build did needs an FTS-capable SQLite.
constexpr std::string_view kLegacyFtsSchema =
    "CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts4("
    "id, scope, project_id, content, tokenize=unicode61)";

// The `memories` layout and row such an upgraded database already holds.
constexpr std::string_view kCreateLegacyMemories = R"sql(
CREATE TABLE memories (
  id TEXT PRIMARY KEY,
  scope TEXT NOT NULL,
  project_id TEXT,
  content TEXT NOT NULL,
  source TEXT NOT NULL,
  confidence REAL NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  last_used_at INTEGER,
  use_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT,
  title TEXT NOT NULL DEFAULT ''
)
)sql";

constexpr std::string_view kInsertLegacyMemory = R"sql(
INSERT INTO memories
  (id, scope, project_id, content, source, confidence, created_at, updated_at,
   use_count, raw_json, title)
VALUES ('legacy-memory', 'user', NULL, 'delete me', 'manual', 1, 10, 20, 0,
        '', '')
)sql";

std::string LegacyFtsSchemaStatement() {
  return "INSERT INTO sqlite_master(type, name, tbl_name, rootpage, sql) "
         "VALUES ('table', 'memories_fts', 'memories_fts', 0, '" +
         std::string{kLegacyFtsSchema} + "')";
}

huxerui::Task<huxerui::sqlite::Result<void>> SeedLegacyDatabase(
    huxerui::File file) {
  auto opened = co_await huxerui::sqlite::Database::OpenAsync(
      std::move(file), {.create_parent_directories = true});
  if (!opened)
    co_return huxerui::sqlite::Result<void>{opened.Error()};
  std::array statements{
      std::string{kCreateLegacyMemories},
      std::string{kInsertLegacyMemory},
      std::string{"PRAGMA writable_schema = ON"},
      LegacyFtsSchemaStatement(),
      std::string{"PRAGMA writable_schema = OFF"},
      std::string{"PRAGMA schema_version = 2"},
  };
  for (const auto &statement : statements) {
    auto executed = co_await opened->ExecuteAsync(statement);
    if (!executed)
      co_return huxerui::sqlite::Result<void>{executed.Error()};
  }
  co_return huxerui::sqlite::Result<void>{};
}

struct DeleteScenario final {
  std::shared_ptr<infrastructure::SqliteMemoryStore> store;
  std::string database_path;
  bool done{};
  std::string failure;
};

std::shared_ptr<DeleteScenario> scenario;

huxerui::View DeleteProbe() {
  const auto current = scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    const auto handle = tasks.Launch([current]() -> huxerui::Task<void> {
      const auto fail = [current](std::string message) {
        current->failure = std::move(message);
        current->done = true;
      };
      {
        auto seeded =
            co_await SeedLegacyDatabase(huxerui::File{current->database_path});
        if (!seeded) {
          fail("legacy fixture failed: " + seeded.Error().Message());
          co_return;
        }
      }

      auto deleted =
          co_await current->store->Delete({std::string{kLegacyMemoryId}});
      if (!deleted) {
        fail("deleting a memory failed: " + deleted.error().message);
        co_return;
      }
      auto overview = co_await current->store->LoadOverview("");
      if (!overview) {
        fail("reloading memories failed: " + overview.error().message);
        co_return;
      }
      if (!overview->long_term.empty()) {
        fail("the deleted memory is still stored");
        co_return;
      }
      current->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("sqlite-memory-store-probe");
}

void DeleteSucceedsWithUnreachableLegacyIndex() {
  const auto nonce = std::chrono::steady_clock::now()
                         .time_since_epoch()
                         .count();
  const auto temporary = std::filesystem::temp_directory_path() /
                         ("linecode-memory-store-" + std::to_string(nonce));
  const auto database = temporary / "linecode.db";

  scenario = std::make_shared<DeleteScenario>();
  scenario->database_path = database.string();
  scenario->store = std::make_shared<infrastructure::SqliteMemoryStore>(
      huxerui::File{scenario->database_path});
  {
    const huxerui::Application app(DeleteProbe, {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 2'000 && !scenario->done; ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    EXPECT_EXPRESSION(scenario->done);
    EXPECT_EXPRESSION(scenario->failure.empty()) << scenario->failure;
  }
  scenario.reset();
  std::error_code ignored;
  std::filesystem::remove_all(temporary, ignored);
}

} // namespace

TEST(sqlite_memory_store_tests, LegacySuite) {
  DeleteSucceedsWithUnreachableLegacyIndex();
  std::cout << "sqlite memory store tests passed\n";
}
