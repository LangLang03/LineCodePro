#include <array>
#include "gtest_support.h"
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
#include "infrastructure/archive_json.h"
#include "infrastructure/linecode_zip.h"
#include "infrastructure/hux_data_archive_service.h"

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

  // The legacy plane an export has to carry; the export tests assert on it.
  huxerui::Task<DataArchiveResult<linecode::application::LegacyArchiveData>>
  ExportLegacy() override {
    ++export_legacy_calls;
    if (fail_export_legacy)
      co_return std::unexpected(
          DataArchiveError{"injected legacy export failure"});
    co_return legacy_export;
  }

  bool fail_import{};
  bool fail_export_legacy{};
  std::size_t export_legacy_calls{};
  linecode::application::LegacyArchiveData legacy_export;
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
  EXPECT_EXPRESSION(base.CreateDirectories());
  const File workspace = base.Child("workspace");
  EXPECT_EXPRESSION(workspace.CreateDirectories());
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
  EXPECT_EXPRESSION(encoded.has_value());
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
  EXPECT_EXPRESSION(parent.has_value());
  EXPECT_EXPRESSION(parent->CreateDirectories());
  EXPECT_EXPRESSION(file.WriteString(text));
}

std::string ReadText(const File &file) {
  const auto result = file.ReadString();
  EXPECT_EXPRESSION(result.Succeeded());
  return result.Value();
}

bool HasTransactionDirectory(const ImportFixture &fixture) {
  const auto children = fixture.roots.front().Parent()->ListChildren();
  EXPECT_EXPRESSION(children.Succeeded());
  for (const auto &child : children.Value()) {
    if (child.Name().starts_with(".import-"))
      return true;
  }
  return false;
}

// An export has to carry the legacy plane. Without it a legacy app importing
// our archive restores no models, no conversations and no settings, because it
// reads them from `async-storage.json` rather than our table snapshot.
struct ExportFixture final {
  std::shared_ptr<RecordingArchiveDatabase> database;
  std::shared_ptr<HuxDataArchiveService> service;
  std::optional<DataArchiveResult<linecode::application::PreparedDataArchive>>
      result;
};

std::shared_ptr<ExportFixture>
MakeExportFixture(const File &suite_root, std::string_view name) {
  const File base = suite_root.Child(name);
  EXPECT_EXPRESSION(base.CreateDirectories());
  const File workspace = base.Child("workspace");
  EXPECT_EXPRESSION(workspace.CreateDirectories());
  std::array roots{workspace.Child("home"), workspace.Child("project"),
                   workspace.Child("skills")};
  const auto database = std::make_shared<RecordingArchiveDatabase>();
  linecode::application::LegacyArchiveData legacy;
  linecode::domain::ModelConfig model;
  model.id = "m1";
  model.name = "Exported Model";
  model.model_id = "model-1";
  linecode::application::LegacyArchiveModel archive_model;
  archive_model.config = model;
  archive_model.selected = true;
  legacy.models.push_back(std::move(archive_model));
  legacy.selected_model_id = "m1";
  legacy.current_conversation_id = "c1";
  linecode::application::LegacyArchiveConversation conversation;
  conversation.id = "c1";
  conversation.title = "Exported Conversation";
  conversation.created_at = 10;
  conversation.updated_at = 20;
  legacy.conversations.push_back(std::move(conversation));
  legacy.settings.emplace("@linecode_chat_mode", "agent");
  database->legacy_export = std::move(legacy);
  return std::make_shared<ExportFixture>(ExportFixture{
      .database = database,
      .service = std::make_shared<HuxDataArchiveService>(
          database, base.Child("temporary"), roots[0], roots[1], roots[2]),
      .result = std::nullopt,
  });
}

struct Scenario final {
  std::vector<std::shared_ptr<ImportFixture>> fixtures;
  std::vector<std::shared_ptr<ExportFixture>> exports;
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
      for (const auto &fixture : scenario->exports) {
        fixture->result = co_await fixture->service->PrepareExport();
      }
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("archive-transaction-probe");
}

