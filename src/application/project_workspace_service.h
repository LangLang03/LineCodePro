#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "application/ports/project_workspace_controller.h"

namespace linecode::application {

class WorkspaceClock {
public:
  virtual ~WorkspaceClock() = default;
  [[nodiscard]] virtual std::int64_t NowMilliseconds() const noexcept = 0;
};

class ProjectWorkspaceService final : public ProjectWorkspaceController {
public:
  ProjectWorkspaceService(std::shared_ptr<ProjectCatalogStore> catalog,
                          std::shared_ptr<WorkspaceFileStore> files,
                          std::shared_ptr<WorkspaceClock> clock);

  [[nodiscard]] huxerui::Task<
      ProjectWorkspaceResult<std::vector<domain::ProjectRecord>>>
  ListProjects() override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectedProject() override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
  CreateManagedProject(std::string name) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
  RegisterExternalProject(std::string path, std::string label = {}) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectProject(std::string id) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  DeleteProject(std::string id) override;

  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectFileNode>>
  LoadTree(std::string project_id) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  CreateFile(std::string project_id, std::string relative_path) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  CreateDirectory(std::string project_id, std::string relative_path) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<std::string>>
  ReadText(std::string project_id, std::string relative_path) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  WriteText(std::string project_id, std::string relative_path,
            std::string value) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  Rename(std::string project_id, std::string relative_path,
         std::string new_name) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  Copy(std::string project_id, std::string source_relative_path,
       std::string destination_relative_path) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  Move(std::string project_id, std::string source_relative_path,
       std::string destination_relative_path) override;
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<void>>
  Delete(std::string project_id, std::string relative_path) override;

private:
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectCatalog>>
  LoadReadyCatalog();
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<std::string>>
  ProjectRoot(std::string project_id);

  std::shared_ptr<ProjectCatalogStore> catalog_;
  std::shared_ptr<WorkspaceFileStore> files_;
  std::shared_ptr<WorkspaceClock> clock_;
};

} // namespace linecode::application
