#include "infrastructure/hux_workspace_file_store.h"

#include <algorithm>
#include <atomic>
#include <filesystem>
#include <ranges>
#include <stdexcept>
#include <utility>

namespace linecode::infrastructure {
namespace {

namespace fs = std::filesystem;
using application::ManagedProjectDirectory;
using application::ProjectWorkspaceError;
using application::ProjectWorkspaceErrorCode;
using application::ProjectWorkspaceResult;

constexpr std::size_t kMaximumTreeDepth = 64;
constexpr std::size_t kMaximumTreeNodes = 20'000;
std::atomic<std::uint64_t> next_stage_id{1};

ProjectWorkspaceError Error(ProjectWorkspaceErrorCode code,
                            std::string message) {
  return {.code = code, .message = std::move(message)};
}

bool IsContained(const fs::path &root, const fs::path &candidate) {
  const auto mismatch = std::ranges::mismatch(root, candidate);
  return mismatch.in1 == root.end();
}

ProjectWorkspaceResult<fs::path> CanonicalDirectory(std::string_view value) {
  if (value.empty())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Workspace root is empty"));
  const fs::path requested{value};
  std::error_code error;
  const auto status = fs::symlink_status(requested, error);
  if (error || status.type() == fs::file_type::not_found)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::not_found,
                                 "Workspace directory does not exist"));
  if (status.type() == fs::file_type::symlink)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::symbolic_link,
                                 "Workspace root cannot be a symbolic link"));
  if (status.type() != fs::file_type::directory)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Workspace root is not a directory"));
  auto canonical = fs::canonical(requested, error);
  if (error)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to resolve the workspace root"));
  return canonical.lexically_normal();
}

ProjectWorkspaceResult<fs::path>
ResolveRelative(std::string_view root_value, std::string_view relative_value,
                bool allow_missing_leaf, bool allow_root = false) {
  auto root = CanonicalDirectory(root_value);
  if (!root)
    return std::unexpected(std::move(root.error()));
  if (relative_value.empty()) {
    if (allow_root)
      return *root;
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Workspace-relative path is empty"));
  }
  const fs::path relative{relative_value};
  if (relative.is_absolute() || relative.has_root_path())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::outside_workspace,
                                 "Absolute paths are not workspace-relative"));

  fs::path current = *root;
  for (const auto &part : relative) {
    if (part.empty() || part == "." || part == "..")
      return std::unexpected(Error(ProjectWorkspaceErrorCode::outside_workspace,
                                   "Path traversal is not allowed"));
    current /= part;
    std::error_code error;
    const auto status = fs::symlink_status(current, error);
    if (!error && status.type() == fs::file_type::symlink)
      return std::unexpected(Error(ProjectWorkspaceErrorCode::symbolic_link,
                                   "Symbolic links cannot be followed"));
    if (error && error != std::errc::no_such_file_or_directory)
      return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                   "Unable to inspect workspace path"));
  }

  std::error_code error;
  const bool exists = fs::exists(current, error);
  if (error)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to inspect workspace path"));
  if (!exists && !allow_missing_leaf)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::not_found,
                                 "Workspace entry does not exist"));
  const auto resolved = fs::weakly_canonical(current, error);
  if (error || !IsContained(*root, resolved))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::outside_workspace,
                                 "Workspace path escapes its root"));
  return resolved;
}

bool IsProtectedMetadataPath(std::string_view relative_path) {
  const fs::path relative{relative_path};
  const auto first = relative.begin();
  return first != relative.end() && *first == ".linecode";
}

ProjectWorkspaceResult<fs::path> ResolveMutation(std::string_view root,
                                                 std::string_view relative_path,
                                                 bool allow_missing_leaf) {
  if (IsProtectedMetadataPath(relative_path))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::protected_project,
                                 "Workspace metadata is protected"));
  return ResolveRelative(root, relative_path, allow_missing_leaf);
}

ProjectWorkspaceResult<void> RequireMissing(const fs::path &path) {
  std::error_code error;
  if (fs::exists(path, error))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::conflict,
                                 "Destination already exists"));
  if (error)
    return std::unexpected(
        Error(ProjectWorkspaceErrorCode::io, "Unable to inspect destination"));
  return {};
}

ProjectWorkspaceResult<void> RequireParentDirectory(const fs::path &path) {
  const auto parent = path.parent_path();
  std::error_code error;
  const auto status = fs::status(parent, error);
  if (error || status.type() != fs::file_type::directory)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::not_found,
                                 "Destination parent is not a directory"));
  return {};
}

