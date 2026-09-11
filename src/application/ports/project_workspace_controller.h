#pragma once

#include <memory>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/project_workspace_store.h"

namespace linecode::application {

// Presentation-facing asynchronous workspace capability.  Implementations
// own the location-specific policy (local filesystem, SSH, or a future
// backend); callers never branch on ProjectSource.
class ProjectWorkspaceController {
public:
  virtual ~ProjectWorkspaceController() = default;

  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<std::vector<domain::ProjectRecord>>>
  ListProjects() = 0;
  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectedProject() = 0;
  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectRecord>>
  CreateManagedProject(std::string name) = 0;
  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectRecord>>
  RegisterExternalProject(std::string path, std::string label = {}) = 0;
  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectProject(std::string id) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  DeleteProject(std::string id) = 0;

  [[nodiscard]] virtual huxerui::Task<
      ProjectWorkspaceResult<domain::ProjectFileNode>>
  LoadTree(std::string project_id) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  CreateFile(std::string project_id, std::string relative_path) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  CreateDirectory(std::string project_id, std::string relative_path) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<std::string>>
  ReadText(std::string project_id, std::string relative_path) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  WriteText(std::string project_id, std::string relative_path,
            std::string value) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  Rename(std::string project_id, std::string relative_path,
         std::string new_name) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  Copy(std::string project_id, std::string source_relative_path,
       std::string destination_relative_path) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  Move(std::string project_id, std::string source_relative_path,
       std::string destination_relative_path) = 0;
  [[nodiscard]] virtual huxerui::Task<ProjectWorkspaceResult<void>>
  Delete(std::string project_id, std::string relative_path) = 0;
};

} // namespace linecode::application
