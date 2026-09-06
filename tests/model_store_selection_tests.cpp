#include <cassert>
#include <cstdint>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>

#include <sqlite3.h>

#include "infrastructure/legacy_model_schema.h"

namespace {

namespace schema = linecode::infrastructure::legacy_model_schema;

class Database final {
public:
  Database() {
    if (sqlite3_open(":memory:", &handle_) != SQLITE_OK) {
      throw std::runtime_error("sqlite3_open failed");
    }
    Execute(schema::create_table);
  }

  ~Database() { sqlite3_close(handle_); }

  Database(const Database &) = delete;
  Database &operator=(const Database &) = delete;

  void Execute(std::string_view sql) {
    char *error = nullptr;
    const std::string owned{sql};
    if (sqlite3_exec(handle_, owned.c_str(), nullptr, nullptr, &error) ==
        SQLITE_OK) {
      return;
    }
    const std::string message =
        error == nullptr ? "sqlite3_exec failed" : std::string{error};
    sqlite3_free(error);
    throw std::runtime_error(message);
  }

  void Insert(std::string_view id, std::int64_t selected,
              std::int64_t updated_at) {
    sqlite3_stmt *statement = nullptr;
    constexpr std::string_view sql =
        "INSERT INTO model_configs "
        "(id, name, protocol_type, provider_label, base_url, api_key, "
        "model_id, selected, created_at, updated_at) "
        "VALUES (?, ?, 'OPENAI_COMPATIBLE', 'OpenAI', '', '', ?, ?, 1, ?)";
    const std::string owned{sql};
    Check(sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr));
    BindText(statement, 1, id);
    BindText(statement, 2, id);
    BindText(statement, 3, id);
    Check(sqlite3_bind_int64(statement, 4, selected));
    Check(sqlite3_bind_int64(statement, 5, updated_at));
    StepDone(statement);
  }

  void Select(std::string_view id, std::int64_t updated_at) {
    Execute("BEGIN IMMEDIATE");
    try {
      Execute(schema::clear_selection);
      if (!id.empty()) {
        sqlite3_stmt *statement = nullptr;
        const std::string sql{schema::select_id};
        Check(sqlite3_prepare_v2(handle_, sql.c_str(), -1, &statement,
                                 nullptr));
        Check(sqlite3_bind_int64(statement, 1, updated_at));
        BindText(statement, 2, id);
        StepDone(statement);
      }
      Execute("COMMIT");
    } catch (...) {
      try {
        Execute("ROLLBACK");
      } catch (...) {
      }
      throw;
    }
  }

  void Delete(std::string_view id) {
    sqlite3_stmt *statement = nullptr;
    constexpr std::string_view sql =
        "DELETE FROM model_configs WHERE id = ?";
    const std::string owned{sql};
    Check(sqlite3_prepare_v2(handle_, owned.c_str(), -1, &statement, nullptr));
    BindText(statement, 1, id);
    StepDone(statement);
  }

  [[nodiscard]] std::optional<std::string> SelectedId() const {
    if (auto selected = QueryId(schema::selected_id)) {
      return selected;
    }
    return QueryId(schema::fallback_model_id);
  }

  [[nodiscard]] std::int64_t SelectedCount() const {
    sqlite3_stmt *statement = nullptr;
    Check(sqlite3_prepare_v2(
        handle_,
        "SELECT COUNT(*) FROM model_configs WHERE selected != 0", -1,
        &statement, nullptr));
    if (sqlite3_step(statement) != SQLITE_ROW) {
      const std::string message = sqlite3_errmsg(handle_);
      sqlite3_finalize(statement);
      throw std::runtime_error(message);
    }
    const auto count = sqlite3_column_int64(statement, 0);
    sqlite3_finalize(statement);
    return count;
  }

private:
  [[nodiscard]] std::optional<std::string>
  QueryId(std::string_view query) const {
    sqlite3_stmt *statement = nullptr;
    const std::string sql{query};
    Check(sqlite3_prepare_v2(handle_, sql.c_str(), -1, &statement, nullptr));
    const int step = sqlite3_step(statement);
    if (step == SQLITE_DONE) {
      sqlite3_finalize(statement);
      return std::nullopt;
    }
    if (step != SQLITE_ROW) {
      const std::string message = sqlite3_errmsg(handle_);
      sqlite3_finalize(statement);
      throw std::runtime_error(message);
    }
    const auto *text = sqlite3_column_text(statement, 0);
    const std::string id =
        text == nullptr ? std::string{} : reinterpret_cast<const char *>(text);
    sqlite3_finalize(statement);
    return id;
  }

