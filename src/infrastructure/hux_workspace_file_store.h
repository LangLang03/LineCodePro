#pragma once

#include <huxerui/file.h>

#include "application/ports/project_workspace_store.h"

namespace linecode::infrastructure {

class HuxWorkspaceFileStore final : public application::WorkspaceFileStore {
public:
  HuxWorkspaceFileStore(huxerui::File default_root, huxerui::File managed_root);

  [[nodiscard]] application::ProjectWorkspaceResult<std::string>
  PrepareDefaultProject() override;
  [[nodiscard]] application::ProjectWorkspaceResult<
      application::ManagedProjectDirectory>
  PrepareManagedProject(std::string_view directory_name) override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  RollbackManagedProject(
      const application::ManagedProjectDirectory &directory) override;
  [[nodiscard]] application::ProjectWorkspaceResult<std::string>
  ResolveDirectory(std::string_view root) const override;

  [[nodiscard]] application::ProjectWorkspaceResult<domain::ProjectFileNode>
  LoadTree(std::string_view root) const override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  CreateFile(std::string_view root, std::string_view relative_path) override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  CreateDirectory(std::string_view root,
                  std::string_view relative_path) override;
  [[nodiscard]] application::ProjectWorkspaceResult<std::string>
  ReadText(std::string_view root,
           std::string_view relative_path) const override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  WriteText(std::string_view root, std::string_view relative_path,
            std::string value) override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  Rename(std::string_view root, std::string_view relative_path,
         std::string_view new_name) override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  Copy(std::string_view root, std::string_view source_relative_path,
       std::string_view destination_relative_path) override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  Move(std::string_view root, std::string_view source_relative_path,
       std::string_view destination_relative_path) override;
  [[nodiscard]] application::ProjectWorkspaceResult<void>
  Delete(std::string_view root, std::string_view relative_path) override;

private:
  huxerui::File default_root_;
  huxerui::File managed_root_;
};

} // namespace linecode::infrastructure
