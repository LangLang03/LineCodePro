#include "application/ssh_project_workspace.h"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <ranges>
#include <stdexcept>
#include <type_traits>
#include <utility>

namespace linecode::application {
namespace {

ProjectWorkspaceError Error(ProjectWorkspaceErrorCode code,
                            std::string message) {
  return {.code = code, .message = std::move(message)};
}

ProjectWorkspaceError FromSsh(SshError error) {
  const auto code = [&] {
    switch (error.code) {
    case SshErrorCode::invalid_argument:
    case SshErrorCode::not_configured:
      return ProjectWorkspaceErrorCode::invalid_argument;
    case SshErrorCode::not_found:
      return ProjectWorkspaceErrorCode::not_found;
    case SshErrorCode::conflict:
      return ProjectWorkspaceErrorCode::conflict;
    case SshErrorCode::outside_workspace:
      return ProjectWorkspaceErrorCode::outside_workspace;
    case SshErrorCode::symbolic_link:
      return ProjectWorkspaceErrorCode::symbolic_link;
    case SshErrorCode::protected_path:
      return ProjectWorkspaceErrorCode::protected_project;
    default:
      return ProjectWorkspaceErrorCode::io;
    }
  }();
  return Error(code, std::move(error.message));
}

template <class Value>
ProjectWorkspaceResult<Value> FromSshResult(SshResult<Value> result) {
  if (!result)
    return std::unexpected(FromSsh(std::move(result.error())));
  if constexpr (std::is_void_v<Value>)
    return {};
  else
    return std::move(*result);
}

std::string Trim(std::string value) {
  const auto visible = [](unsigned char byte) { return !std::isspace(byte); };
  const auto first = std::ranges::find_if(value, visible);
  const auto last =
      std::ranges::find_if(value | std::views::reverse, visible).base();
  return first < last ? std::string{first, last} : std::string{};
}

std::string Basename(std::string_view path) {
  while (path.size() > 1U && path.back() == '/')
    path.remove_suffix(1U);
  const auto separator = path.find_last_of('/');
  return std::string{
      separator == std::string_view::npos ? path : path.substr(separator + 1U)};
}

auto FindProject(domain::ProjectCatalog &catalog, std::string_view id) {
  return std::ranges::find(catalog.projects, id, &domain::ProjectRecord::id);
}

void Select(domain::ProjectCatalog &catalog, std::string_view id,
            std::int64_t now) {
  catalog.selected_id = id;
  for (auto &project : catalog.projects) {
    project.selected = project.id == id;
    if (project.selected)
      project.updated_at = now;
  }
}

} // namespace

SshProjectWorkspace::SshProjectWorkspace(
    std::shared_ptr<ProjectCatalogStore> catalog,
    std::shared_ptr<SshSettingsService> settings,
    std::shared_ptr<SshWorkspaceService> workspace,
    std::shared_ptr<WorkspaceClock> clock)
    : catalog_(std::move(catalog)), settings_(std::move(settings)),
      workspace_(std::move(workspace)), clock_(std::move(clock)) {
  if (!catalog_ || !settings_ || !workspace_ || !clock_)
    throw std::invalid_argument(
        "SshProjectWorkspace requires catalog, settings, workspace, and clock");
}

huxerui::Task<ProjectWorkspaceResult<domain::SshConfig>>
SshProjectWorkspace::LoadConfig() {
  auto loaded = co_await settings_->Load();
  if (!loaded)
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::io, loaded.error().message));
  auto config = domain::NormalizeSshConfig(std::move(*loaded));
  if (!config.IsConfigured()) {
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                    "SSH is not configured"));
  }
  co_return config;
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectCatalog>>
SshProjectWorkspace::LoadReadyCatalog() {
  auto loaded = co_await catalog_->LoadCatalog();
  if (!loaded)
    co_return std::unexpected(std::move(loaded.error()));
  auto catalog = std::move(*loaded);
  const auto now = clock_->NowMilliseconds();
  bool changed{};
  if (FindProject(catalog, kDefaultSshProjectId) == catalog.projects.end()) {
    catalog.projects.push_back(domain::ProjectRecord{
        .id = std::string{kDefaultSshProjectId},
        .label = "SSH",
        .path = {},
        .source = domain::ProjectSource::ssh,
        .description = "SSH 登录目录",
        .selected = false,
        .created_at = now,
        .updated_at = now,
    });
    changed = true;
  }
  if (catalog.selected_id.empty() ||
      FindProject(catalog, catalog.selected_id) == catalog.projects.end()) {
    catalog.selected_id = kDefaultSshProjectId;
    changed = true;
  }
  for (auto &project : catalog.projects) {
    const bool selected = project.id == catalog.selected_id;
    if (project.selected != selected) {
      project.selected = selected;
      changed = true;
    }
  }
  if (changed) {
    auto stored = co_await catalog_->ReplaceCatalog(catalog);
    if (!stored)
      co_return std::unexpected(std::move(stored.error()));
  }
  co_return catalog;
}

