#include "infrastructure/sqlite_project_catalog_store.h"

#include <algorithm>
#include <array>
#include <iterator>
#include <optional>
#include <ranges>
#include <string_view>
#include <utility>

#include <huxerui/sqlite.h>

#include "infrastructure/legacy_project_schema.h"

namespace linecode::infrastructure {

class SqliteProjectCatalogStoreState final {
public:
  SqliteProjectCatalogStoreState(huxerui::File file, ProjectCatalogScope scope)
      : file(std::move(file)), scope(scope) {}

  huxerui::File file;
  ProjectCatalogScope scope{ProjectCatalogScope::local};
  std::optional<huxerui::sqlite::Database> database;
};

namespace {

using application::ProjectWorkspaceError;
using application::ProjectWorkspaceErrorCode;
using application::ProjectWorkspaceResult;
using huxerui::sqlite::Database;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;

using SourceName = std::pair<domain::ProjectSource, std::string_view>;

constexpr std::array<SourceName, 4> source_names{
    std::pair{domain::ProjectSource::default_home, std::string_view{"default"}},
    std::pair{domain::ProjectSource::managed, std::string_view{"managed"}},
    std::pair{domain::ProjectSource::external, std::string_view{"external"}},
    std::pair{domain::ProjectSource::ssh, std::string_view{"ssh"}},
};

std::string_view SelectedProjectKey(ProjectCatalogScope scope) {
  return scope == ProjectCatalogScope::ssh
             ? std::string_view{"@linecode_selected_project_ssh"}
             : legacy_project_schema::selected_local_project_key;
}

bool BelongsToScope(domain::ProjectSource source, ProjectCatalogScope scope) {
  return (scope == ProjectCatalogScope::ssh) ==
         (source == domain::ProjectSource::ssh);
}

ProjectWorkspaceError Error(ProjectWorkspaceErrorCode code,
                            std::string message) {
  return {.code = code, .message = std::move(message)};
}

ProjectWorkspaceError DatabaseError(const huxerui::sqlite::Error &error) {
  const auto code =
      error.Code() == huxerui::sqlite::ErrorCode::Decode ||
              error.Code() == huxerui::sqlite::ErrorCode::SchemaMismatch
          ? ProjectWorkspaceErrorCode::corrupt_catalog
          : ProjectWorkspaceErrorCode::io;
  return Error(code, error.Message());
}

huxerui::sqlite::Result<domain::ProjectSource>
DecodeSource(std::string_view value) {
  const auto found =
      std::ranges::find(source_names, value, &SourceName::second);
  if (found == source_names.end())
    return huxerui::sqlite::Error{
        huxerui::sqlite::ErrorCode::Decode,
        "projects.source contains an unsupported local source"};
  return found->first;
}

std::string_view EncodeSource(domain::ProjectSource source) {
  const auto found =
      std::ranges::find(source_names, source, &SourceName::first);
  return found == source_names.end() ? std::string_view{} : found->second;
}

huxerui::sqlite::Result<domain::ProjectRecord>
DecodeProject(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id)
    return id.Error();
  auto label = row.Get<std::string>(1);
  if (!label)
    return label.Error();
  auto path = row.Get<std::string>(2);
  if (!path)
    return path.Error();
  auto source_text = row.Get<std::string>(3);
  if (!source_text)
    return source_text.Error();
  auto source = DecodeSource(*source_text);
  if (!source)
    return source.Error();
  auto description = row.Get<std::optional<std::string>>(4);
  if (!description)
    return description.Error();
  auto selected = row.Get<bool>(5);
  if (!selected)
    return selected.Error();
  auto created_at = row.Get<std::int64_t>(6);
  if (!created_at)
    return created_at.Error();
  auto updated_at = row.Get<std::int64_t>(7);
  if (!updated_at)
    return updated_at.Error();
  if (id->empty() || (path->empty() && *source != domain::ProjectSource::ssh))
    return huxerui::sqlite::Error{huxerui::sqlite::ErrorCode::Decode,
                                  "projects contains an empty id or path"};
  return domain::ProjectRecord{
      .id = std::move(*id),
      .label = std::move(*label),
      .path = std::move(*path),
      .source = *source,
      .description = description->value_or(""),
      .selected = *selected,
      .created_at = *created_at,
      .updated_at = *updated_at,
  };
}

ProjectWorkspaceResult<void>
ValidateCatalog(const domain::ProjectCatalog &catalog,
                ProjectCatalogScope scope) {
  if (!catalog.selected_id.empty() &&
      std::ranges::none_of(catalog.projects, [&](const auto &project) {
        return project.id == catalog.selected_id;
      })) {
    return std::unexpected(Error(ProjectWorkspaceErrorCode::corrupt_catalog,
                                 "Selected project is not in the catalog"));
  }
  for (auto current = catalog.projects.begin();
       current != catalog.projects.end(); ++current) {
    if (current->id.empty() ||
        (current->path.empty() &&
         current->source != domain::ProjectSource::ssh) ||
        !BelongsToScope(current->source, scope) ||
        EncodeSource(current->source).empty() ||
        std::ranges::find(std::next(current), catalog.projects.end(),
                          current->id, &domain::ProjectRecord::id) !=
            catalog.projects.end()) {
      return std::unexpected(Error(ProjectWorkspaceErrorCode::corrupt_catalog,
                                   "Project catalog is invalid"));
    }
  }
  return {};
}

huxerui::Task<ProjectWorkspaceResult<Database>>
Open(const std::shared_ptr<SqliteProjectCatalogStoreState> &state) {
  if (state->database)
    co_return *state->database;
  auto opened = co_await Database::OpenAsync(
      state->file,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(DatabaseError(opened.Error()));
  auto schema = co_await opened->TransactionAsync([](Transaction &transaction) {
    return legacy_project_schema::Ensure(transaction);
  });
  if (!schema)
    co_return std::unexpected(DatabaseError(schema.Error()));
  state->database = *opened;
  co_return *state->database;
}

} // namespace

SqliteProjectCatalogStore::SqliteProjectCatalogStore(
    huxerui::File database_file, ProjectCatalogScope scope)
    : state_(std::make_shared<SqliteProjectCatalogStoreState>(
          std::move(database_file), scope)) {}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectCatalog>>
SqliteProjectCatalogStore::LoadCatalog() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(std::move(database.error()));
  const auto predicate = state_->scope == ProjectCatalogScope::ssh
                             ? std::string{"source = ?"}
                             : std::string{"source <> ?"};
  auto projects = co_await database->QueryAsync<domain::ProjectRecord>(
      "SELECT id, label, path, source, description, selected, created_at, "
      "updated_at FROM projects WHERE " +
          predicate + " ORDER BY selected DESC, updated_at DESC, id ASC",
      DecodeProject, std::string{"ssh"});
  if (!projects)
    co_return std::unexpected(DatabaseError(projects.Error()));
  auto selected_setting = co_await database->QueryAsync<std::string>(
      "SELECT value FROM settings WHERE key = ? LIMIT 1",
      [](const RowView &row) { return row.Get<std::string>(0); },
      std::string{SelectedProjectKey(state_->scope)});
  if (!selected_setting)
    co_return std::unexpected(DatabaseError(selected_setting.Error()));

