#pragma once

#include <memory>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/project_workspace_controller.h"

namespace linecode::application {

struct ProjectWorkspaceRoute final {
  domain::McpExecutionMode mode{domain::McpExecutionMode::local};
  std::shared_ptr<ProjectWorkspaceController> controller;
};

// Open-ended execution-mode router.  Adding another workspace backend is a
// registration operation rather than a conditional in every file command.
class ExecutionModeProjectWorkspace final : public ProjectWorkspaceController {
public:
  ExecutionModeProjectWorkspace(
      std::shared_ptr<McpExecutionSettingsService> settings,
      std::vector<ProjectWorkspaceRoute> routes);

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
  [[nodiscard]] huxerui::Task<
      ProjectWorkspaceResult<std::shared_ptr<ProjectWorkspaceController>>>
  Active();

  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::vector<ProjectWorkspaceRoute> routes_;
};

} // namespace linecode::application