std::string StageName(std::string_view prefix) {
  return ".linecode-" + std::string{prefix} + "-" +
         std::to_string(next_stage_id.fetch_add(1, std::memory_order_relaxed));
}

ProjectWorkspaceResult<void> CopyEntry(const huxerui::File &source,
                                       const huxerui::File &destination,
                                       std::size_t depth, std::size_t &nodes) {
  if (depth > kMaximumTreeDepth || ++nodes > kMaximumTreeNodes)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Workspace copy exceeds safety limits"));
  std::error_code link_error;
  if (fs::symlink_status(source.Path(), link_error).type() ==
      fs::file_type::symlink)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::symbolic_link,
                                 "Symbolic links cannot be copied"));
  auto info = source.Stat();
  if (!info.Succeeded())
    return std::unexpected(
        Error(ProjectWorkspaceErrorCode::io, "Unable to inspect copy source"));
  if (info.Value().type == huxerui::FileType::File) {
    if (!source.CopyTo(destination, false))
      return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                   "Unable to copy workspace file"));
    return {};
  }
  if (info.Value().type != huxerui::FileType::Directory)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Only files and directories can be copied"));
  if (!destination.CreateDirectory())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to create copied directory"));
  auto listed = source.ListChildren();
  if (!listed.Succeeded())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to enumerate copy source"));
  for (const auto &child : listed.Value()) {
    auto copied =
        CopyEntry(child, destination.Child(child.Name()), depth + 1, nodes);
    if (!copied)
      return copied;
  }
  return {};
}

ProjectWorkspaceResult<domain::ProjectFileNode>
LoadNode(const huxerui::File &file, std::size_t depth, std::size_t &nodes) {
  if (depth > kMaximumTreeDepth || ++nodes > kMaximumTreeNodes)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Workspace tree exceeds safety limits"));
  std::error_code link_error;
  const bool symbolic_link =
      fs::symlink_status(file.Path(), link_error).type() ==
      fs::file_type::symlink;
  if (link_error)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to inspect workspace entry"));
  if (symbolic_link) {
    return domain::ProjectFileNode{.name = file.Name(),
                                   .path = file.Path(),
                                   .directory = false,
                                   .symbolic_link = true,
                                   .expanded = false,
                                   .children = {}};
  }
  auto info = file.Stat();
  if (!info.Succeeded())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to inspect workspace entry"));
  const bool directory = info.Value().type == huxerui::FileType::Directory;
  domain::ProjectFileNode node{.name = file.Name(),
                               .path = file.Path(),
                               .directory = directory,
                               .symbolic_link = false,
                               .expanded = depth == 0,
                               .children = {}};
  if (!directory)
    return node;
  auto listed = file.ListChildren();
  if (!listed.Succeeded())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to enumerate workspace directory"));
  auto children = std::move(listed.Value());
  std::ranges::sort(children, [](const auto &left, const auto &right) {
    return left.Name() < right.Name();
  });
  for (const auto &child : children) {
    if ((file.Name() == ".linecode" && child.Name() == ".workspace-trash") ||
        child.Name().starts_with(".linecode-copy-stage-") ||
        child.Name().starts_with(".linecode-write-stage-"))
      continue;
    auto loaded = LoadNode(child, depth + 1, nodes);
    if (!loaded)
      return std::unexpected(std::move(loaded.error()));
    node.children.push_back(std::move(*loaded));
  }
  std::ranges::stable_sort(node.children,
                           [](const auto &left, const auto &right) {
                             if (left.directory != right.directory)
                               return left.directory;
                             return left.name < right.name;
                           });
  return node;
}

ProjectWorkspaceResult<void> AtomicWrite(const fs::path &target,
                                         std::string value, bool overwrite) {
  auto parent = huxerui::File{target.parent_path().string()};
  auto stage = parent.Child(StageName("write-stage"));
  try {
    if (!stage.WriteString(value)) {
      static_cast<void>(stage.Delete());
      return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                   "Unable to stage workspace file"));
    }
  } catch (const std::invalid_argument &) {
    static_cast<void>(stage.Delete());
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Workspace file text is not valid UTF-8"));
  }
  const huxerui::File destination{target.string()};
  if (!stage.MoveTo(destination, overwrite)) {
    static_cast<void>(stage.Delete());
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to commit workspace file"));
  }
  return {};
}

} // namespace

