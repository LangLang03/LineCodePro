#include <algorithm>
#include "gtest_support.h"
#include <chrono>
#include <filesystem>
#include <iostream>
#include <memory>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>

#include <huxerui/file.h>
#include <huxerui/huxerui.h>
#include <huxerui/sqlite.h>
#include <huxerui/testing/ui_test.h>

#include "application/project_workspace_service.h"
#include "infrastructure/hux_workspace_file_store.h"
#include "infrastructure/sqlite_archive_database.h"
#include "infrastructure/sqlite_project_catalog_store.h"

namespace {

namespace fs = std::filesystem;
using huxerui::sqlite::Database;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;
using linecode::application::ProjectCatalogStore;
using linecode::application::ProjectWorkspaceError;
using linecode::application::ProjectWorkspaceErrorCode;
using linecode::application::ProjectWorkspaceResult;
using linecode::application::ProjectWorkspaceService;
using linecode::application::WorkspaceClock;
using linecode::domain::ProjectCatalog;
using linecode::domain::ProjectFileNode;
using linecode::domain::ProjectSource;
using linecode::infrastructure::HuxWorkspaceFileStore;
using linecode::infrastructure::ProjectCatalogScope;
using linecode::infrastructure::SqliteArchiveDatabase;
using linecode::infrastructure::SqliteProjectCatalogStore;

class TemporaryDirectory final {
public:
  TemporaryDirectory() {
    const auto seed =
        std::chrono::steady_clock::now().time_since_epoch().count();
    path_ = fs::temp_directory_path() /
            ("linecode-workspace-tests-" + std::to_string(seed));
    EXPECT_EXPRESSION(fs::create_directories(path_));
  }

  ~TemporaryDirectory() {
    std::error_code ignored;
    fs::remove_all(path_, ignored);
  }

