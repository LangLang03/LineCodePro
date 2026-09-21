#pragma once

#include <memory>

#include <huxerui/file.h>

#include "application/ports/image_understanding.h"
#include "application/ports/project_workspace_controller.h"
#include "application/ports/terminal_provider.h"
#include "application/ssh_settings_service.h"
#include "application/ssh_workspace_service.h"

namespace linecode::application {
class McpExecutionSettingsService;
class TerminalProviderStore;
} // namespace linecode::application

namespace linecode::app {

struct ProjectRuntimeDependencies final {
  huxerui::File database_file;
  huxerui::File linecode_directory;
  std::shared_ptr<application::McpExecutionSettingsService> execution_settings;
  std::shared_ptr<application::SshSettingsService> ssh_settings;
  std::shared_ptr<application::SshWorkspaceService> ssh_files;
  std::shared_ptr<application::TerminalProviderStore> terminal_providers;
  std::shared_ptr<application::TerminalProviderGateway> terminal_gateway;
};

struct ProjectRuntime final {
  std::shared_ptr<application::ProjectWorkspaceController> local;
  std::shared_ptr<application::ProjectWorkspaceController> ssh;
  std::shared_ptr<application::ProjectWorkspaceController> routed;
  std::shared_ptr<application::WorkspaceImageReader> images;
};

[[nodiscard]] std::shared_ptr<ProjectRuntime>
BuildProjectRuntime(ProjectRuntimeDependencies dependencies);

} // namespace linecode::app
