#include "application/execution_mode_project_workspace.h"

#include <ranges>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

ProjectWorkspaceError Error(std::string message) {
  return {.code = ProjectWorkspaceErrorCode::io, .message = std::move(message)};
}

} // namespace

ExecutionModeProjectWorkspace::ExecutionModeProjectWorkspace(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::vector<ProjectWorkspaceRoute> routes)
    : settings_(std::move(settings)), routes_(std::move(routes)) {
  if (!settings_ || routes_.empty() ||
      std::ranges::any_of(routes_, [](const auto &route) {
        return route.controller == nullptr;
      })) {
    throw std::invalid_argument(
        "ExecutionModeProjectWorkspace requires settings and routes");
  }
}

huxerui::Task<
    ProjectWorkspaceResult<std::shared_ptr<ProjectWorkspaceController>>>
ExecutionModeProjectWorkspace::Active() {
  auto settings = co_await settings_->Load();
  if (!settings)
    co_return std::unexpected(Error(settings.error().message));
  const auto route =
      std::ranges::find(routes_, settings->mode, &ProjectWorkspaceRoute::mode);
  if (route == routes_.end()) {
    co_return std::unexpected(
        Error("No workspace backend is registered for the execution mode"));
  }
  co_return route->controller;
}

using ProjectList = std::vector<domain::ProjectRecord>;

#define LINECODE_FORWARD_WORKSPACE(result_type, method, declaration,           \
                                   arguments)                                  \
  huxerui::Task<ProjectWorkspaceResult<result_type>>                           \
      ExecutionModeProjectWorkspace::method declaration {                      \
    auto controller = co_await Active();                                       \
    if (!controller)                                                           \
      co_return std::unexpected(std::move(controller.error()));                \
    co_return co_await (*controller)->method arguments;                        \
  }

// Keep each forwarding signature explicit and mechanically uniform.  The
// routing decision remains centralized in Active().
LINECODE_FORWARD_WORKSPACE(ProjectList, ListProjects, (), ())
LINECODE_FORWARD_WORKSPACE(domain::ProjectRecord, SelectedProject, (), ())
LINECODE_FORWARD_WORKSPACE(domain::ProjectRecord, CreateManagedProject,
                           (std::string name), (std::move(name)))
LINECODE_FORWARD_WORKSPACE(domain::ProjectRecord, RegisterExternalProject,
                           (std::string path, std::string label),
                           (std::move(path), std::move(label)))
LINECODE_FORWARD_WORKSPACE(domain::ProjectRecord, SelectProject,
                           (std::string id), (std::move(id)))
LINECODE_FORWARD_WORKSPACE(void, DeleteProject, (std::string id),
                           (std::move(id)))
LINECODE_FORWARD_WORKSPACE(domain::ProjectFileNode, LoadTree,
                           (std::string project_id), (std::move(project_id)))
LINECODE_FORWARD_WORKSPACE(void, CreateFile,
                           (std::string project_id, std::string relative_path),
                           (std::move(project_id), std::move(relative_path)))
LINECODE_FORWARD_WORKSPACE(void, CreateDirectory,
                           (std::string project_id, std::string relative_path),
                           (std::move(project_id), std::move(relative_path)))
LINECODE_FORWARD_WORKSPACE(std::string, ReadText,
                           (std::string project_id, std::string relative_path),
                           (std::move(project_id), std::move(relative_path)))
LINECODE_FORWARD_WORKSPACE(void, WriteText,
                           (std::string project_id, std::string relative_path,
                            std::string value),
                           (std::move(project_id), std::move(relative_path),
                            std::move(value)))
LINECODE_FORWARD_WORKSPACE(void, Rename,
                           (std::string project_id, std::string relative_path,
                            std::string new_name),
                           (std::move(project_id), std::move(relative_path),
                            std::move(new_name)))
LINECODE_FORWARD_WORKSPACE(void, Copy,
                           (std::string project_id,
                            std::string source_relative_path,
                            std::string destination_relative_path),
                           (std::move(project_id),
                            std::move(source_relative_path),
                            std::move(destination_relative_path)))
LINECODE_FORWARD_WORKSPACE(void, Move,
                           (std::string project_id,
                            std::string source_relative_path,
                            std::string destination_relative_path),
                           (std::move(project_id),
                            std::move(source_relative_path),
                            std::move(destination_relative_path)))
LINECODE_FORWARD_WORKSPACE(void, Delete,
                           (std::string project_id, std::string relative_path),
                           (std::move(project_id), std::move(relative_path)))

#undef LINECODE_FORWARD_WORKSPACE

} // namespace linecode::application