void AssertDatabaseFailureRollback(const ImportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(!fixture.result->has_value());
  EXPECT_EXPRESSION(fixture.database->replace_calls == 1U);
  EXPECT_EXPRESSION(fixture.database->legacy_calls == 0U);
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("original-home.txt")) == "home-old");
  EXPECT_EXPRESSION(ReadText(fixture.roots[1].Child("original-project.txt")) ==
         "project-old");
  EXPECT_EXPRESSION(ReadText(fixture.roots[2].Child("original-skill.txt")) ==
         "skill-old");
  EXPECT_EXPRESSION(!fixture.roots[0].Child("new-home.txt").Exists());
  EXPECT_EXPRESSION(!fixture.roots[1].Child("new-project.txt").Exists());
  EXPECT_EXPRESSION(!fixture.roots[2].Child("new-skill.txt").Exists());
  EXPECT_EXPRESSION(!HasTransactionDirectory(fixture));
}

void AssertMerge(const ImportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(fixture.result->has_value());
  EXPECT_EXPRESSION((*fixture.result)->restored_files == 4U);
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("keep.txt")) == "destination-only");
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("overwrite.txt")) == "archive-new");
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("new.txt")) == "new-home");
  EXPECT_EXPRESSION(ReadText(fixture.roots[1].Child("new.txt")) == "new-project");
  EXPECT_EXPRESSION(ReadText(fixture.roots[2].Child("new.txt")) == "new-skill");
  EXPECT_EXPRESSION(!HasTransactionDirectory(fixture));
}

void AssertLegacyRootReplace(const ImportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(fixture.result->has_value());
  EXPECT_EXPRESSION((*fixture.result)->restored_files == 3U);
  for (const auto &root : fixture.roots)
    EXPECT_EXPRESSION(!root.Child("old.txt").Exists());
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Resolve("nested/home.txt")) == "legacy-home");
  EXPECT_EXPRESSION(ReadText(fixture.roots[1].Child("project.txt")) == "legacy-project");
  EXPECT_EXPRESSION(ReadText(fixture.roots[2].Child("SKILL.md")) == "legacy-skill");
  EXPECT_EXPRESSION(!HasTransactionDirectory(fixture));
}

void AssertStagingFailureDoesNotMutate(const ImportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(!fixture.result->has_value());
  EXPECT_EXPRESSION(fixture.database->replace_calls == 0U);
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("old.txt")) == "home-old");
  EXPECT_EXPRESSION(ReadText(fixture.roots[1]) == "project-is-a-file");
  EXPECT_EXPRESSION(ReadText(fixture.roots[2].Child("old.txt")) == "skill-old");
  EXPECT_EXPRESSION(!HasTransactionDirectory(fixture));
}

void AssertInvalidLegacyPreflight(const ImportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(!fixture.result->has_value());
  EXPECT_EXPRESSION(fixture.database->replace_calls == 0U);
  EXPECT_EXPRESSION(fixture.database->legacy_calls == 0U);
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("old.txt")) == "home-old");
  EXPECT_EXPRESSION(!fixture.roots[0].Child("new.txt").Exists());
  EXPECT_EXPRESSION(!HasTransactionDirectory(fixture));
}

void AssertInvalidDatabasePreflight(const ImportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(!fixture.result->has_value());
  EXPECT_EXPRESSION(fixture.database->replace_calls == 0U);
  EXPECT_EXPRESSION(fixture.database->legacy_calls == 0U);
  EXPECT_EXPRESSION(ReadText(fixture.roots[0].Child("old.txt")) == "home-old");
  EXPECT_EXPRESSION(!fixture.roots[0].Child("new.txt").Exists());
  EXPECT_EXPRESSION(!HasTransactionDirectory(fixture));
}

} // namespace

