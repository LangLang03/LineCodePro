#pragma once

#include <memory>

#include "application/ports/image_understanding.h"
#include "application/ports/project_workspace_controller.h"
#include "application/ports/terminal_provider.h"
#include "application/ssh_settings_service.h"
#include "application/ssh_workspace_service.h"

namespace linecode::infrastructure {

class LocalWorkspaceImageReader final
    : public application::WorkspaceImageReader {
public:
  explicit LocalWorkspaceImageReader(
      std::shared_ptr<application::ProjectWorkspaceController> workspace);

  [[nodiscard]] huxerui::Task<application::ImageUnderstandingResult<
      domain::RawWorkspaceImage>>
  Read(std::string path) override;

private:
  std::shared_ptr<application::ProjectWorkspaceController> workspace_;
};

class TerminalProviderWorkspaceImageReader final
    : public application::WorkspaceImageReader {
public:
  TerminalProviderWorkspaceImageReader(
      std::shared_ptr<application::TerminalProviderStore> providers,
      std::shared_ptr<application::TerminalProviderGateway> gateway);

  [[nodiscard]] huxerui::Task<application::ImageUnderstandingResult<
      domain::RawWorkspaceImage>>
  Read(std::string path) override;

private:
  std::shared_ptr<application::TerminalProviderStore> providers_;
  std::shared_ptr<application::TerminalProviderGateway> gateway_;
};

class SshWorkspaceImageReader final
    : public application::WorkspaceImageReader {
public:
  SshWorkspaceImageReader(
      std::shared_ptr<application::SshSettingsService> settings,
      std::shared_ptr<application::ProjectWorkspaceController> workspace,
      std::shared_ptr<application::SshWorkspaceService> files);

  [[nodiscard]] huxerui::Task<application::ImageUnderstandingResult<
      domain::RawWorkspaceImage>>
  Read(std::string path) override;

private:
  std::shared_ptr<application::SshSettingsService> settings_;
  std::shared_ptr<application::ProjectWorkspaceController> workspace_;
  std::shared_ptr<application::SshWorkspaceService> files_;
};

} // namespace linecode::infrastructure