huxerui::Task<ProjectWorkspaceResult<std::vector<domain::ProjectRecord>>>
SshProjectWorkspace::ListProjects() {
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  std::ranges::stable_sort(catalog->projects,
                           [](const auto &left, const auto &right) {
                             if (left.selected != right.selected)
                               return left.selected;
                             return left.updated_at > right.updated_at;
                           });
  co_return std::move(catalog->projects);
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
SshProjectWorkspace::SelectedProject() {
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  auto selected = FindProject(*catalog, catalog->selected_id);
  if (selected == catalog->projects.end()) {
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::corrupt_catalog,
                                    "Selected SSH project is missing"));
  }
  co_return *selected;
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
SshProjectWorkspace::CreateManagedProject(std::string name) {
  name = Trim(std::move(name));
  if (name.empty())
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                    "Project name is empty"));
  auto config = co_await LoadConfig();
  if (!config)
    co_return std::unexpected(std::move(config.error()));
  auto directory = co_await workspace_->CreateManagedProject(*config, name);
  auto converted = FromSshResult(std::move(directory));
  if (!converted)
    co_return std::unexpected(std::move(converted.error()));

  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const std::string id = "ssh-managed:" + *converted;
  if (FindProject(*catalog, id) != catalog->projects.end()) {
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::conflict,
                                    "A project with this name already exists"));
  }
  const auto now = clock_->NowMilliseconds();
  domain::ProjectRecord project{
      .id = id,
      .label = Basename(*converted),
      .path = std::move(*converted),
      .source = domain::ProjectSource::ssh,
      .description = ".linecode/project",
      .selected = true,
      .created_at = now,
      .updated_at = now,
  };
  catalog->projects.push_back(project);
  Select(*catalog, id, now);
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return project;
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
SshProjectWorkspace::RegisterExternalProject(std::string path,
                                             std::string label) {
  auto config = co_await LoadConfig();
  if (!config)
    co_return std::unexpected(std::move(config.error()));
  auto directory =
      co_await workspace_->ResolveDirectory(*config, std::move(path));
  auto converted = FromSshResult(std::move(directory));
  if (!converted)
    co_return std::unexpected(std::move(converted.error()));
  label = Trim(std::move(label));
  if (label.empty())
    label = Basename(*converted);
  if (label.empty())
    label = "SSH workspace";

  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const std::string id = "ssh:" + *converted;
  const auto now = clock_->NowMilliseconds();
  auto project = FindProject(*catalog, id);
  if (project == catalog->projects.end()) {
    catalog->projects.push_back(domain::ProjectRecord{
        .id = id,
        .label = std::move(label),
        .path = *converted,
        .source = domain::ProjectSource::ssh,
        .description = *converted,
        .selected = true,
        .created_at = now,
        .updated_at = now,
    });
  } else {
    project->label = std::move(label);
    project->path = *converted;
    project->source = domain::ProjectSource::ssh;
    project->description = *converted;
    project->updated_at = now;
  }
  Select(*catalog, id, now);
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return *FindProject(*catalog, id);
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
SshProjectWorkspace::SelectProject(std::string id) {
  auto config = co_await LoadConfig();
  if (!config)
    co_return std::unexpected(std::move(config.error()));
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  auto selected = FindProject(*catalog, id);
  if (selected == catalog->projects.end())
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::not_found, "Project was not found"));
  auto root = co_await workspace_->ResolveDirectory(*config, selected->path);
  auto converted = FromSshResult(std::move(root));
  if (!converted)
    co_return std::unexpected(std::move(converted.error()));
  Select(*catalog, id, clock_->NowMilliseconds());
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return *FindProject(*catalog, id);
}