  void Check(int result) const {
    if (result != SQLITE_OK) {
      throw std::runtime_error(sqlite3_errmsg(handle_));
    }
  }

  void BindText(sqlite3_stmt *statement, int index,
                std::string_view value) const {
    Check(sqlite3_bind_text(statement, index, value.data(),
                            static_cast<int>(value.size()), SQLITE_TRANSIENT));
  }

  void StepDone(sqlite3_stmt *statement) const {
    const int step = sqlite3_step(statement);
    if (step != SQLITE_DONE) {
      const std::string message = sqlite3_errmsg(handle_);
      sqlite3_finalize(statement);
      throw std::runtime_error(message);
    }
    sqlite3_finalize(statement);
  }

  sqlite3 *handle_{};
};

void SelectedEqualsOneTakesPriority() {
  Database database;
  database.Insert("selected-older", 1, 10);
  database.Insert("selected-newer", 1, 20);
  database.Insert("non-standard", 2, 30);

  assert(database.SelectedId() == "selected-newer");
}

void NoSelectedEqualsOneFallsBackToLegacySortOrder() {
  Database database;
  database.Insert("negative-newest", -1, 50);
  database.Insert("zero-newest", 0, 40);
  database.Insert("two-older", 2, 10);

  assert(database.SelectedId() == "two-older");

  Database all_unselected;
  all_unselected.Insert("older", 0, 10);
  all_unselected.Insert("newest", 0, 20);
  assert(all_unselected.SelectedId() == "newest");
}

void SelectingOneModelClearsEveryPreviousMarker() {
  Database database;
  database.Insert("legacy-a", -1, 10);
  database.Insert("legacy-b", 2, 20);
  database.Insert("target", 0, 30);

  database.Select("target", 40);

  assert(database.SelectedCount() == 1);
  assert(database.SelectedId() == "target");
}

void SelectingMissingOrEmptyIdUsesLegacyFallback() {
  Database database;
  database.Insert("selected", 1, 10);
  database.Insert("other", 0, 20);

  database.Select("missing", 30);
  assert(database.SelectedCount() == 0);
  assert(database.SelectedId() == "other");

  database.Select("other", 40);
  database.Select("", 50);
  assert(database.SelectedCount() == 0);
  assert(database.SelectedId() == "other");
}

void DeletingCurrentModelFallsBackToNewestRemainingModel() {
  Database database;
  database.Insert("current", 1, 10);
  database.Insert("older", 0, 20);
  database.Insert("newest", 0, 30);

  database.Delete("current");

  assert(database.SelectedCount() == 0);
  assert(database.SelectedId() == "newest");
}

void EmptyTableIsTheOnlyEmptySelection() {
  Database database;

  assert(!database.SelectedId());
}

} // namespace

int main() {
  SelectedEqualsOneTakesPriority();
  NoSelectedEqualsOneFallsBackToLegacySortOrder();
  SelectingOneModelClearsEveryPreviousMarker();
  SelectingMissingOrEmptyIdUsesLegacyFallback();
  DeletingCurrentModelFallsBackToNewestRemainingModel();
  EmptyTableIsTheOnlyEmptySelection();
  return 0;
}
