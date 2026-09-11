#pragma once

#include <memory>

#include "application/ports/project_workspace_controller.h"
#include "application/project_workspace_service.h"
#include "application/ssh_settings_service.h"
#include "application/ssh_workspace_service.h"

namespace linecode::application {

inline constexpr std::string_view kDefaultSshProjectId = "ssh:default";

// SSH implementation of the same workspace capability used by the drawer.
// It owns only remote catalog policy; all protocol and path-safety work stays
// in SshWorkspaceService.
class SshProjectWorkspace final : public ProjectWorkspaceController {
public:
  SshProjectWorkspace(std::shared_ptr<ProjectCatalogStore> catalog,
                      std::shared_ptr<SshSettingsService> settings,
                      std::shared_ptr<SshWorkspaceService> workspace,
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
  struct Context final {
    domain::SshConfig config;
    domain::ProjectRecord project;
  };

  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::ProjectCatalog>>
  LoadReadyCatalog();
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<domain::SshConfig>>
  LoadConfig();
  [[nodiscard]] huxerui::Task<ProjectWorkspaceResult<Context>>
  ProjectContext(std::string project_id);

  std::shared_ptr<ProjectCatalogStore> catalog_;
  std::shared_ptr<SshSettingsService> settings_;
  std::shared_ptr<SshWorkspaceService> workspace_;
  std::shared_ptr<WorkspaceClock> clock_;
};

} // namespace linecode::application