huxerui::Task<ProjectWorkspaceResult<void>>
SshProjectWorkspace::DeleteProject(std::string id) {
  if (id == kDefaultSshProjectId) {
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::protected_project,
              "The default SSH project cannot be removed"));
  }
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const auto project = FindProject(*catalog, id);
  if (project == catalog->projects.end())
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::not_found, "Project was not found"));
  const bool selected = catalog->selected_id == id;
  catalog->projects.erase(project);
  if (selected)
    Select(*catalog, kDefaultSshProjectId, clock_->NowMilliseconds());
  co_return co_await catalog_->ReplaceCatalog(*catalog);
}

huxerui::Task<ProjectWorkspaceResult<SshProjectWorkspace::Context>>
SshProjectWorkspace::ProjectContext(std::string project_id) {
  auto config = co_await LoadConfig();
  if (!config)
    co_return std::unexpected(std::move(config.error()));
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const auto project = FindProject(*catalog, project_id);
  if (project == catalog->projects.end())
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::not_found, "Project was not found"));
  co_return Context{.config = std::move(*config), .project = *project};
}

#define LINECODE_SSH_WORKSPACE_OPERATION(result_type, method, declaration,     \
                                         service_call)                         \
  huxerui::Task<ProjectWorkspaceResult<result_type>>                           \
      SshProjectWorkspace::method declaration {                                \
    auto context = co_await ProjectContext(std::move(project_id));             \
    if (!context)                                                              \
      co_return std::unexpected(std::move(context.error()));                   \
    auto result = co_await workspace_->service_call;                           \
    co_return FromSshResult(std::move(result));                                \
  }

LINECODE_SSH_WORKSPACE_OPERATION(domain::ProjectFileNode, LoadTree,
                                 (std::string project_id),
                                 LoadTree(context->config,
                                          context->project.path))
LINECODE_SSH_WORKSPACE_OPERATION(
    void, CreateFile, (std::string project_id, std::string relative_path),
    CreateFile(context->config, context->project.path,
               std::move(relative_path)))
LINECODE_SSH_WORKSPACE_OPERATION(
    void, CreateDirectory, (std::string project_id, std::string relative_path),
    CreateDirectory(context->config, context->project.path,
                    std::move(relative_path)))
LINECODE_SSH_WORKSPACE_OPERATION(
    std::string, ReadText, (std::string project_id, std::string relative_path),
    ReadText(context->config, context->project.path, std::move(relative_path)))
LINECODE_SSH_WORKSPACE_OPERATION(
    void, WriteText,
    (std::string project_id, std::string relative_path, std::string value),
    WriteText(context->config, context->project.path, std::move(relative_path),
              std::move(value)))
LINECODE_SSH_WORKSPACE_OPERATION(
    void, Rename,
    (std::string project_id, std::string relative_path, std::string new_name),
    Rename(context->config, context->project.path, std::move(relative_path),
           std::move(new_name)))
LINECODE_SSH_WORKSPACE_OPERATION(void, Copy,
                                 (std::string project_id,
                                  std::string source_relative_path,
                                  std::string destination_relative_path),
                                 Copy(context->config, context->project.path,
                                      std::move(source_relative_path),
                                      std::move(destination_relative_path)))
LINECODE_SSH_WORKSPACE_OPERATION(void, Move,
                                 (std::string project_id,
                                  std::string source_relative_path,
                                  std::string destination_relative_path),
                                 Move(context->config, context->project.path,
                                      std::move(source_relative_path),
                                      std::move(destination_relative_path)))
LINECODE_SSH_WORKSPACE_OPERATION(
    void, Delete, (std::string project_id, std::string relative_path),
    Delete(context->config, context->project.path, std::move(relative_path)))

#undef LINECODE_SSH_WORKSPACE_OPERATION

} // namespace linecode::application
