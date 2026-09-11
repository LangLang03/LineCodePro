#pragma once

#include <cstdint>
#include <expected>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "domain/project_workspace.h"

namespace linecode::application {

enum class ProjectWorkspaceErrorCode : std::uint8_t {
  invalid_argument,
  not_found,
  conflict,
  outside_workspace,
  symbolic_link,
  protected_project,
  corrupt_catalog,
  io,
};

struct ProjectWorkspaceError final {
  ProjectWorkspaceErrorCode code{ProjectWorkspaceErrorCode::io};
  std::string message;

  bool operator==(const ProjectWorkspaceError &) const = default;
};

template <class Value>
using ProjectWorkspaceResult = std::expected<Value, ProjectWorkspaceError>;

struct ManagedProjectDirectory final {
  std::string path;
  bool created{};

  bool operator==(const ManagedProjectDirectory &) const = default;
};

// Atomic catalog persistence is deliberately separate from filesystem mutation.
// ProjectWorkspaceService can therefore roll a newly-created managed directory
// back when committing its record fails.
class ProjectCatalogStore {
public:
  virtual ~ProjectCatalogStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectCatalog>>
  LoadCatalog() = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  ReplaceCatalog(domain::ProjectCatalog catalog) = 0;
};

// Local workspace I/O contract. Paths accepted by operations are relative to
// root and are validated by the implementation before touching the filesystem.
class WorkspaceFileStore {
public:
  virtual ~WorkspaceFileStore() = default;

  [[nodiscard]] virtual ProjectWorkspaceResult<std::string>
  PrepareDefaultProject() = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<ManagedProjectDirectory>
  PrepareManagedProject(std::string_view directory_name) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  RollbackManagedProject(const ManagedProjectDirectory &directory) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<std::string>
  ResolveDirectory(std::string_view root) const = 0;

  [[nodiscard]] virtual ProjectWorkspaceResult<domain::ProjectFileNode>
  LoadTree(std::string_view root) const = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  CreateFile(std::string_view root, std::string_view relative_path) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  CreateDirectory(std::string_view root, std::string_view relative_path) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<std::string>
  ReadText(std::string_view root, std::string_view relative_path) const = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  WriteText(std::string_view root, std::string_view relative_path,
            std::string value) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  Rename(std::string_view root, std::string_view relative_path,
         std::string_view new_name) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  Copy(std::string_view root, std::string_view source_relative_path,
       std::string_view destination_relative_path) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  Move(std::string_view root, std::string_view source_relative_path,
       std::string_view destination_relative_path) = 0;
  [[nodiscard]] virtual ProjectWorkspaceResult<void>
  Delete(std::string_view root, std::string_view relative_path) = 0;
};

class ProjectWorkspaceStore {
public:
  virtual ~ProjectWorkspaceStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectWorkspace>>
  Load() = 0;
};

} // namespace linecode::application
