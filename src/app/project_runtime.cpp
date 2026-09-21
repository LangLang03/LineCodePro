#include "app/project_runtime.h"

#include <utility>
#include <vector>

#include "app/app_root_support.h"
#include "application/execution_mode_project_workspace.h"
#include "application/mcp_execution_settings.h"
#include "application/mode_workspace_image_reader.h"
#include "application/project_workspace_service.h"
#include "application/ssh_project_workspace.h"
#include "infrastructure/hux_workspace_file_store.h"
#include "infrastructure/sqlite_project_catalog_store.h"
#include "infrastructure/workspace_image_readers.h"

namespace linecode::app {

std::shared_ptr<ProjectRuntime>
BuildProjectRuntime(ProjectRuntimeDependencies dependencies) {
  auto clock = std::make_shared<SystemWorkspaceClock>();
  auto local_catalog =
      std::make_shared<infrastructure::SqliteProjectCatalogStore>(
          dependencies.database_file);
  auto project_files = std::make_shared<infrastructure::HuxWorkspaceFileStore>(
      dependencies.linecode_directory.Child("home"),
      dependencies.linecode_directory.Child("project"));
  auto local = std::make_shared<application::ProjectWorkspaceService>(
      std::move(local_catalog), std::move(project_files), clock);

  auto ssh_catalog =
      std::make_shared<infrastructure::SqliteProjectCatalogStore>(
          dependencies.database_file, infrastructure::ProjectCatalogScope::ssh);
  auto ssh = std::make_shared<application::SshProjectWorkspace>(
      std::move(ssh_catalog), dependencies.ssh_settings, dependencies.ssh_files,
      std::move(clock));

  auto routed = std::make_shared<application::ExecutionModeProjectWorkspace>(
      dependencies.execution_settings,
      std::vector<application::ProjectWorkspaceRoute>{
          {.mode = domain::McpExecutionMode::local, .controller = local},
          {.mode = domain::McpExecutionMode::ssh, .controller = ssh},
          {.mode = domain::McpExecutionMode::terminal_provider,
           .controller = local},
      });

  std::vector<application::WorkspaceImageReaderRoute> image_readers{
      {.mode = domain::McpExecutionMode::local,
       .reader =
           std::make_shared<infrastructure::LocalWorkspaceImageReader>(local)},
      {.mode = domain::McpExecutionMode::ssh,
       .reader = std::make_shared<infrastructure::SshWorkspaceImageReader>(
           dependencies.ssh_settings, ssh, dependencies.ssh_files)},
  };
  if (dependencies.terminal_gateway) {
    image_readers.push_back(
        {.mode = domain::McpExecutionMode::terminal_provider,
         .reader = std::make_shared<
             infrastructure::TerminalProviderWorkspaceImageReader>(
             dependencies.terminal_providers, dependencies.terminal_gateway)});
  }
  auto images = std::make_shared<application::ModeWorkspaceImageReader>(
      dependencies.execution_settings, std::move(image_readers));

  return std::make_shared<ProjectRuntime>(ProjectRuntime{
      .local = std::move(local),
      .ssh = std::move(ssh),
      .routed = std::move(routed),
      .images = std::move(images),
  });
}

} // namespace linecode::app
