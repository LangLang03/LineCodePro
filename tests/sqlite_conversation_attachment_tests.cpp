#include <cassert>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <memory>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <vector>

#include <sqlite3.h>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "infrastructure/attachment_json_codec.h"
#include "infrastructure/legacy_conversation_schema.h"
#include "infrastructure/sqlite_conversation_store.h"

namespace {

class Database final {
public:
  explicit Database(const std::filesystem::path &path) {
    if (sqlite3_open(path.c_str(), &database_) != SQLITE_OK) {
      throw std::runtime_error("cannot open temporary SQLite database");
    }
  }

  ~Database() {
    if (database_ != nullptr) {
      sqlite3_close(database_);
    }
  }

  Database(const Database &) = delete;
  Database &operator=(const Database &) = delete;

  void Execute(std::string_view sql) {
    char *message{};
    if (sqlite3_exec(database_, std::string{sql}.c_str(), nullptr, nullptr,
                     &message) != SQLITE_OK) {
      const std::string detail = message == nullptr ? "SQLite error" : message;
      sqlite3_free(message);
      throw std::runtime_error(detail);
    }
  }

  void InsertMessage(std::string_view id, std::int64_t order,
                     std::string_view content,
                     std::string_view raw_json_column = {}) {
    sqlite3_stmt *statement{};
    constexpr std::string_view sql =
        "INSERT INTO messages "
        "(id, conversation_id, local_order, role, content, timestamp, "
        "raw_json) VALUES (?, 'legacy-conversation', ?, 'user', ?, 100, ?)";
    if (sqlite3_prepare_v2(database_, sql.data(), static_cast<int>(sql.size()),
                           &statement, nullptr) != SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    Bind(statement, 1, id);
    sqlite3_bind_int64(statement, 2, order);
    Bind(statement, 3, content);
    Bind(statement, 4, raw_json_column);
    Step(statement);
  }

  void InsertChunk(std::string_view message_id, std::string_view field,
                   std::string_view content) {
    sqlite3_stmt *statement{};
    constexpr std::string_view sql =
        "INSERT INTO message_text_chunks "
        "(message_id, field_name, chunk_order, content) VALUES (?, ?, 0, ?)";
    if (sqlite3_prepare_v2(database_, sql.data(), static_cast<int>(sql.size()),
                           &statement, nullptr) != SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    Bind(statement, 1, message_id);
    Bind(statement, 2, field);
    Bind(statement, 3, content);
    Step(statement);
  }

  void InsertAttachment(std::string_view message_id, std::string_view name,
                        std::string_view path, std::string_view source) {
    sqlite3_stmt *statement{};
    constexpr std::string_view sql =
        "INSERT INTO attachments (message_id, name, path, source, raw_json) "
        "VALUES (?, ?, ?, ?, '')";
    if (sqlite3_prepare_v2(database_, sql.data(), static_cast<int>(sql.size()),
                           &statement, nullptr) != SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    Bind(statement, 1, message_id);
    Bind(statement, 2, name);
    Bind(statement, 3, path);
    Bind(statement, 4, source);
    Step(statement);
  }

  [[nodiscard]] std::int64_t Integer(std::string_view sql) {
    sqlite3_stmt *statement{};
    if (sqlite3_prepare_v2(database_, sql.data(), static_cast<int>(sql.size()),
                           &statement, nullptr) != SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    if (sqlite3_step(statement) != SQLITE_ROW) {
      sqlite3_finalize(statement);
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    const auto value = sqlite3_column_int64(statement, 0);
    sqlite3_finalize(statement);
    return value;
  }

  [[nodiscard]] std::string Text(std::string_view sql) {
    sqlite3_stmt *statement{};
    if (sqlite3_prepare_v2(database_, sql.data(), static_cast<int>(sql.size()),
                           &statement, nullptr) != SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    if (sqlite3_step(statement) != SQLITE_ROW) {
      sqlite3_finalize(statement);
      throw std::runtime_error(sqlite3_errmsg(database_));
    }
    const auto *text = reinterpret_cast<const char *>(
        sqlite3_column_text(statement, 0));
    const std::string value = text == nullptr ? std::string{} : text;
    sqlite3_finalize(statement);
    return value;
  }

private:
  static void Bind(sqlite3_stmt *statement, int index,
                   std::string_view value) {
    if (sqlite3_bind_text(statement, index, value.data(),
                          static_cast<int>(value.size()), SQLITE_TRANSIENT) !=
        SQLITE_OK) {
      sqlite3_finalize(statement);
      throw std::runtime_error("cannot bind SQLite text");
    }
  }

  void Step(sqlite3_stmt *statement) {
    if (sqlite3_step(statement) != SQLITE_DONE) {
      const std::string detail = sqlite3_errmsg(database_);
      sqlite3_finalize(statement);
      throw std::runtime_error(detail);
    }
    sqlite3_finalize(statement);
  }

  sqlite3 *database_{};
};

void CreateLegacyFixture(const std::filesystem::path &path) {
  using namespace linecode::infrastructure::legacy_schema;
  Database database{path};
  database.Execute("PRAGMA foreign_keys = ON");
  for (const auto statement : table_statements) {
    database.Execute(statement);
  }
  database.Execute("PRAGMA user_version = 4");
  database.Execute(
      "INSERT INTO conversations "
      "(id, title, created_at, updated_at, current, raw_json) "
      "VALUES ('legacy-conversation', 'legacy', 10, 20, 1, '')");

  const std::vector<linecode::domain::InputAttachment> raw_attachments{
      {"raw.txt", "/legacy/raw.txt", "ssh"}};
  const auto raw_attachment =
      linecode::infrastructure::EncodeAttachmentJson(raw_attachments);
  database.InsertMessage("legacy-raw", 0, "raw");
  database.InsertChunk("legacy-raw", "raw_json", raw_attachment);

  database.InsertMessage("legacy-table", 1, "table", "{malformed");
  database.InsertAttachment("legacy-table", "table.txt",
                            "/legacy/table.txt", "terminal_provider");

  const std::vector<linecode::domain::InputAttachment> column_attachments{
      {"column.txt", "/legacy/column.txt", "local"}};
  const auto column_attachment =
      linecode::infrastructure::EncodeAttachmentJson(column_attachments);
  database.InsertMessage("legacy-column", 2, "column", column_attachment);
}

struct Scenario final {
  std::filesystem::path database_path;
  bool done{};
  bool passed{};
};

std::shared_ptr<Scenario> active_scenario;

huxerui::View ConversationStoreProbe() {
  const auto scenario = active_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario, tasks]() -> huxerui::Task<void> {
      auto store =
          std::make_shared<linecode::infrastructure::SqliteConversationStore>(
              tasks, [] {});
      const auto initialized = co_await store->InitializeAsync(
          huxerui::File{scenario->database_path.string()});
      if (!initialized) {
        scenario->done = true;
        co_return;
      }
      const auto loaded = store->Messages();
      bool passed = loaded.size() == 3U &&
                    loaded[0].attachments.size() == 1U &&
                    loaded[0].attachments[0].Path() == "/legacy/raw.txt" &&
                    loaded[1].attachments.size() == 1U &&
                    loaded[1].attachments[0].Source() ==
                        "terminal_provider" &&
                    loaded[2].attachments.size() == 1U &&
                    loaded[2].attachments[0].Name() == "column.txt";

      store->Append({
          .id = store->AllocateMessageId(),
          .role = linecode::domain::MessageRole::user,
          .content = "fresh",
          .attachments = {{"new.txt", "/new.txt", "local"},
                          {"remote.txt", "/remote.txt", "ssh"}},
      });
      const auto flushed = co_await store->FlushPendingAsync();
      bool reload_succeeded{};
      if (flushed) {
        const auto reloaded = co_await store->ReloadAsync();
        reload_succeeded = static_cast<bool>(reloaded);
      }
      const auto after_reload = store->Messages();
      const auto fresh = std::ranges::find(after_reload, std::string_view{"fresh"},
                                           &linecode::domain::ChatMessage::content);
      passed = passed && reload_succeeded && fresh != after_reload.end() &&
               fresh->attachments.size() == 2U &&
               fresh->attachments[0].Path() == "/new.txt" &&
               fresh->attachments[1].Source() == "ssh";

      const auto recalled_id = store->AllocateMessageId();
      store->Append({.id = recalled_id,
                     .role = linecode::domain::MessageRole::user,
                     .content = "retry",
                     .attachments = {{"retry.txt", "/retry.txt", "local"}}});
      store->Append({.id = store->AllocateMessageId(),
                     .role = linecode::domain::MessageRole::assistant,
                     .content = "discard",
                     .attachments = {}});
      const auto recalled = store->RecallUserMessage(recalled_id);
      const auto recall_flushed = co_await store->FlushPendingAsync();
      bool recall_reloaded{};
      if (recall_flushed) {
        recall_reloaded =
            static_cast<bool>(co_await store->ReloadAsync());
      }
      const auto after_recall = store->Messages();
      passed = passed && recalled && recalled->content == "retry" &&
               recall_reloaded && after_recall.size() == 4U &&
               std::ranges::none_of(after_recall, [](const auto &message) {
                 return message.content == "retry" ||
                        message.content == "discard";
               });
      scenario->passed = passed;
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("conversation-attachment-store-probe");
}

void StorePersistsAndRestoresBothLegacyRepresentations() {
  const auto nonce =
      std::chrono::steady_clock::now().time_since_epoch().count();
  const auto temporary = std::filesystem::temp_directory_path() /
                         ("linecode-attachment-store-" +
                          std::to_string(nonce));
  std::filesystem::create_directories(temporary);
  const auto database_path = temporary / "linecode.db";
  CreateLegacyFixture(database_path);

  active_scenario = std::make_shared<Scenario>(
      Scenario{.database_path = database_path});
  {
    const huxerui::Application app(ConversationStoreProbe,
                                   {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 4'000 && !active_scenario->done;
         ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    assert(active_scenario->done);
    assert(active_scenario->passed);
  }

  Database persisted{database_path};
  assert(persisted.Integer(
             "SELECT COUNT(*) FROM attachments AS a JOIN messages AS m "
             "ON m.id = a.message_id WHERE m.content = '' AND "
             "a.path IN ('/new.txt', '/remote.txt')") == 2);
  const auto raw_json = persisted.Text(
      "SELECT group_concat(c.content, '') FROM message_text_chunks AS c "
      "JOIN messages AS m ON m.id = c.message_id "
      "WHERE c.field_name = 'raw_json' AND m.local_order = 3 "
      "ORDER BY c.chunk_order");
  const auto decoded =
      linecode::infrastructure::DecodeAttachmentJson(raw_json);
  assert(decoded.size() == 2U);
  assert(decoded[0].Path() == "/new.txt");
  assert(decoded[1].Path() == "/remote.txt");

  active_scenario.reset();
  std::error_code ignored;
  std::filesystem::remove_all(temporary, ignored);
}

} // namespace

int main() { StorePersistsAndRestoresBothLegacyRepresentations(); }