  [[nodiscard]] const fs::path &Path() const noexcept { return path_; }

private:
  fs::path path_;
};

class FixedClock final : public WorkspaceClock {
public:
  [[nodiscard]] std::int64_t NowMilliseconds() const noexcept override {
    return now++;
  }

private:
  mutable std::int64_t now{1'000};
};

class FailingCatalog final : public ProjectCatalogStore {
public:
  huxerui::Task<ProjectWorkspaceResult<ProjectCatalog>> LoadCatalog() override {
    co_return catalog;
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  ReplaceCatalog(ProjectCatalog next) override {
    ++replace_calls;
    if (replace_calls == fail_on_call)
      co_return std::unexpected(ProjectWorkspaceError{
          .code = ProjectWorkspaceErrorCode::io,
          .message = "injected catalog failure",
      });
    catalog = std::move(next);
    co_return ProjectWorkspaceResult<void>{};
  }

  ProjectCatalog catalog;
  std::size_t replace_calls{};
  std::size_t fail_on_call{2};
};

const ProjectFileNode *FindNode(const ProjectFileNode &root,
                                std::string_view name) {
  if (root.name == name)
    return &root;
  for (const auto &child : root.children) {
    if (const auto *found = FindNode(child, name))
      return found;
  }
  return nullptr;
}

std::shared_ptr<HuxWorkspaceFileStore>
FileStore(const TemporaryDirectory &temporary) {
  const auto linecode = temporary.Path() / ".linecode";
  return std::make_shared<HuxWorkspaceFileStore>(
      huxerui::File{(linecode / "home").string()},
      huxerui::File{(linecode / "project").string()});
}

huxerui::Task<void> ProjectLifecyclePersistsAndKeepsProjectData() {
  TemporaryDirectory temporary;
  const auto linecode = temporary.Path() / ".linecode";
  auto catalog = std::make_shared<SqliteProjectCatalogStore>(
      huxerui::File{(temporary.Path() / "linecode.db").string()});
  auto files = FileStore(temporary);
  ProjectWorkspaceService service{catalog, files,
                                  std::make_shared<FixedClock>()};

  auto initial = co_await service.ListProjects();
  EXPECT_EXPRESSION(initial && initial->size() == 1);
  EXPECT_EXPRESSION(initial->front().id == linecode::domain::default_project_id);
  EXPECT_EXPRESSION(initial->front().selected);

  auto managed = co_await service.CreateManagedProject("  Demo / Project  ");
  EXPECT_EXPRESSION(managed && managed->id == "managed:demo-project");
  EXPECT_EXPRESSION(managed->label == "Demo-Project");
  EXPECT_EXPRESSION(managed->source == ProjectSource::managed);
  EXPECT_EXPRESSION(fs::is_directory(managed->path));
  EXPECT_EXPRESSION(fs::is_directory(fs::path{managed->path} / ".linecode" / "skills"));
  EXPECT_EXPRESSION(co_await service.CreateDirectory(managed->id, "src"));
  EXPECT_EXPRESSION(
      co_await service.WriteText(managed->id, "src/main.cpp", "int main() {}"));
  auto managed_text = co_await service.ReadText(managed->id, "src/main.cpp");
  EXPECT_EXPRESSION(managed_text && *managed_text == "int main() {}");
  auto managed_tree = co_await service.LoadTree(managed->id);
  EXPECT_EXPRESSION(managed_tree && FindNode(*managed_tree, "main.cpp"));

  const auto external_root = temporary.Path() / "external";
  EXPECT_EXPRESSION(fs::create_directories(external_root));
  auto external = co_await service.RegisterExternalProject(
      external_root.string(), " External ");
  EXPECT_EXPRESSION(external && external->source == ProjectSource::external);
  EXPECT_EXPRESSION(external->id.starts_with("external:"));
  EXPECT_EXPRESSION(external->label == "External");
  auto external_selected = co_await service.SelectedProject();
  EXPECT_EXPRESSION(external_selected && external_selected->id == external->id);

  auto selected = co_await service.SelectProject(managed->id);
  EXPECT_EXPRESSION(selected && selected->selected);
  EXPECT_EXPRESSION(co_await service.DeleteProject(managed->id));
  auto fallback_selected = co_await service.SelectedProject();
  EXPECT_EXPRESSION(fallback_selected &&
         fallback_selected->id == linecode::domain::default_project_id);
  EXPECT_EXPRESSION(fs::is_directory(
      managed->path)); // Legacy removal forgets the record only.

  ProjectWorkspaceService reloaded{catalog, files,
                                   std::make_shared<FixedClock>()};
  auto projects = co_await reloaded.ListProjects();
  EXPECT_EXPRESSION(projects && projects->size() == 2);
  EXPECT_EXPRESSION(std::ranges::any_of(*projects, [&](const auto &project) {
    return project.id == external->id &&
           project.source == ProjectSource::external;
  }));
  auto protected_delete = co_await reloaded.DeleteProject(
      std::string{linecode::domain::default_project_id});
  EXPECT_EXPRESSION(!protected_delete);
  EXPECT_EXPRESSION(protected_delete.error().code ==
         ProjectWorkspaceErrorCode::protected_project);
  co_return;
}

huxerui::Task<void> ManagedCreationRollsBackWhenCatalogCommitFails() {
  TemporaryDirectory temporary;
  auto catalog = std::make_shared<FailingCatalog>();
  auto files = FileStore(temporary);
  ProjectWorkspaceService service{catalog, files,
                                  std::make_shared<FixedClock>()};

  auto created = co_await service.CreateManagedProject("Rollback Me");
  EXPECT_EXPRESSION(!created);
  EXPECT_EXPRESSION(created.error().code == ProjectWorkspaceErrorCode::io);
  EXPECT_EXPRESSION(
      !fs::exists(temporary.Path() / ".linecode" / "project" / "Rollback-Me"));
  EXPECT_EXPRESSION(catalog->catalog.projects.size() == 1);
  EXPECT_EXPRESSION(catalog->catalog.selected_id == linecode::domain::default_project_id);
  co_return;
}

huxerui::Task<void> ProjectDeletionFailurePreservesCatalogAndSelection() {
  TemporaryDirectory temporary;
  auto catalog = std::make_shared<FailingCatalog>();
  catalog->fail_on_call = 3;
  auto files = FileStore(temporary);
  ProjectWorkspaceService service{catalog, files,
                                  std::make_shared<FixedClock>()};

  auto created = co_await service.CreateManagedProject("Keep Me");
  EXPECT_EXPRESSION(created);
  EXPECT_EXPRESSION(catalog->catalog.selected_id == created->id);
  auto removed = co_await service.DeleteProject(created->id);
  EXPECT_EXPRESSION(!removed && removed.error().code == ProjectWorkspaceErrorCode::io);
  EXPECT_EXPRESSION(catalog->catalog.selected_id == created->id);
  EXPECT_EXPRESSION(
      std::ranges::any_of(catalog->catalog.projects, [&](const auto &project) {
        return project.id == created->id && project.selected;
      }));
  EXPECT_EXPRESSION(fs::is_directory(created->path));
  co_return;
}

huxerui::Task<void>
CatalogRejectsInvalidReplacementWithoutLosingPreviousState() {
  TemporaryDirectory temporary;
  const auto database_file = temporary.Path() / "linecode.db";
  SqliteProjectCatalogStore store{huxerui::File{database_file.string()}};
  ProjectCatalog valid{
      .projects = {{.id = "default",
                    .label = "LineCode",
                    .path = temporary.Path().string(),
                    .source = ProjectSource::default_home,
                    .description = "home",
                    .selected = true,
                    .created_at = 1,
                    .updated_at = 2}},
      .selected_id = "default",
  };
  EXPECT_EXPRESSION(co_await store.ReplaceCatalog(valid));
  auto loaded = co_await store.LoadCatalog();
  EXPECT_EXPRESSION(loaded && *loaded == valid);

  auto invalid = valid;
  invalid.selected_id = "missing";
  EXPECT_EXPRESSION(!(co_await store.ReplaceCatalog(invalid)));
  loaded = co_await store.LoadCatalog();
  EXPECT_EXPRESSION(loaded && *loaded == valid);
  co_return;
}

huxerui::Task<void> LegacyCatalogMigratesInPlaceAndRemainsArchiveVisible() {
  TemporaryDirectory temporary;
  const auto database_file = temporary.Path() / "linecode.db";
  auto database = co_await Database::OpenAsync(
      huxerui::File{database_file.string()},
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  EXPECT_EXPRESSION(database);
  auto seeded = co_await database->TransactionAsync(
      [root = temporary.Path().string()](
          Transaction &transaction) -> huxerui::sqlite::Result<void> {
        auto projects = transaction.Execute(
            "CREATE TABLE projects (id TEXT PRIMARY KEY, label TEXT NOT NULL, "
            "path TEXT NOT NULL, source TEXT NOT NULL, description TEXT, "
            "selected INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, "
            "updated_at INTEGER NOT NULL)");
        if (!projects)
          return projects.Error();
        auto settings = transaction.Execute(
            "CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, "
            "type TEXT NOT NULL DEFAULT 'string', updated_at INTEGER NOT "
            "NULL)");
        if (!settings)
          return settings.Error();
        auto home = transaction.Execute(
            "INSERT INTO projects VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            std::string{"default"}, std::string{"Legacy Home"}, root,
            std::string{"default"}, std::string{"legacy"}, false,
            std::int64_t{1}, std::int64_t{2});
        if (!home)
          return home.Error();
        auto managed = transaction.Execute(
            "INSERT INTO projects VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            std::string{"managed:legacy"}, std::string{"Legacy"}, root,
            std::string{"managed"}, std::string{"legacy managed"}, true,
            std::int64_t{3}, std::int64_t{4});
        if (!managed)
          return managed.Error();
        auto ssh = transaction.Execute(
            "INSERT INTO projects VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            std::string{"ssh:default"}, std::string{"SSH"}, std::string{},
            std::string{"ssh"}, std::string{"remote"}, true, std::int64_t{5},
            std::int64_t{6});
        if (!ssh)
          return ssh.Error();
        auto selected = transaction.Execute(
            "INSERT INTO settings VALUES (?, ?, 'string', ?)",
            std::string{"@linecode_selected_project_local"},
            std::string{"managed:legacy"}, std::int64_t{7});
        if (!selected)
          return selected.Error();
        return {};
      });
  EXPECT_EXPRESSION(seeded);

  SqliteProjectCatalogStore store{huxerui::File{database_file.string()}};
  auto loaded = co_await store.LoadCatalog();
  EXPECT_EXPRESSION(loaded && loaded->projects.size() == 2);
  EXPECT_EXPRESSION(loaded->selected_id == "managed:legacy");
  EXPECT_EXPRESSION(std::ranges::none_of(loaded->projects, [](const auto &project) {
    return project.id.starts_with("ssh:");
  }));

  auto replacement = *loaded;
  replacement.projects.front().label = "Migrated local";
  EXPECT_EXPRESSION(co_await store.ReplaceCatalog(replacement));
  auto raw_projects = co_await database->QueryAsync<std::string>(
      "SELECT id FROM projects ORDER BY id ASC",
      [](const RowView &row) { return row.Get<std::string>(0); });
  EXPECT_EXPRESSION(raw_projects && raw_projects->size() == 3);
  EXPECT_EXPRESSION(std::ranges::find(*raw_projects, "ssh:default") !=
         raw_projects->end());
  // The archive exporter enumerates ordinary SQLite tables and includes the
  // legacy `projects` name; writing in place keeps these rows archive-visible.
  auto table = co_await database->QueryAsync<std::string>(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name = "
      "'projects'",
      [](const RowView &row) { return row.Get<std::string>(0); });
  EXPECT_EXPRESSION(table && table->size() == 1);

  auto trigger = co_await database->ExecuteAsync(
      "CREATE TRIGGER reject_project BEFORE INSERT ON projects "
      "WHEN NEW.label = 'explode' BEGIN SELECT RAISE(ABORT, 'injected'); END");
  EXPECT_EXPRESSION(trigger);
  auto rejected = replacement;
  rejected.projects.front().label = "explode";
  auto failed = co_await store.ReplaceCatalog(std::move(rejected));
  EXPECT_EXPRESSION(!failed && failed.error().code == ProjectWorkspaceErrorCode::io);
  auto preserved = co_await store.LoadCatalog();
  EXPECT_EXPRESSION(preserved && *preserved == replacement);
  auto selected_setting = co_await database->QueryAsync<std::string>(
      "SELECT value FROM settings WHERE key = ?",
      [](const RowView &row) { return row.Get<std::string>(0); },
      std::string{"@linecode_selected_project_local"});
  EXPECT_EXPRESSION(selected_setting && selected_setting->size() == 1 &&
         selected_setting->front() == "managed:legacy");
  co_return;
}

huxerui::Task<void> ProjectCatalogSurvivesArchiveRoundTrip() {
  TemporaryDirectory temporary;
  const auto source_file = temporary.Path() / "source.db";
  const auto destination_file = temporary.Path() / "destination.db";
  ProjectCatalog catalog{
      .projects = {{.id = "external:/archive-visible",
                    .label = "Archive project",
                    .path = temporary.Path().string(),
                    .source = ProjectSource::external,
                    .description = "visible through database.json",
                    .selected = true,
                    .created_at = 11,
                    .updated_at = 12}},
      .selected_id = "external:/archive-visible",
  };
  SqliteProjectCatalogStore source_store{huxerui::File{source_file.string()}};
  EXPECT_EXPRESSION(co_await source_store.ReplaceCatalog(catalog));
  SqliteArchiveDatabase source_archive{huxerui::File{source_file.string()}};
  auto exported = co_await source_archive.ExportRedacted();
  EXPECT_EXPRESSION(exported);
  EXPECT_EXPRESSION(exported->json.contains("\"projects\""));
  EXPECT_EXPRESSION(exported->json.contains("external:/archive-visible"));

  SqliteArchiveDatabase destination_archive{
      huxerui::File{destination_file.string()}};
  auto restored =
      co_await destination_archive.ReplaceFromSnapshot(exported->json);
  EXPECT_EXPRESSION(restored);
  SqliteProjectCatalogStore destination_store{
      huxerui::File{destination_file.string()}};
  auto loaded = co_await destination_store.LoadCatalog();
  EXPECT_EXPRESSION(loaded && *loaded == catalog);
  co_return;
}

huxerui::Task<void> LocalAndSshCatalogsRemainIsolated() {
  TemporaryDirectory temporary;
  const auto database_file = temporary.Path() / "linecode.db";
  SqliteProjectCatalogStore local{huxerui::File{database_file.string()}};
  SqliteProjectCatalogStore ssh{huxerui::File{database_file.string()},
                                ProjectCatalogScope::ssh};
  const ProjectCatalog local_catalog{
      .projects = {{.id = "default",
                    .label = "LineCode",
                    .path = temporary.Path().string(),
                    .source = ProjectSource::default_home,
                    .description = "local",
                    .selected = true,
                    .created_at = 1,
                    .updated_at = 2}},
      .selected_id = "default",
  };
  const ProjectCatalog ssh_catalog{
      .projects = {{.id = "ssh:default",
                    .label = "SSH",
                    .path = {},
                    .source = ProjectSource::ssh,
                    .description = "remote login directory",
                    .selected = true,
                    .created_at = 3,
                    .updated_at = 4}},
      .selected_id = "ssh:default",
  };
  EXPECT_EXPRESSION(co_await local.ReplaceCatalog(local_catalog));
  EXPECT_EXPRESSION(co_await ssh.ReplaceCatalog(ssh_catalog));
  auto loaded_local = co_await local.LoadCatalog();
  auto loaded_ssh = co_await ssh.LoadCatalog();
  EXPECT_EXPRESSION(loaded_local && *loaded_local == local_catalog);
  EXPECT_EXPRESSION(loaded_ssh && *loaded_ssh == ssh_catalog);

  auto updated_local = local_catalog;
  updated_local.projects.front().label = "Local only";
  EXPECT_EXPRESSION(co_await local.ReplaceCatalog(updated_local));
  loaded_ssh = co_await ssh.LoadCatalog();
  EXPECT_EXPRESSION(loaded_ssh && *loaded_ssh == ssh_catalog);
  co_return;
}

struct AsyncScenario final {
  bool done{};
  bool passed{};
};

std::shared_ptr<AsyncScenario> async_scenario;

huxerui::View AsyncProjectProbe() {
  const auto current = async_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    const auto handle = tasks.Launch([current]() -> huxerui::Task<void> {
      co_await ProjectLifecyclePersistsAndKeepsProjectData();
      co_await ManagedCreationRollsBackWhenCatalogCommitFails();
      co_await ProjectDeletionFailurePreservesCatalogAndSelection();
      co_await CatalogRejectsInvalidReplacementWithoutLosingPreviousState();
      co_await LegacyCatalogMigratesInPlaceAndRemainsArchiveVisible();
      co_await ProjectCatalogSurvivesArchiveRoundTrip();
      co_await LocalAndSshCatalogsRemainIsolated();
      current->passed = true;
      current->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("project-workspace-service-probe");
}

void RunAsyncProjectTests() {
  async_scenario = std::make_shared<AsyncScenario>();
  {
    const huxerui::Application app(AsyncProjectProbe,
                                   {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 5'000 && !async_scenario->done; ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
    }
    EXPECT_EXPRESSION(async_scenario->done);
    EXPECT_EXPRESSION(async_scenario->passed);
  }
  async_scenario.reset();
}

void RecursiveFileOperationsAreTransactionalAtCommitBoundaries() {
  TemporaryDirectory temporary;
  auto files = FileStore(temporary);
  auto root = files->PrepareDefaultProject();
  EXPECT_EXPRESSION(root);

  EXPECT_EXPRESSION(files->CreateDirectory(*root, "src"));
  EXPECT_EXPRESSION(files->CreateDirectory(*root, "src/nested"));
  EXPECT_EXPRESSION(files->CreateFile(*root, "src/nested/a.txt"));
  EXPECT_EXPRESSION(files->WriteText(*root, "src/nested/a.txt", "alpha"));
  auto original_text = files->ReadText(*root, "src/nested/a.txt");
  EXPECT_EXPRESSION(original_text && *original_text == "alpha");

  auto tree = files->LoadTree(*root);
  EXPECT_EXPRESSION(tree);
  const auto *nested = FindNode(*tree, "nested");
  EXPECT_EXPRESSION(nested && nested->directory);
  EXPECT_EXPRESSION(FindNode(*tree, "a.txt"));

  EXPECT_EXPRESSION(files->Rename(*root, "src/nested/a.txt", "b.txt"));
  EXPECT_EXPRESSION(files->Copy(*root, "src", "src-copy"));
  auto copied_text = files->ReadText(*root, "src-copy/nested/b.txt");
  EXPECT_EXPRESSION(copied_text && *copied_text == "alpha");
  EXPECT_EXPRESSION(files->Move(*root, "src-copy/nested/b.txt", "moved.txt"));
  auto moved_text = files->ReadText(*root, "moved.txt");
  EXPECT_EXPRESSION(moved_text && *moved_text == "alpha");

  EXPECT_EXPRESSION(files->CreateFile(*root, "occupied.txt"));
  EXPECT_EXPRESSION(files->WriteText(*root, "occupied.txt", "keep"));
  auto conflict = files->Copy(*root, "moved.txt", "occupied.txt");
  EXPECT_EXPRESSION(!conflict &&
         conflict.error().code == ProjectWorkspaceErrorCode::conflict);
  auto preserved_target = files->ReadText(*root, "occupied.txt");
  auto preserved_source = files->ReadText(*root, "moved.txt");
  EXPECT_EXPRESSION(preserved_target && *preserved_target == "keep");
  EXPECT_EXPRESSION(preserved_source && *preserved_source == "alpha");

  auto move_conflict = files->Move(*root, "moved.txt", "occupied.txt");
  EXPECT_EXPRESSION(!move_conflict &&
         move_conflict.error().code == ProjectWorkspaceErrorCode::conflict);
  auto move_failure_preserved = files->ReadText(*root, "moved.txt");
  EXPECT_EXPRESSION(move_failure_preserved && *move_failure_preserved == "alpha");

  auto bad_write = files->WriteText(*root, "occupied.txt/child", "damage");
  EXPECT_EXPRESSION(!bad_write);
  auto write_failure_preserved = files->ReadText(*root, "occupied.txt");
  EXPECT_EXPRESSION(write_failure_preserved && *write_failure_preserved == "keep");

  EXPECT_EXPRESSION(files->Delete(*root, "src-copy"));
  EXPECT_EXPRESSION(!fs::exists(fs::path{*root} / "src-copy"));
  EXPECT_EXPRESSION(files->Delete(*root, "moved.txt"));
  EXPECT_EXPRESSION(!fs::exists(fs::path{*root} / "moved.txt"));
}

void TraversalAndSymbolicLinksNeverEscapeWorkspace() {
  TemporaryDirectory temporary;
  auto files = FileStore(temporary);
  auto root = files->PrepareDefaultProject();
  EXPECT_EXPRESSION(root);
  const auto outside = temporary.Path() / "outside.txt";
  EXPECT_EXPRESSION(huxerui::File{outside.string()}.WriteString("outside"));

  const auto traversal = files->WriteText(*root, "../outside.txt", "changed");
  EXPECT_EXPRESSION(!traversal);
  EXPECT_EXPRESSION(traversal.error().code ==
         ProjectWorkspaceErrorCode::outside_workspace);
  EXPECT_EXPRESSION(huxerui::File{outside.string()}.ReadString().Value() == "outside");
  EXPECT_EXPRESSION(!files->Delete(*root, ".linecode"));

  std::error_code link_error;
  fs::create_symlink(outside, fs::path{*root} / "outside-link", link_error);
  if (!link_error) {
    auto tree = files->LoadTree(*root);
    EXPECT_EXPRESSION(tree);
    const auto *link = FindNode(*tree, "outside-link");
    EXPECT_EXPRESSION(link && link->symbolic_link && !link->directory);
    auto read = files->ReadText(*root, "outside-link");
    EXPECT_EXPRESSION(!read);
    EXPECT_EXPRESSION(read.error().code == ProjectWorkspaceErrorCode::symbolic_link);
    auto removed = files->Delete(*root, "outside-link");
    EXPECT_EXPRESSION(!removed);
    EXPECT_EXPRESSION(huxerui::File{outside.string()}.ReadString().Value() == "outside");

    EXPECT_EXPRESSION(files->CreateDirectory(*root, "copy-with-link"));
    std::error_code nested_link_error;
    fs::create_symlink(outside,
                       fs::path{*root} / "copy-with-link" / "nested-link",
                       nested_link_error);
    EXPECT_EXPRESSION(!nested_link_error);
    auto copied = files->Copy(*root, "copy-with-link", "rejected-copy");
    EXPECT_EXPRESSION(!copied &&
           copied.error().code == ProjectWorkspaceErrorCode::symbolic_link);
    EXPECT_EXPRESSION(!fs::exists(fs::path{*root} / "rejected-copy"));
    EXPECT_EXPRESSION(std::ranges::none_of(
        fs::directory_iterator{*root}, [](const auto &entry) {
          return entry.path().filename().string().starts_with(
              ".linecode-copy-stage-");
        }));
  }

  std::error_code root_link_error;
  fs::create_directory_symlink(
      fs::path{*root}, temporary.Path() / "workspace-link", root_link_error);
  if (!root_link_error) {
    auto linked_root =
        files->ResolveDirectory((temporary.Path() / "workspace-link").string());
    EXPECT_EXPRESSION(!linked_root);
    EXPECT_EXPRESSION(linked_root.error().code ==
           ProjectWorkspaceErrorCode::symbolic_link);
  }
}

} // namespace

TEST(project_workspace_service_tests, LegacySuite) {
  RecursiveFileOperationsAreTransactionalAtCommitBoundaries();
  TraversalAndSymbolicLinksNeverEscapeWorkspace();
  RunAsyncProjectTests();
  std::cout << "project workspace service tests passed\n";
}