  domain::ProjectCatalog catalog{.projects = std::move(*projects),
                                 .selected_id = {}};
  if (!selected_setting->empty() &&
      std::ranges::any_of(catalog.projects, [&](const auto &project) {
        return project.id == selected_setting->front();
      })) {
    catalog.selected_id = selected_setting->front();
  } else {
    const auto selected = std::ranges::find(catalog.projects, true,
                                            &domain::ProjectRecord::selected);
    if (selected != catalog.projects.end())
      catalog.selected_id = selected->id;
  }
  for (auto &project : catalog.projects)
    project.selected = project.id == catalog.selected_id;
  co_return catalog;
}

huxerui::Task<ProjectWorkspaceResult<void>>
SqliteProjectCatalogStore::ReplaceCatalog(domain::ProjectCatalog catalog) {
  auto valid = ValidateCatalog(catalog, state_->scope);
  if (!valid)
    co_return std::unexpected(std::move(valid.error()));
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(std::move(database.error()));
  std::int64_t catalog_timestamp{};
  for (const auto &project : catalog.projects)
    catalog_timestamp = std::max(catalog_timestamp, project.updated_at);
  auto replaced = co_await database->TransactionAsync(
      [catalog = std::move(catalog), catalog_timestamp, scope = state_->scope](
          Transaction &transaction) -> huxerui::sqlite::Result<void> {
        const auto remove_statement =
            scope == ProjectCatalogScope::ssh
                ? std::string{"DELETE FROM projects WHERE source = ?"}
                : std::string{"DELETE FROM projects WHERE source <> ?"};
        auto removed =
            transaction.Execute(remove_statement, std::string{"ssh"});
        if (!removed)
          return removed.Error();
        for (const auto &project : catalog.projects) {
          auto inserted = transaction.Execute(
              "INSERT INTO projects (id, label, path, source, description, "
              "selected, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, "
              "?)",
              project.id, project.label, project.path,
              std::string{EncodeSource(project.source)}, project.description,
              project.id == catalog.selected_id, project.created_at,
              project.updated_at);
          if (!inserted)
            return inserted.Error();
        }
        auto setting = transaction.Execute(
            "INSERT INTO settings (key, value, type, updated_at) "
            "VALUES (?, ?, 'string', ?) ON CONFLICT(key) DO UPDATE SET "
            "value = excluded.value, type = excluded.type, "
            "updated_at = excluded.updated_at",
            std::string{SelectedProjectKey(scope)}, catalog.selected_id,
            catalog_timestamp);
        if (!setting)
          return setting.Error();
        return {};
      });
  if (!replaced)
    co_return std::unexpected(DatabaseError(replaced.Error()));
  co_return ProjectWorkspaceResult<void>{};
}

} // namespace linecode::infrastructure
