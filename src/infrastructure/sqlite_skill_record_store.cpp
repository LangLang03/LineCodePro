#include "infrastructure/sqlite_skill_record_store.h"

#include <optional>
#include <utility>

#include <huxerui/sqlite.h>

#include "infrastructure/archive_json.h"
#include "infrastructure/legacy_feature_schema.h"

namespace linecode::infrastructure {

class SqliteSkillRecordStoreState final {
public:
  explicit SqliteSkillRecordStoreState(huxerui::File file)
      : file(std::move(file)) {}

  huxerui::File file;
  std::optional<huxerui::sqlite::Database> database;
};

namespace {

using application::SkillError;
template <class Value>
using Result = application::SkillResult<Value>;
using huxerui::sqlite::Database;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;
namespace json = archive_json;

SkillError Error(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

huxerui::Task<Result<Database>>
Open(const std::shared_ptr<SqliteSkillRecordStoreState> &state) {
  if (state->database)
    co_return *state->database;
  auto opened = co_await Database::OpenAsync(
      state->file,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(Error(opened.Error()));
  auto schema = co_await opened->TransactionAsync([](Transaction &transaction) {
    return legacy_feature_schema::Ensure(transaction);
  });
  if (!schema)
    co_return std::unexpected(Error(schema.Error()));
  state->database = *opened;
  co_return *state->database;
}

template <class Value>
huxerui::sqlite::Result<Value> Required(const RowView &row,
                                        const std::size_t column) {
  return row.Get<Value>(column);
}

huxerui::sqlite::Result<std::string>
OptionalText(const RowView &row, const std::size_t column) {
  auto value = row.Get<std::optional<std::string>>(column);
  if (!value)
    return value.Error();
  return value->value_or("");
}

huxerui::sqlite::Result<domain::SkillRecord> Decode(const RowView &row) {
  auto id = Required<std::string>(row, 0);
  if (!id)
    return id.Error();
  auto name = Required<std::string>(row, 1);
  if (!name)
    return name.Error();
  auto scope = Required<std::string>(row, 2);
  if (!scope)
    return scope.Error();
  auto path = OptionalText(row, 3);
  if (!path)
    return path.Error();
  auto description = OptionalText(row, 4);
  if (!description)
    return description.Error();
  auto enabled = Required<bool>(row, 5);
  if (!enabled)
    return enabled.Error();
  auto updated = Required<std::int64_t>(row, 6);
  if (!updated)
    return updated.Error();
  auto raw = OptionalText(row, 7);
  if (!raw)
    return raw.Error();
  std::string markdown_path = path->empty()
                                  ? std::string{}
                                  : huxerui::File{*path}.Child("SKILL.md").Path();
  std::int64_t discovered = *updated;
  if (const auto parsed = json::Parse(*raw); parsed) {
    if (const auto *object = json::AsObject(&*parsed)) {
      if (const auto *value =
              json::AsString(json::Find(*object, "skillMdPath")))
        markdown_path = *value;
      if (const auto *value = json::Find(*object, "discoveredAt")) {
        if (const auto *integer = std::get_if<std::int64_t>(value))
          discovered = *integer;
      }
    }
  }
  return domain::SkillRecord{
      .id = std::move(*id),
      .name = std::move(*name),
      .description = std::move(*description),
      .root_path = std::move(*path),
      .skill_markdown_path = std::move(markdown_path),
      .location = domain::ParseSkillLocation(*scope),
      .enabled = *enabled,
      .discovered_at = discovered,
      .updated_at = *updated,
  };
}

std::string RawJson(const domain::SkillRecord &skill) {
  return json::Serialize(json::Object{
      {"skillMdPath", skill.skill_markdown_path},
      {"rootPath", skill.root_path},
      {"location", std::string{domain::SerializeSkillLocation(skill.location)}},
      {"discoveredAt", skill.discovered_at},
  });
}

} // namespace

SqliteSkillRecordStore::SqliteSkillRecordStore(huxerui::File database_file)
    : state_(std::make_shared<SqliteSkillRecordStoreState>(
          std::move(database_file))) {}

huxerui::Task<Result<std::vector<domain::SkillRecord>>>
SqliteSkillRecordStore::List() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(std::move(database.error()));
  auto rows = co_await database->QueryAsync<domain::SkillRecord>(
      "SELECT id, name, scope, path, description, enabled, updated_at, "
      "raw_json FROM skills ORDER BY name COLLATE NOCASE ASC, id ASC",
      Decode);
  if (!rows)
    co_return std::unexpected(Error(rows.Error()));
  co_return std::move(*rows);
}

huxerui::Task<Result<void>> SqliteSkillRecordStore::UpsertDiscovered(
    std::vector<domain::SkillRecord> skills) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(std::move(database.error()));
  auto saved = co_await database->TransactionAsync(
      [skills = std::move(skills)](Transaction &transaction)
          -> huxerui::sqlite::Result<void> {
        for (const auto &skill : skills) {
          auto row = transaction.Execute(
              "INSERT INTO skills (id, name, scope, path, description, enabled, "
              "updated_at, raw_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
              "ON CONFLICT(id) DO UPDATE SET name = excluded.name, "
              "scope = excluded.scope, path = excluded.path, "
              "description = excluded.description, "
              "updated_at = excluded.updated_at, raw_json = excluded.raw_json",
              skill.id, skill.name,
              std::string{domain::SerializeSkillLocation(skill.location)},
              skill.root_path, skill.description, skill.enabled,
              skill.updated_at, RawJson(skill));
          if (!row)
            return row.Error();
        }
        return huxerui::sqlite::Result<void>{};
      });
  if (!saved)
    co_return std::unexpected(Error(saved.Error()));
  co_return Result<void>{};
}

huxerui::Task<Result<void>>
SqliteSkillRecordStore::SetEnabled(std::string id, const bool enabled) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(std::move(database.error()));
  auto changed = co_await database->ExecuteAsync(
      "UPDATE skills SET enabled = ? WHERE id = ?", enabled, id);
  if (!changed)
    co_return std::unexpected(Error(changed.Error()));
  co_return Result<void>{};
}

huxerui::Task<Result<void>>
SqliteSkillRecordStore::Delete(std::vector<std::string> ids) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(std::move(database.error()));
  auto deleted = co_await database->TransactionAsync(
      [ids = std::move(ids)](Transaction &transaction)
          -> huxerui::sqlite::Result<void> {
        for (const auto &id : ids) {
          if (id.empty())
            continue;
          auto row = transaction.Execute("DELETE FROM skills WHERE id = ?", id);
          if (!row)
            return row.Error();
        }
        return huxerui::sqlite::Result<void>{};
      });
  if (!deleted)
    co_return std::unexpected(Error(deleted.Error()));
  co_return Result<void>{};
}

} // namespace linecode::infrastructure
