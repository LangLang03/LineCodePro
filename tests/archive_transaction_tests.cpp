#include <array>
#include <cassert>
#include <chrono>
#include <filesystem>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/ports/archive_database.h"
#include "infrastructure/hux_data_archive_service.h"
#include "infrastructure/linecode_zip.h"

namespace {

using huxerui::Bytes;
using huxerui::File;
using linecode::application::ArchiveDatabase;
using linecode::application::ArchiveDatabaseExport;
using linecode::application::DataArchiveError;
using linecode::application::DataArchiveResult;
using linecode::domain::ArchiveImportMode;
using linecode::domain::ArchiveSummary;
using linecode::infrastructure::HuxDataArchiveService;
using linecode::infrastructure::WriteLineCodeZip;
using linecode::infrastructure::ZipEntryData;

constexpr std::string_view kDatabaseSnapshot =
    R"({"format":"linecode-database","schemaVersion":4,"tables":{}})";

Bytes TextBytes(std::string_view text) {
  Bytes bytes;
  bytes.reserve(text.size());
  for (const unsigned char value : text)
    bytes.push_back(static_cast<std::byte>(value));
  return bytes;
}

class RecordingArchiveDatabase final : public ArchiveDatabase {
public:
  huxerui::Task<DataArchiveResult<ArchiveDatabaseExport>>
  ExportRedacted() override {
    co_return ArchiveDatabaseExport{};
  }

  huxerui::Task<DataArchiveResult<ArchiveSummary>>
  ReplaceFromSnapshot(std::string json) override {
    ++replace_calls;
    snapshots.push_back(std::move(json));
    if (fail_import)
      co_return std::unexpected(DataArchiveError{"injected database failure"});
    co_return summary;
  }

  huxerui::Task<DataArchiveResult<ArchiveSummary>>
  ImportLegacy(linecode::application::LegacyArchiveData data,
               ArchiveImportMode mode) override {
    ++legacy_calls;
    legacy_modes.push_back(mode);
    legacy_payloads.push_back(std::move(data));
    if (fail_import)
      co_return std::unexpected(DataArchiveError{"injected database failure"});
    co_return summary;
  }