// Reads back the archive `PrepareExport` staged and checks the legacy plane.
void AssertExportCarriesTheLegacyPlane(const ExportFixture &fixture) {
  EXPECT_EXPRESSION(fixture.result.has_value());
  EXPECT_EXPRESSION(fixture.result->has_value());
  EXPECT_EXPRESSION(fixture.database->export_legacy_calls == 1U);
  const auto bytes = fixture.result->value().file.ReadBytes();
  EXPECT_EXPRESSION(bytes.Succeeded());
  auto entries = linecode::infrastructure::ReadLineCodeZip(bytes.Value());
  EXPECT_EXPRESSION(entries.has_value());

  const auto find = [&entries](std::string_view name) -> const ZipEntryData * {
    for (const auto &entry : *entries) {
      if (entry.name == name)
        return &entry;
    }
    return nullptr;
  };

  const auto *storage = find("async-storage.json");
  EXPECT_EXPRESSION(storage != nullptr && "an export must carry async-storage.json");
  std::string text;
  for (const auto byte : storage->content)
    text.push_back(static_cast<char>(byte));
  // Empty or unparsable here is exactly the defect this pins: the legacy side
  // would restore nothing.
  auto parsed = linecode::infrastructure::archive_json::Parse(text);
  EXPECT_EXPRESSION(parsed.has_value());
  const auto *array = linecode::infrastructure::archive_json::AsArray(&*parsed);
  EXPECT_EXPRESSION(array != nullptr);
  EXPECT_EXPRESSION(!array->empty());

  const auto value_of = [array](std::string_view key) -> std::string {
    for (const auto &value : *array) {
      const auto *object = linecode::infrastructure::archive_json::AsObject(&value);
      if (object == nullptr)
        continue;
      const auto *name = linecode::infrastructure::archive_json::AsString(
          linecode::infrastructure::archive_json::Find(*object, "key"));
      if (name == nullptr || *name != key)
        continue;
      const auto *stored = linecode::infrastructure::archive_json::AsString(
          linecode::infrastructure::archive_json::Find(*object, "value"));
      return stored == nullptr ? std::string{} : *stored;
    }
    return {};
  };
  EXPECT_EXPRESSION(value_of("@lineai_selected_model") == "m1");
  EXPECT_EXPRESSION(value_of("@lineai_current_conversation") == "c1");
  EXPECT_EXPRESSION(value_of("@linecode_chat_mode") == "agent");
  const auto models = value_of("@lineai_models");
  EXPECT_EXPRESSION(models.find("Exported Model") != std::string::npos);
  // The row has to appear under the name the metadata entry points at.
  const auto *conversation_file = find("conversations/c1.json");
  EXPECT_EXPRESSION(conversation_file != nullptr && "an export must carry its conversations");
  std::string conversation_text;
  for (const auto byte : conversation_file->content)
    conversation_text.push_back(static_cast<char>(byte));
  auto conversation =
      linecode::infrastructure::archive_json::Parse(conversation_text);
  EXPECT_EXPRESSION(conversation.has_value());
  const auto *object =
      linecode::infrastructure::archive_json::AsObject(&*conversation);
  EXPECT_EXPRESSION(object != nullptr);
  EXPECT_EXPRESSION(*linecode::infrastructure::archive_json::AsString(
             linecode::infrastructure::archive_json::Find(*object, "id")) ==
         "c1");
}

TEST(archive_transaction_tests, LegacySuite) {
  const auto unique =
      std::to_string(std::chrono::steady_clock::now().time_since_epoch().count());
  const File suite_root(
      (std::filesystem::temp_directory_path() /
       ("linecode-archive-transaction-tests-" + unique))
          .string());
  EXPECT_EXPRESSION(suite_root.CreateDirectories());

  auto export_fixture = MakeExportFixture(suite_root, "export");

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
  active_scenario->exports = {export_fixture};
  const huxerui::Application application(
      ArchiveTransactionProbe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  for (std::size_t frame = 0; frame < 10'000U && !active_scenario->done;
       ++frame) {
    std::this_thread::sleep_for(std::chrono::milliseconds(1));
    ui.Pump(std::chrono::milliseconds(1));
  }
  EXPECT_EXPRESSION(active_scenario->done);

  AssertDatabaseFailureRollback(*database_failure);
  AssertMerge(*merge);
  AssertLegacyRootReplace(*legacy_paths);
  AssertStagingFailureDoesNotMutate(*staging_failure);
  AssertInvalidLegacyPreflight(*invalid_legacy);
  AssertExportCarriesTheLegacyPlane(*export_fixture);
  AssertInvalidDatabasePreflight(*invalid_database);

  active_scenario.reset();
  EXPECT_EXPRESSION(suite_root.DeleteRecursively());
}