HuxWorkspaceFileStore::HuxWorkspaceFileStore(huxerui::File default_root,
                                             huxerui::File managed_root)
    : default_root_(std::move(default_root)),
      managed_root_(std::move(managed_root)) {}

ProjectWorkspaceResult<std::string>
HuxWorkspaceFileStore::PrepareDefaultProject() {
  if (!default_root_.CreateDirectories() ||
      !default_root_.Resolve(".linecode/skills").CreateDirectories())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to create the default workspace"));
  return ResolveDirectory(default_root_.Path());
}

ProjectWorkspaceResult<ManagedProjectDirectory>
HuxWorkspaceFileStore::PrepareManagedProject(std::string_view directory_name) {
  if (directory_name.empty())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Managed project name is empty"));
  if (!managed_root_.CreateDirectories())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to create the managed project root"));
  huxerui::File directory = managed_root_;
  try {
    directory = managed_root_.Child(directory_name);
  } catch (const std::invalid_argument &) {
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Managed project name is invalid"));
  }
  const bool existed = directory.Exists();
  if (existed && !directory.IsDirectory())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::conflict,
                                 "Managed project path is not a directory"));
  if (!directory.CreateDirectory() ||
      !directory.Resolve(".linecode/skills").CreateDirectories()) {
    if (!existed)
      static_cast<void>(directory.DeleteRecursively());
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to prepare the managed project"));
  }
  auto resolved = ResolveDirectory(directory.Path());
  if (!resolved) {
    if (!existed)
      static_cast<void>(directory.DeleteRecursively());
    return std::unexpected(std::move(resolved.error()));
  }
  return ManagedProjectDirectory{.path = std::move(*resolved),
                                 .created = !existed};
}

ProjectWorkspaceResult<void> HuxWorkspaceFileStore::RollbackManagedProject(
    const ManagedProjectDirectory &directory) {
  if (!directory.created)
    return {};
  auto managed_root = CanonicalDirectory(managed_root_.Path());
  if (!managed_root)
    return std::unexpected(std::move(managed_root.error()));
  auto target = CanonicalDirectory(directory.path);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (target->parent_path() != *managed_root)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::outside_workspace,
                                 "Rollback target is not a managed project"));
  if (!huxerui::File{target->string()}.DeleteRecursively())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to roll back the managed project"));
  return {};
}

ProjectWorkspaceResult<std::string>
HuxWorkspaceFileStore::ResolveDirectory(std::string_view root) const {
  auto canonical = CanonicalDirectory(root);
  if (!canonical)
    return std::unexpected(std::move(canonical.error()));
  return canonical->string();
}

ProjectWorkspaceResult<domain::ProjectFileNode>
HuxWorkspaceFileStore::LoadTree(std::string_view root) const {
  auto canonical = CanonicalDirectory(root);
  if (!canonical)
    return std::unexpected(std::move(canonical.error()));
  std::size_t nodes{};
  return LoadNode(huxerui::File{canonical->string()}, 0, nodes);
}

ProjectWorkspaceResult<void>
HuxWorkspaceFileStore::CreateFile(std::string_view root,
                                  std::string_view relative_path) {
  auto target = ResolveMutation(root, relative_path, true);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (auto missing = RequireMissing(*target); !missing)
    return missing;
  if (auto parent = RequireParentDirectory(*target); !parent)
    return parent;
  return AtomicWrite(*target, {}, false);
}

ProjectWorkspaceResult<void>
HuxWorkspaceFileStore::CreateDirectory(std::string_view root,
                                       std::string_view relative_path) {
  auto target = ResolveMutation(root, relative_path, true);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (auto missing = RequireMissing(*target); !missing)
    return missing;
  if (auto parent = RequireParentDirectory(*target); !parent)
    return parent;
  if (!huxerui::File{target->string()}.CreateDirectory())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to create workspace directory"));
  return {};
}

ProjectWorkspaceResult<std::string>
HuxWorkspaceFileStore::ReadText(std::string_view root,
                                std::string_view relative_path) const {
  auto target = ResolveRelative(root, relative_path, false);
  if (!target)
    return std::unexpected(std::move(target.error()));
  auto value = huxerui::File{target->string()}.ReadString();
  if (!value.Succeeded())
    return std::unexpected(
        Error(ProjectWorkspaceErrorCode::io, "Unable to read workspace file"));
  return std::move(value.Value());
}