  bool fail_import{};
  ArchiveSummary summary;
  std::size_t replace_calls{};
  std::size_t legacy_calls{};
  std::vector<std::string> snapshots;
  std::vector<ArchiveImportMode> legacy_modes;
  std::vector<linecode::application::LegacyArchiveData> legacy_payloads;
};

struct ImportFixture final {
  File base;
  std::array<File, 3> roots;
  std::shared_ptr<RecordingArchiveDatabase> database;
  std::shared_ptr<HuxDataArchiveService> service;
  Bytes archive;
  ArchiveImportMode mode;
  std::optional<DataArchiveResult<ArchiveSummary>> result;
};

std::shared_ptr<ImportFixture>
MakeFixture(const File &suite_root, std::string_view name,
            std::vector<ZipEntryData> entries, ArchiveImportMode mode,
            bool database_fails = false) {
  const File base = suite_root.Child(name);
  assert(base.CreateDirectories());
  const File workspace = base.Child("workspace");
  assert(workspace.CreateDirectories());
  std::array roots{workspace.Child("home"), workspace.Child("project"),
                   workspace.Child("skills")};
  const auto database = std::make_shared<RecordingArchiveDatabase>();
  database->fail_import = database_fails;
  database->summary = {
      .conversations = 1,
      .models = 2,
      .settings = 3,
  };
  const auto encoded = WriteLineCodeZip(entries);
  assert(encoded.has_value());
  return std::make_shared<ImportFixture>(ImportFixture{
      .base = base,
      .roots = roots,
      .database = database,
      .service = std::make_shared<HuxDataArchiveService>(
          database, base.Child("temporary"), roots[0], roots[1], roots[2]),
      .archive = *encoded,
      .mode = mode,
      .result = std::nullopt,
  });
}

void WriteText(const File &file, std::string_view text) {
  const auto parent = file.Parent();
  assert(parent.has_value());
  assert(parent->CreateDirectories());
  assert(file.WriteString(text));
}

std::string ReadText(const File &file) {
  const auto result = file.ReadString();
  assert(result.Succeeded());
  return result.Value();
}

bool HasTransactionDirectory(const ImportFixture &fixture) {
  const auto children = fixture.roots.front().Parent()->ListChildren();
  assert(children.Succeeded());
  for (const auto &child : children.Value()) {
    if (child.Name().starts_with(".import-"))
      return true;
  }
  return false;
}

struct Scenario final {
  std::vector<std::shared_ptr<ImportFixture>> fixtures;
  bool done{};
};

std::shared_ptr<Scenario> active_scenario;

huxerui::View ArchiveTransactionProbe() {
  const auto scenario = active_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      for (const auto &fixture : scenario->fixtures) {
        fixture->result = co_await fixture->service->ImportBytes(
            fixture->archive, fixture->mode);
      }
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("archive-transaction-probe");
}

void AssertDatabaseFailureRollback(const ImportFixture &fixture) {
  assert(fixture.result.has_value());
  assert(!fixture.result->has_value());
  assert(fixture.database->replace_calls == 1U);
  assert(fixture.database->legacy_calls == 0U);
  assert(ReadText(fixture.roots[0].Child("original-home.txt")) == "home-old");
  assert(ReadText(fixture.roots[1].Child("original-project.txt")) ==
         "project-old");
  assert(ReadText(fixture.roots[2].Child("original-skill.txt")) ==
         "skill-old");
  assert(!fixture.roots[0].Child("new-home.txt").Exists());
  assert(!fixture.roots[1].Child("new-project.txt").Exists());
  assert(!fixture.roots[2].Child("new-skill.txt").Exists());
  assert(!HasTransactionDirectory(fixture));
}

void AssertMerge(const ImportFixture &fixture) {
  assert(fixture.result.has_value());
  assert(fixture.result->has_value());
  assert((*fixture.result)->restored_files == 4U);
  assert(ReadText(fixture.roots[0].Child("keep.txt")) == "destination-only");
  assert(ReadText(fixture.roots[0].Child("overwrite.txt")) == "archive-new");
  assert(ReadText(fixture.roots[0].Child("new.txt")) == "new-home");
  assert(ReadText(fixture.roots[1].Child("new.txt")) == "new-project");
  assert(ReadText(fixture.roots[2].Child("new.txt")) == "new-skill");
  assert(!HasTransactionDirectory(fixture));
}

void AssertLegacyRootReplace(const ImportFixture &fixture) {
  assert(fixture.result.has_value());
  assert(fixture.result->has_value());
  assert((*fixture.result)->restored_files == 3U);
  for (const auto &root : fixture.roots)
    assert(!root.Child("old.txt").Exists());
  assert(ReadText(fixture.roots[0].Resolve("nested/home.txt")) == "legacy-home");
  assert(ReadText(fixture.roots[1].Child("project.txt")) == "legacy-project");
  assert(ReadText(fixture.roots[2].Child("SKILL.md")) == "legacy-skill");
  assert(!HasTransactionDirectory(fixture));
}

void AssertStagingFailureDoesNotMutate(const ImportFixture &fixture) {
  assert(fixture.result.has_value());
  assert(!fixture.result->has_value());
  assert(fixture.database->replace_calls == 0U);
  assert(ReadText(fixture.roots[0].Child("old.txt")) == "home-old");
  assert(ReadText(fixture.roots[1]) == "project-is-a-file");
  assert(ReadText(fixture.roots[2].Child("old.txt")) == "skill-old");
  assert(!HasTransactionDirectory(fixture));
}

void AssertInvalidLegacyPreflight(const ImportFixture &fixture) {
  assert(fixture.result.has_value());
  assert(!fixture.result->has_value());
  assert(fixture.database->replace_calls == 0U);
  assert(fixture.database->legacy_calls == 0U);
  assert(ReadText(fixture.roots[0].Child("old.txt")) == "home-old");
  assert(!fixture.roots[0].Child("new.txt").Exists());
  assert(!HasTransactionDirectory(fixture));
}

void AssertInvalidDatabasePreflight(const ImportFixture &fixture) {
  assert(fixture.result.has_value());
  assert(!fixture.result->has_value());
  assert(fixture.database->replace_calls == 0U);
  assert(fixture.database->legacy_calls == 0U);
  assert(ReadText(fixture.roots[0].Child("old.txt")) == "home-old");
  assert(!fixture.roots[0].Child("new.txt").Exists());
  assert(!HasTransactionDirectory(fixture));
}

} // namespace

