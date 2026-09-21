#pragma once

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/ssh_transport.h"
#include "domain/project_workspace.h"

namespace linecode::application {

class SshWorkspaceService final {
public:
  explicit SshWorkspaceService(std::shared_ptr<SshTransport> transport);

  [[nodiscard]] huxerui::Task<SshResult<std::string>>
  ResolveDirectory(domain::SshConfig config, std::string root);
  [[nodiscard]] huxerui::Task<SshResult<domain::ProjectFileNode>>
  LoadTree(domain::SshConfig config, std::string root);
  [[nodiscard]] huxerui::Task<SshResult<std::string>>
  ReadText(domain::SshConfig config, std::string root,
           std::string relative_path, std::size_t maximum_bytes = 8U * 1024U * 1024U);
  [[nodiscard]] huxerui::Task<SshResult<std::vector<std::byte>>>
  ReadBytes(domain::SshConfig config, std::string root,
            std::string relative_path,
            std::size_t maximum_bytes = 10U * 1024U * 1024U);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  WriteText(domain::SshConfig config, std::string root,
            std::string relative_path, std::string value);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  CreateFile(domain::SshConfig config, std::string root,
             std::string relative_path);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  CreateDirectory(domain::SshConfig config, std::string root,
                  std::string relative_path);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  Rename(domain::SshConfig config, std::string root,
         std::string relative_path, std::string new_name);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  Copy(domain::SshConfig config, std::string root,
       std::string source_relative_path, std::string destination_relative_path);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  Move(domain::SshConfig config, std::string root,
       std::string source_relative_path, std::string destination_relative_path);
  [[nodiscard]] huxerui::Task<SshResult<void>>
  Delete(domain::SshConfig config, std::string root,
         std::string relative_path);
  [[nodiscard]] huxerui::Task<SshResult<std::string>>
  CreateManagedProject(domain::SshConfig config, std::string name);

private:
  std::shared_ptr<SshTransport> transport_;
  huxerui::WorkerSequence operations_;
};

} // namespace linecode::application