ProjectWorkspaceResult<void> HuxWorkspaceFileStore::WriteText(
    std::string_view root, std::string_view relative_path, std::string value) {
  auto target = ResolveMutation(root, relative_path, true);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (auto parent = RequireParentDirectory(*target); !parent)
    return parent;
  std::error_code error;
  if (fs::exists(*target, error) && !fs::is_regular_file(*target, error))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "Workspace write target is not a file"));
  if (error)
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to inspect workspace write target"));
  return AtomicWrite(*target, std::move(value), true);
}

ProjectWorkspaceResult<void>
HuxWorkspaceFileStore::Rename(std::string_view root,
                              std::string_view relative_path,
                              std::string_view new_name) {
  auto source = ResolveMutation(root, relative_path, false);
  if (!source)
    return std::unexpected(std::move(source.error()));
  huxerui::File destination{source->parent_path().string()};
  try {
    destination = destination.Child(new_name);
  } catch (const std::invalid_argument &) {
    return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                 "New file name is invalid"));
  }
  auto canonical_root = CanonicalDirectory(root);
  if (!canonical_root)
    return std::unexpected(std::move(canonical_root.error()));
  const auto destination_relative =
      fs::path{destination.Path()}.lexically_relative(*canonical_root);
  auto target = ResolveMutation(root, destination_relative.string(), true);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (auto missing = RequireMissing(*target); !missing)
    return missing;
  if (!huxerui::File{source->string()}.MoveTo(huxerui::File{target->string()}))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to rename workspace entry"));
  return {};
}

ProjectWorkspaceResult<void>
HuxWorkspaceFileStore::Copy(std::string_view root,
                            std::string_view source_relative_path,
                            std::string_view destination_relative_path) {
  auto source = ResolveMutation(root, source_relative_path, false);
  if (!source)
    return std::unexpected(std::move(source.error()));
  auto target = ResolveMutation(root, destination_relative_path, true);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (auto missing = RequireMissing(*target); !missing)
    return missing;
  if (auto parent = RequireParentDirectory(*target); !parent)
    return parent;
  if (IsContained(*source, *target))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::conflict,
                                 "A directory cannot be copied into itself"));

  const huxerui::File parent{target->parent_path().string()};
  const auto stage = parent.Child(StageName("copy-stage"));
  std::size_t nodes{};
  auto copied = CopyEntry(huxerui::File{source->string()}, stage, 0, nodes);
  if (!copied) {
    static_cast<void>(stage.DeleteRecursively());
    return copied;
  }
  if (!stage.MoveTo(huxerui::File{target->string()})) {
    static_cast<void>(stage.DeleteRecursively());
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to commit copied workspace entry"));
  }
  return {};
}

ProjectWorkspaceResult<void>
HuxWorkspaceFileStore::Move(std::string_view root,
                            std::string_view source_relative_path,
                            std::string_view destination_relative_path) {
  auto source = ResolveMutation(root, source_relative_path, false);
  if (!source)
    return std::unexpected(std::move(source.error()));
  auto target = ResolveMutation(root, destination_relative_path, true);
  if (!target)
    return std::unexpected(std::move(target.error()));
  if (auto missing = RequireMissing(*target); !missing)
    return missing;
  if (auto parent = RequireParentDirectory(*target); !parent)
    return parent;
  if (IsContained(*source, *target))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::conflict,
                                 "A directory cannot be moved into itself"));
  if (!huxerui::File{source->string()}.MoveTo(huxerui::File{target->string()}))
    return std::unexpected(
        Error(ProjectWorkspaceErrorCode::io, "Unable to move workspace entry"));
  return {};
}

ProjectWorkspaceResult<void>
HuxWorkspaceFileStore::Delete(std::string_view root,
                              std::string_view relative_path) {
  auto source = ResolveMutation(root, relative_path, false);
  if (!source)
    return std::unexpected(std::move(source.error()));
  auto canonical_root = CanonicalDirectory(root);
  if (!canonical_root)
    return std::unexpected(std::move(canonical_root.error()));
  const huxerui::File root_file{canonical_root->string()};
  const auto trash = root_file.Resolve(".linecode/.workspace-trash");
  if (!trash.CreateDirectories())
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to create workspace trash"));
  const auto staged = trash.Child(StageName("deleted"));
  if (!huxerui::File{source->string()}.MoveTo(staged))
    return std::unexpected(Error(ProjectWorkspaceErrorCode::io,
                                 "Unable to stage workspace deletion"));
  // Rename is the commit point. Cleanup failure leaves only an internal trash
  // entry and does not turn a completed user-visible deletion into a failure.
  static_cast<void>(staged.DeleteRecursively());
  return {};
}

} // namespace linecode::infrastructure
