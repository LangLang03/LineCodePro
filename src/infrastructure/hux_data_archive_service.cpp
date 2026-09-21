#include "infrastructure/hux_data_archive_service.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstddef>
#include <expected>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/data_archive.h"
#include "infrastructure/archive_validation.h"
#include "infrastructure/linecode_zip.h"

namespace linecode::infrastructure {
namespace {

using application::DataArchiveError;
using application::DataArchiveResult;
using huxerui::Bytes;
using huxerui::File;
using huxerui::FileType;
using huxerui::IoErrorCode;

constexpr std::array<std::string_view, 3> kRootNames{"home", "project",
                                                     "skills"};

struct WorkspaceImport final {
  File transaction_root;
  std::array<File, 3> roots;
  std::array<File, 3> staged;
  std::array<File, 3> backups;
  std::array<bool, 3> had_original{};
  std::array<bool, 3> swapped{};
  bool rollback_complete{true};
  std::uint64_t restored_files{};
};

Bytes TextBytes(std::string_view text) {
  Bytes bytes;
  bytes.reserve(text.size());
  for (const unsigned char value : text) {
    bytes.push_back(static_cast<std::byte>(value));
  }
  return bytes;
}

std::string BytesText(std::span<const std::byte> bytes) {
  std::string text;
  text.reserve(bytes.size());
  for (const auto value : bytes) {
    text.push_back(
        static_cast<char>(std::to_integer<unsigned char>(value)));
  }
  return text;
}

huxerui::Task<DataArchiveResult<void>> AppendDirectory(
    const File &directory, std::string archive_prefix,
    std::vector<ZipEntryData> &entries, std::size_t depth) {
  if (depth > kMaximumArchivePathDepth) {
    co_return std::unexpected(
        DataArchiveError{"workspace nesting exceeds archive safety limit"});
  }
  auto listed = co_await directory.ListChildrenAsync();
  if (!listed.Succeeded()) {
    if (listed.Error().code == IoErrorCode::NotFound) {
      co_return DataArchiveResult<void>{};
    }
    co_return std::unexpected(DataArchiveError{listed.Error().message});
  }
  for (const auto &child : listed.Value()) {
    auto info = co_await child.StatAsync();
    if (!info.Succeeded()) {
      co_return std::unexpected(DataArchiveError{info.Error().message});
    }
    const std::string child_name = child.Name();
    if (child_name.empty() || child_name.find('/') != std::string::npos ||
        child_name.find('\\') != std::string::npos) {
      co_return std::unexpected(
          DataArchiveError{"workspace contains an invalid file name"});
    }
    std::string archive_name = archive_prefix + "/" + child_name;
    if (info.Value().type == FileType::Directory) {
      auto appended = co_await AppendDirectory(
          child, std::move(archive_name), entries, depth + 1U);
      if (!appended) {
        co_return std::unexpected(std::move(appended.error()));
      }
    } else if (info.Value().type == FileType::File) {
      auto content = co_await child.ReadBytesAsync();
      if (!content.Succeeded()) {
        co_return std::unexpected(DataArchiveError{content.Error().message});
      }
      entries.push_back(
          ZipEntryData{std::move(archive_name), std::move(content).Value()});
    }
  }
  co_return DataArchiveResult<void>{};
}

const ZipEntryData *FindEntry(const std::vector<ZipEntryData> &entries,
                              std::string_view name) {
  const auto found =
      std::ranges::find(entries, name, &ZipEntryData::name);
  return found == entries.end() ? nullptr : &*found;
}

std::string_view RootRelativePath(std::string_view entry,
                                  std::string_view root) {
  if (!entry.starts_with(root) || entry.size() <= root.size() + 1 ||
      entry[root.size()] != '/') {
    return {};
  }
  return entry.substr(root.size() + 1);
}

huxerui::Task<DataArchiveResult<std::uint64_t>> RestoreRoot(
    const std::vector<ZipEntryData> &entries, std::string_view root_name,
    const File &target, bool replace) {
  if (replace && target.Exists() && !co_await target.DeleteRecursivelyAsync()) {
    co_return std::unexpected(
        DataArchiveError{"cannot clear existing workspace root"});
  }
  if (!co_await target.CreateDirectoriesAsync()) {
    co_return std::unexpected(DataArchiveError{"cannot create workspace root"});
  }
  std::uint64_t restored{};
  for (const auto &entry : entries) {
    std::string_view relative = RootRelativePath(entry.name, root_name);
    if (relative.empty()) {
      const std::string nested = ".linecode/" + std::string{root_name};
      relative = RootRelativePath(entry.name, nested);
    }
    if (relative.empty()) {
      continue;
    }
    File output = target.Resolve(relative);
    const auto parent = output.Parent();
    if (!parent || !co_await parent->CreateDirectoriesAsync() ||
        !co_await output.WriteBytesAsync(entry.content)) {
      co_return std::unexpected(
          DataArchiveError{"cannot restore workspace file: " + entry.name});
    }
    ++restored;
  }
  co_return restored;
}

huxerui::Task<DataArchiveResult<void>> CopyDirectoryContents(
    const File &source, const File &destination, std::size_t depth) {
  if (depth > kMaximumArchivePathDepth) {
    co_return std::unexpected(
        DataArchiveError{"workspace nesting exceeds archive safety limit"});
  }
  auto listed = co_await source.ListChildrenAsync();
  if (!listed.Succeeded()) {
    co_return std::unexpected(DataArchiveError{listed.Error().message});
  }
  if (!co_await destination.CreateDirectoriesAsync()) {
    co_return std::unexpected(
        DataArchiveError{"cannot create workspace staging directory"});
  }
  for (const auto &child : listed.Value()) {
    const std::string name = child.Name();
    if (name.empty() || name.find('/') != std::string::npos ||
        name.find('\\') != std::string::npos) {
      co_return std::unexpected(
          DataArchiveError{"workspace contains an invalid file name"});
    }
    auto info = co_await child.StatAsync();
    if (!info.Succeeded()) {
      co_return std::unexpected(DataArchiveError{info.Error().message});
    }
    const File output = destination.Child(name);
    if (info.Value().type == FileType::Directory) {
      auto copied = co_await CopyDirectoryContents(child, output, depth + 1U);
      if (!copied) {
        co_return std::unexpected(std::move(copied.error()));
      }
    } else if (info.Value().type == FileType::File) {
      if (!co_await child.CopyToAsync(output)) {
        co_return std::unexpected(
            DataArchiveError{"cannot stage workspace file: " + child.Path()});
      }
    }
  }
  co_return DataArchiveResult<void>{};
}

File UniqueTransactionRoot(const File &parent) {
  static std::atomic_uint64_t sequence{};
  const auto now = std::chrono::duration_cast<std::chrono::microseconds>(
      std::chrono::system_clock::now().time_since_epoch());
  return parent.Child(".import-" + std::to_string(now.count()) + "-" +
                      std::to_string(sequence.fetch_add(1)));
}

huxerui::Task<DataArchiveResult<WorkspaceImport>> PrepareWorkspaceImport(
    const std::vector<ZipEntryData> &entries, const std::array<File, 3> &roots,
    bool replace) {
  const auto parent = roots.front().Parent();
  if (!parent ||
      !std::ranges::all_of(roots, [&](const File &root) {
        const auto candidate = root.Parent();
        return candidate && *candidate == *parent;
      }) ||
      !co_await parent->CreateDirectoriesAsync()) {
    co_return std::unexpected(
        DataArchiveError{"workspace roots do not share a writable parent"});
  }

  const File transaction_root = UniqueTransactionRoot(*parent);
  const File staged_parent = transaction_root.Child("staged");
  const File backup_parent = transaction_root.Child("backup");
  WorkspaceImport prepared{
      .transaction_root = transaction_root,
      .roots = roots,
      .staged = {staged_parent.Child("home"), staged_parent.Child("project"),
                 staged_parent.Child("skills")},
      .backups = {backup_parent.Child("home"), backup_parent.Child("project"),
                  backup_parent.Child("skills")},
  };
  if (!co_await staged_parent.CreateDirectoriesAsync() ||
      !co_await backup_parent.CreateDirectoriesAsync()) {
    static_cast<void>(
        co_await prepared.transaction_root.DeleteRecursivelyAsync());
    co_return std::unexpected(
        DataArchiveError{"cannot create workspace import transaction"});
  }

  for (std::size_t index = 0; index < roots.size(); ++index) {
    if (!co_await prepared.staged[index].CreateDirectoriesAsync()) {
      static_cast<void>(
          co_await prepared.transaction_root.DeleteRecursivelyAsync());
      co_return std::unexpected(
          DataArchiveError{"cannot create staged workspace root"});
    }
    if (!replace && roots[index].Exists()) {
      auto copied = co_await CopyDirectoryContents(
          roots[index], prepared.staged[index], 1U);
      if (!copied) {
        static_cast<void>(
            co_await prepared.transaction_root.DeleteRecursivelyAsync());
        co_return std::unexpected(std::move(copied.error()));
      }
    }
    auto restored = co_await RestoreRoot(entries, kRootNames[index],
                                         prepared.staged[index], false);
    if (!restored) {
      static_cast<void>(
          co_await prepared.transaction_root.DeleteRecursivelyAsync());
      co_return std::unexpected(std::move(restored.error()));
    }
    prepared.restored_files += *restored;
  }
  co_return prepared;
}

huxerui::Task<bool> RollbackWorkspaceImport(WorkspaceImport &prepared) {
  bool restored = true;
  for (std::size_t reverse = prepared.roots.size(); reverse > 0; --reverse) {
    const std::size_t index = reverse - 1U;
    if (!prepared.swapped[index]) {
      continue;
    }
    if (prepared.roots[index].Exists() &&
        !co_await prepared.roots[index].DeleteRecursivelyAsync()) {
      restored = false;
      continue;
    }
    if (prepared.had_original[index] &&
        !co_await prepared.backups[index].MoveToAsync(prepared.roots[index])) {
      restored = false;
      continue;
    }
    prepared.swapped[index] = false;
  }
  co_return restored;
}

huxerui::Task<DataArchiveResult<void>> CommitWorkspaceImport(
    WorkspaceImport &prepared) {
  for (std::size_t index = 0; index < prepared.roots.size(); ++index) {
    prepared.had_original[index] = prepared.roots[index].Exists();
    if (prepared.had_original[index] &&
        !co_await prepared.roots[index].MoveToAsync(prepared.backups[index])) {
      const bool rolled_back = co_await RollbackWorkspaceImport(prepared);
      prepared.rollback_complete = rolled_back;
      co_return std::unexpected(DataArchiveError{
          rolled_back ? "cannot stage existing workspace for replacement"
                      : "cannot stage existing workspace and rollback failed; "
                        "recovery data retained at " +
                            prepared.transaction_root.Path()});
    }
    if (!co_await prepared.staged[index].MoveToAsync(prepared.roots[index])) {
      bool current_restored = true;
      if (prepared.had_original[index]) {
        current_restored = co_await prepared.backups[index].MoveToAsync(
            prepared.roots[index]);
      }
      const bool previous_restored = co_await RollbackWorkspaceImport(prepared);
      const bool rolled_back = current_restored && previous_restored;
      prepared.rollback_complete = rolled_back;
      co_return std::unexpected(DataArchiveError{
          rolled_back ? "cannot activate staged workspace"
                      : "cannot activate staged workspace and rollback failed; "
                        "recovery data retained at " +
                            prepared.transaction_root.Path()});
    }
    prepared.swapped[index] = true;
  }
  co_return DataArchiveResult<void>{};
}

} // namespace

HuxDataArchiveService::HuxDataArchiveService(
    std::shared_ptr<application::ArchiveDatabase> database,
    File temporary_directory, File home_root, File project_root,
    File skills_root)
    : database_(std::move(database)),
      temporary_directory_(std::move(temporary_directory)),
      roots_{std::move(home_root), std::move(project_root),
             std::move(skills_root)} {}

huxerui::Task<DataArchiveResult<application::PreparedDataArchive>>
HuxDataArchiveService::PrepareExport() {
  if (!database_) {
    co_return std::unexpected(DataArchiveError{"archive database unavailable"});
  }
  auto database = co_await database_->ExportRedacted();
  if (!database) {
    co_return std::unexpected(std::move(database.error()));
  }
  const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::system_clock::now().time_since_epoch());
  std::vector<ZipEntryData> entries;
  entries.push_back({
      "manifest.json",
      TextBytes("{\n  \"format\": \"linecode\",\n  \"formatVersion\": 1,\n  "
                "\"container\": \"zip\",\n  \"createdAt\": " +
                std::to_string(now.count()) +
                ",\n  \"database\": true,\n  \"workspaceRoots\": "
                "[\"home\", \"project\", \"skills\"]\n}"),
  });
  entries.push_back({"database.json", TextBytes(database->json)});
  // The legacy app reads models, conversations and settings from
  // `async-storage.json`, not from our table snapshot, so the export has to
  // carry both planes or importing it there restores nothing.
  auto legacy = co_await database_->ExportLegacy();
  if (!legacy)
    co_return std::unexpected(std::move(legacy.error()));
  const auto encoded_legacy = EncodeLegacyArchive(*legacy);
  entries.push_back(
      {"async-storage.json", TextBytes(encoded_legacy.async_storage_json)});
  for (const auto &[name, content] : encoded_legacy.conversation_files)
    entries.push_back({name, TextBytes(content)});
  for (std::size_t index = 0; index < roots_.size(); ++index) {
    if (!roots_[index].Exists()) {
      continue;
    }
    auto appended = co_await AppendDirectory(
        roots_[index], std::string{kRootNames[index]}, entries, 1U);
    if (!appended) {
      co_return std::unexpected(std::move(appended.error()));
    }
  }
  auto encoded = WriteLineCodeZip(entries);
  if (!encoded) {
    co_return std::unexpected(DataArchiveError{encoded.error().message});
  }
  if (!co_await temporary_directory_.CreateDirectoriesAsync()) {
    co_return std::unexpected(
        DataArchiveError{"cannot create archive staging directory"});
  }
  const std::string name = application::DefaultArchiveName(now);
  const File output = temporary_directory_.Child(name);
  if (!co_await output.WriteBytesAsync(std::move(*encoded))) {
    co_return std::unexpected(
        DataArchiveError{"cannot write prepared .linecode archive"});
  }
  co_return application::PreparedDataArchive{
      .file = output,
      .suggested_name = name,
      .summary = database->summary,
  };
}

huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
HuxDataArchiveService::Import(huxerui::FileReference source,
                              domain::ArchiveImportMode mode) {
  if (!database_) {
    co_return std::unexpected(DataArchiveError{"archive database unavailable"});
  }
  auto bytes = co_await source.ReadBytesAsync();
  if (!bytes.Succeeded()) {
    co_return std::unexpected(DataArchiveError{bytes.Error().message});
  }
  co_return co_await ImportBytes(std::move(bytes).Value(), mode);
}

huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
HuxDataArchiveService::ImportBytes(Bytes archive,
                                   domain::ArchiveImportMode mode) {
  if (!database_) {
    co_return std::unexpected(DataArchiveError{"archive database unavailable"});
  }
  auto decoded = ReadLineCodeZip(archive);
  if (!decoded) {
    co_return std::unexpected(DataArchiveError{decoded.error().message});
  }
  const auto *database_entry = FindEntry(*decoded, "database.json");
  const auto *legacy_entry = FindEntry(*decoded, "async-storage.json");
  const auto *manifest_entry = FindEntry(*decoded, "manifest.json");
  if (!database_entry && !legacy_entry) {
    co_return std::unexpected(
        DataArchiveError{"please select a valid .linecode backup"});
  }
  if (manifest_entry) {
    auto manifest = ValidateArchiveManifest(BytesText(manifest_entry->content));
    if (!manifest) {
      co_return std::unexpected(DataArchiveError{manifest.error().message});
    }
    if (manifest->contains_database != (database_entry != nullptr)) {
      co_return std::unexpected(
          DataArchiveError{".linecode manifest does not match its payload"});
    }
  }

  std::optional<application::LegacyArchiveData> legacy_data;
  if (database_entry) {
    auto validated = ValidateDatabaseSnapshot(
        BytesText(database_entry->content),
        kCurrentArchiveDatabaseSchemaVersion);
    if (!validated) {
      co_return std::unexpected(DataArchiveError{validated.error().message});
    }
  } else {
    const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch());
    auto legacy = DecodeLegacyArchive(*decoded, now.count());
    if (!legacy) {
      co_return std::unexpected(DataArchiveError{legacy.error().message});
    }
    legacy_data = std::move(*legacy);
  }

  const bool replace = mode == domain::ArchiveImportMode::replace;
  auto workspace = co_await PrepareWorkspaceImport(*decoded, roots_, replace);
  if (!workspace) {
    co_return std::unexpected(std::move(workspace.error()));
  }
  auto committed = co_await CommitWorkspaceImport(*workspace);
  if (!committed) {
    if (workspace->rollback_complete) {
      static_cast<void>(
          co_await workspace->transaction_root.DeleteRecursivelyAsync());
    }
    co_return std::unexpected(std::move(committed.error()));
  }

  // ZIP CRC, manifest, database payload, and every workspace byte have been
  // validated or staged before the database transaction begins. Workspace
  // roots retain same-filesystem backups until that transaction succeeds.
  DataArchiveResult<domain::ArchiveSummary> imported;
  if (database_entry) {
    imported = co_await database_->ReplaceFromSnapshot(
        BytesText(database_entry->content));
  } else {
    imported = co_await database_->ImportLegacy(std::move(*legacy_data), mode);
  }
  if (!imported) {
    const bool rolled_back = co_await RollbackWorkspaceImport(*workspace);
    if (!rolled_back) {
      co_return std::unexpected(DataArchiveError{
          "database import failed and workspace rollback failed: " +
          imported.error().message + "; recovery data retained at " +
          workspace->transaction_root.Path()});
    }
    static_cast<void>(
        co_await workspace->transaction_root.DeleteRecursivelyAsync());
    co_return std::unexpected(std::move(imported.error()));
  }
  imported->restored_files += workspace->restored_files;
  static_cast<void>(
      co_await workspace->transaction_root.DeleteRecursivelyAsync());
  co_return *imported;
}

} // namespace linecode::infrastructure
