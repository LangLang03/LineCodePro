#include "infrastructure/hux_data_archive_service.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cstddef>
#include <expected>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/data_archive.h"
#include "infrastructure/linecode_zip.h"

namespace linecode::infrastructure {
namespace {

using application::DataArchiveError;
using application::DataArchiveResult;
using huxerui::Bytes;
using huxerui::File;
using huxerui::FileErrorCode;
using huxerui::FileType;

constexpr std::array<std::string_view, 3> kRootNames{"home", "project",
                                                     "skills"};

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
    std::vector<ZipEntryData> &entries) {
  auto listed = co_await directory.ListChildrenAsync();
  if (!listed.Succeeded()) {
    if (listed.Error().code == FileErrorCode::NotFound) {
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
      auto appended =
          co_await AppendDirectory(child, std::move(archive_name), entries);
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

bool LooksLikeDatabaseSnapshot(std::string_view json) {
  return json.find("\"format\"") != std::string_view::npos &&
         json.find("linecode-database") != std::string_view::npos &&
         json.find("\"tables\"") != std::string_view::npos;
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
  entries.push_back({"async-storage.json", TextBytes("[]")});
  for (std::size_t index = 0; index < roots_.size(); ++index) {
    if (!roots_[index].Exists()) {
      continue;
    }
    auto appended = co_await AppendDirectory(
        roots_[index], std::string{kRootNames[index]}, entries);
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
  auto decoded = ReadLineCodeZip(bytes.Value());
  if (!decoded) {
    co_return std::unexpected(DataArchiveError{decoded.error().message});
  }
  const auto *database_entry = FindEntry(*decoded, "database.json");
  const auto *legacy_entry = FindEntry(*decoded, "async-storage.json");
  if (!database_entry && !legacy_entry) {
    co_return std::unexpected(
        DataArchiveError{"please select a valid .linecode backup"});
  }
  if (!database_entry) {
    co_return std::unexpected(DataArchiveError{
        "legacy async-storage-only archives are not supported yet"});
  }
  std::string snapshot = BytesText(database_entry->content);
  if (!LooksLikeDatabaseSnapshot(snapshot)) {
    co_return std::unexpected(
        DataArchiveError{"invalid .linecode database snapshot"});
  }

  // Validate every root and stage its bytes in memory before the destructive
  // database replacement. ZIP CRC and path checks have already completed.
  auto imported = co_await database_->ReplaceFromSnapshot(std::move(snapshot));
  if (!imported) {
    co_return std::unexpected(std::move(imported.error()));
  }
  const bool replace = mode == domain::ArchiveImportMode::replace;
  for (std::size_t index = 0; index < roots_.size(); ++index) {
    auto restored = co_await RestoreRoot(*decoded, kRootNames[index],
                                         roots_[index], replace);
    if (!restored) {
      co_return std::unexpected(std::move(restored.error()));
    }
    imported->restored_files += *restored;
  }
  co_return *imported;
}

} // namespace linecode::infrastructure