int main() {
  const auto unique =
      std::to_string(std::chrono::steady_clock::now().time_since_epoch().count());
  const File suite_root(
      (std::filesystem::temp_directory_path() /
       ("linecode-archive-transaction-tests-" + unique))
          .string());
  assert(suite_root.CreateDirectories());

  auto database_failure = MakeFixture(
      suite_root, "database-failure",
      {
          {"database.json", TextBytes(kDatabaseSnapshot)},
          {"home/new-home.txt", TextBytes("home-new")},
          {"project/new-project.txt", TextBytes("project-new")},
          {"skills/new-skill.txt", TextBytes("skill-new")},
      },
      ArchiveImportMode::replace, true);
  WriteText(database_failure->roots[0].Child("original-home.txt"), "home-old");
  WriteText(database_failure->roots[1].Child("original-project.txt"),
            "project-old");
  WriteText(database_failure->roots[2].Child("original-skill.txt"),
            "skill-old");

  auto merge = MakeFixture(
      suite_root, "merge",
      {
          {"database.json", TextBytes(kDatabaseSnapshot)},
          {"home/overwrite.txt", TextBytes("archive-new")},
          {"home/new.txt", TextBytes("new-home")},
          {"project/new.txt", TextBytes("new-project")},
          {"skills/new.txt", TextBytes("new-skill")},
      },
      ArchiveImportMode::merge);
  WriteText(merge->roots[0].Child("keep.txt"), "destination-only");
  WriteText(merge->roots[0].Child("overwrite.txt"), "destination-old");

  auto legacy_paths = MakeFixture(
      suite_root, "legacy-paths",
      {
          {"database.json", TextBytes(kDatabaseSnapshot)},
          {".linecode/home/nested/home.txt", TextBytes("legacy-home")},
          {".linecode/project/project.txt", TextBytes("legacy-project")},
          {".linecode/skills/SKILL.md", TextBytes("legacy-skill")},
      },
      ArchiveImportMode::replace);
  for (const auto &root : legacy_paths->roots)
    WriteText(root.Child("old.txt"), "old");

  auto staging_failure = MakeFixture(
      suite_root, "staging-failure",
      {
          {"database.json", TextBytes(kDatabaseSnapshot)},
          {"home/new.txt", TextBytes("new-home")},
      },
      ArchiveImportMode::merge);
  WriteText(staging_failure->roots[0].Child("old.txt"), "home-old");
  WriteText(staging_failure->roots[1], "project-is-a-file");
  WriteText(staging_failure->roots[2].Child("old.txt"), "skill-old");

  auto invalid_legacy = MakeFixture(
      suite_root, "invalid-legacy",
      {
          {"async-storage.json",
           TextBytes(
               R"([{"key":"@lineai_conv_broken","value":"{\"storage\":\"file\",\"fileName\":\"missing.json\"}"}])")},
          {"home/new.txt", TextBytes("new-home")},
      },
      ArchiveImportMode::replace);
  WriteText(invalid_legacy->roots[0].Child("old.txt"), "home-old");

  auto invalid_database = MakeFixture(
      suite_root, "invalid-database",
      {
          {"database.json", TextBytes("not a database snapshot")},
          {"home/new.txt", TextBytes("new-home")},
      },
      ArchiveImportMode::replace);
  WriteText(invalid_database->roots[0].Child("old.txt"), "home-old");

  active_scenario = std::make_shared<Scenario>();
  active_scenario->fixtures = {database_failure, merge, legacy_paths,
                               staging_failure, invalid_legacy,
                               invalid_database};
  const huxerui::Application application(
      ArchiveTransactionProbe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  for (std::size_t frame = 0; frame < 10'000U && !active_scenario->done;
       ++frame) {
    std::this_thread::sleep_for(std::chrono::milliseconds(1));
    ui.Pump(std::chrono::milliseconds(1));
  }
  assert(active_scenario->done);

  AssertDatabaseFailureRollback(*database_failure);
  AssertMerge(*merge);
  AssertLegacyRootReplace(*legacy_paths);
  AssertStagingFailureDoesNotMutate(*staging_failure);
  AssertInvalidLegacyPreflight(*invalid_legacy);
  AssertInvalidDatabasePreflight(*invalid_database);

  active_scenario.reset();
  assert(suite_root.DeleteRecursively());
}
