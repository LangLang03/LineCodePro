#include "application/sub_agent_environment.h"

#include <ranges>
#include <stdexcept>
#include <utility>

#include "application/mcp_execution_settings.h"
#include "application/ports/project_workspace_controller.h"
#include "application/tool_permission_service.h"
#include "domain/tool_permission.h"

namespace linecode::application {

RuntimeSubAgentEnvironmentProvider::RuntimeSubAgentEnvironmentProvider(
    std::shared_ptr<McpExecutionSettingsService> execution_settings,
    std::shared_ptr<ToolPermissionService> permissions,
    std::vector<SubAgentEnvironmentRoute> routes)
    : execution_settings_(std::move(execution_settings)),
      permissions_(std::move(permissions)), routes_(std::move(routes)) {
  if (!execution_settings_ || !permissions_ || routes_.empty() ||
      std::ranges::any_of(routes_, [](const auto &route) {
        return route.workspace == nullptr;
      })) {
    throw std::invalid_argument(
        "RuntimeSubAgentEnvironmentProvider requires settings, permissions "
        "and workspace routes");
  }
}

huxerui::Task<SubAgentEnvironmentResult>
RuntimeSubAgentEnvironmentProvider::Snapshot() {
  auto execution = co_await execution_settings_->Load();
  if (!execution) {
    co_return std::unexpected(SubAgentEnvironmentError{
        .message = "Unable to load Agent execution mode: " +
                   execution.error().message});
  }
  const auto route = std::ranges::find(routes_, execution->mode,
                                       &SubAgentEnvironmentRoute::mode);
  if (route == routes_.end()) {
    co_return std::unexpected(SubAgentEnvironmentError{
        .message = "No Agent environment route is registered for the "
                   "current execution mode"});
  }
  auto project = co_await route->workspace->SelectedProject();
  if (!project) {
    co_return std::unexpected(
        SubAgentEnvironmentError{.message = "Unable to load Agent workspace: " +
                                            project.error().message});
  }
  auto permission = co_await permissions_->Load();
  if (!permission) {
    co_return std::unexpected(SubAgentEnvironmentError{
        .message = "Unable to load Agent permission mode: " +
                   permission.error().message});
  }
  co_return SubAgentEnvironment{
      .workspace_path = project->path,
      .remote_mode = route->remote_mode,
      .permission_mode =
          std::string{domain::SerializeToolPermissionMode(permission->mode)},
      .permission_scope = project->id,
  };
}

} // namespace linecode::application
