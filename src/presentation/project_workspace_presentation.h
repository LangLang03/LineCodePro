#pragma once

#include <expected>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/file.h>
#include <huxerui/presentation.h>
#include <huxerui/state.h>
#include <huxerui/task.h>

#include "domain/project_workspace.h"
#include "presentation/components/drawer.h"

namespace linecode::application {
class ProjectWorkspaceController;
class StoragePermissionService;
class McpExecutionSettingsService;
class SshSettingsService;
} // namespace linecode::application

namespace linecode::presentation {

struct ProjectWorkspacePresentationState final {
  std::vector<domain::ProjectRecord> projects;
  std::optional<domain::ProjectRecord> selected;
  std::optional<domain::ProjectFileNode> file_tree;
  bool loading = true;
  std::string error;

  bool operator==(const ProjectWorkspacePresentationState &) const = default;
};

struct WorkspaceClipboard final {
  std::string absolute_path;
  std::string name;

  [[nodiscard]] bool Empty() const noexcept { return absolute_path.empty(); }
  bool operator==(const WorkspaceClipboard &) const = default;
};

[[nodiscard]] std::expected<std::string, std::string>
WorkspaceRelativePath(std::string_view root, std::string_view absolute_path);
[[nodiscard]] std::string WorkspaceDisplayPath(std::string_view path);

[[nodiscard]] DrawerFileNode
ToDrawerFileNode(const domain::ProjectFileNode &node);

bool ToggleWorkspaceDirectory(domain::ProjectFileNode &node,
                              std::string_view absolute_path);

class ProjectWorkspaceCoordinator final {
public:
  ProjectWorkspaceCoordinator(
      std::shared_ptr<application::ProjectWorkspaceController> service,
      huxerui::State<ProjectWorkspacePresentationState> state,
      huxerui::State<DrawerModel> drawer,
      huxerui::State<WorkspaceClipboard> clipboard, huxerui::TaskScope tasks,
      huxerui::BottomSheetHandle sheets, huxerui::DialogHandle dialogs,
      huxerui::ToastHandle toast, std::shared_ptr<huxerui::FilePicker> picker,
      std::shared_ptr<application::StoragePermissionService> storage_permission,
      huxerui::State<bool> external_storage_granted,
      huxerui::State<bool> termux_ssh_mode,
      std::shared_ptr<application::McpExecutionSettingsService>
          execution_settings,
      std::shared_ptr<application::SshSettingsService> ssh_settings);

  void Refresh() const;
  void ShowProjectPicker() const;
  void SelectProject(std::string id) const;
  void ShowCreateProjectDialog() const;
  void OpenExternalProject() const;
  void OpenStorageManagement() const;
  [[nodiscard]] bool ExternalStorageGranted() const {
    return external_storage_granted_.Get();
  }
  [[nodiscard]] bool IsTermuxSshMode() const { return termux_ssh_mode_.Get(); }
  void ConfirmDeleteProject(domain::ProjectRecord project) const;

  void ToggleNode(const DrawerFileTarget &target) const;
  void ShowFileActions(DrawerFileTarget target) const;

private:
  void ShowCreateEntryDialog(DrawerFileTarget parent, bool directory) const;
  void ShowRenameDialog(DrawerFileTarget target) const;
  void ConfirmDeleteEntry(DrawerFileTarget target) const;
  void CopyEntry(DrawerFileTarget target) const;
  void PasteInto(DrawerFileTarget target) const;

  std::shared_ptr<application::ProjectWorkspaceController> service_;
  huxerui::State<ProjectWorkspacePresentationState> state_;
  huxerui::State<DrawerModel> drawer_;
  huxerui::State<WorkspaceClipboard> clipboard_;
  huxerui::TaskScope tasks_;
  huxerui::BottomSheetHandle sheets_;
  huxerui::DialogHandle dialogs_;
  huxerui::ToastHandle toast_;
  std::shared_ptr<huxerui::FilePicker> picker_;
  std::shared_ptr<application::StoragePermissionService> storage_permission_;
  huxerui::State<bool> external_storage_granted_;
  huxerui::State<bool> termux_ssh_mode_;
  std::shared_ptr<application::McpExecutionSettingsService> execution_settings_;
  std::shared_ptr<application::SshSettingsService> ssh_settings_;
};

} // namespace linecode::presentation
