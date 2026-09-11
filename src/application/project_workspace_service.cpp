#include "application/project_workspace_service.h"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <ranges>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

ProjectWorkspaceError Error(ProjectWorkspaceErrorCode code,
                            std::string message) {
  return {.code = code, .message = std::move(message)};
}

std::string Trim(std::string value) {
  const auto visible = [](unsigned char value) { return !std::isspace(value); };
  const auto first = std::ranges::find_if(value, visible);
  const auto last =
      std::ranges::find_if(value | std::views::reverse, visible).base();
  if (first >= last)
    return {};
  return std::string(first, last);
}

std::string ManagedDirectoryName(std::string name) {
  name = Trim(std::move(name));
  std::string result;
  result.reserve(std::min<std::size_t>(name.size(), 180));
  bool previous_separator{};
  std::size_t character_count{};
  for (std::size_t index = 0; index < name.size() && character_count < 60;
       ++character_count) {
    const auto byte = static_cast<unsigned char>(name[index]);
    std::size_t sequence_size = 1;
    if (byte >= 0x80) {
      sequence_size = byte < 0xE0 ? 2 : byte < 0xF0 ? 3 : byte < 0xF8 ? 4 : 0;
      if (sequence_size == 0 || index + sequence_size > name.size() ||
          !std::ranges::all_of(
              std::string_view{name}.substr(index + 1, sequence_size - 1),
              [](const unsigned char continuation) {
                return (continuation & 0xC0U) == 0x80U;
              }))
        return {};
      result.append(name, index, sequence_size);
      index += sequence_size;
      previous_separator = false;
      continue;
    }
    const bool forbidden = byte < 0x20 || byte == '\\' || byte == '/' ||
                           byte == ':' || byte == '*' || byte == '?' ||
                           byte == '"' || byte == '<' || byte == '>' ||
                           byte == '|';
    const bool separator = forbidden || std::isspace(byte);
    if (separator) {
      if (!result.empty() && !previous_separator)
        result.push_back('-');
      previous_separator = true;
      ++index;
      continue;
    }
    result.push_back(static_cast<char>(byte));
    previous_separator = false;
    ++index;
  }
  while (!result.empty() && result.back() == '-')
    result.pop_back();
  return result;
}

std::string LowerAscii(std::string value) {
  std::ranges::transform(value, value.begin(), [](const unsigned char byte) {
    return byte < 0x80 ? static_cast<char>(std::tolower(byte))
                       : static_cast<char>(byte);
  });
  return value;
}

std::string Basename(std::string_view path) {
  return std::filesystem::path{path}.filename().string();
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

ProjectWorkspaceService::ProjectWorkspaceService(
    std::shared_ptr<ProjectCatalogStore> catalog,
    std::shared_ptr<WorkspaceFileStore> files,
    std::shared_ptr<WorkspaceClock> clock)
    : catalog_(std::move(catalog)), files_(std::move(files)),
      clock_(std::move(clock)) {
  if (!catalog_ || !files_ || !clock_)
    throw std::invalid_argument(
        "ProjectWorkspaceService requires catalog, files, and clock");
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectCatalog>>
ProjectWorkspaceService::LoadReadyCatalog() {
  auto loaded = co_await catalog_->LoadCatalog();
  if (!loaded)
    co_return std::unexpected(std::move(loaded.error()));

  auto default_root = co_await huxerui::RunWorker(
      [files = files_] { return files->PrepareDefaultProject(); });
  if (!default_root)
    co_return std::unexpected(std::move(default_root.error()));

  auto catalog = std::move(*loaded);
  bool changed{};
  auto home = FindProject(catalog, domain::default_project_id);
  const auto now = clock_->NowMilliseconds();
  if (home == catalog.projects.end()) {
    catalog.projects.push_back(domain::ProjectRecord{
        .id = std::string{domain::default_project_id},
        .label = "LineCode",
        .path = *default_root,
        .source = domain::ProjectSource::default_home,
        .description = "Default home workspace",
        .selected = false,
        .created_at = now,
        .updated_at = now,
    });
    changed = true;
  } else if (home->path != *default_root ||
             home->source != domain::ProjectSource::default_home) {
    home->path = *default_root;
    home->source = domain::ProjectSource::default_home;
    home->updated_at = now;
    changed = true;
  }

  if (catalog.selected_id.empty() ||
      FindProject(catalog, catalog.selected_id) == catalog.projects.end()) {
    catalog.selected_id = domain::default_project_id;
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
ProjectWorkspaceService::ListProjects() {
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
ProjectWorkspaceService::SelectedProject() {
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const auto selected = FindProject(*catalog, catalog->selected_id);
  if (selected == catalog->projects.end())
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::corrupt_catalog,
              "Selected project is missing from the project catalog"));
  co_return *selected;
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
ProjectWorkspaceService::CreateManagedProject(std::string name) {
  const auto directory_name = ManagedDirectoryName(name);
  if (directory_name.empty())
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                    "Project name is empty"));
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const std::string id = "managed:" + LowerAscii(directory_name);
  if (FindProject(*catalog, id) != catalog->projects.end())
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::conflict,
                                    "A project with this name already exists"));

  auto directory =
      co_await huxerui::RunWorker([files = files_, directory_name] {
        return files->PrepareManagedProject(directory_name);
      });
  if (!directory)
    co_return std::unexpected(std::move(directory.error()));
  const auto now = clock_->NowMilliseconds();
  domain::ProjectRecord project{
      .id = id,
      .label = directory_name,
      .path = directory->path,
      .source = domain::ProjectSource::managed,
      .description = ".linecode/project",
      .selected = true,
      .created_at = now,
      .updated_at = now,
  };
  catalog->projects.push_back(project);
  Select(*catalog, id, now);
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored) {
    if (directory->created)
      static_cast<void>(
          co_await huxerui::RunWorker([files = files_, directory = *directory] {
            return files->RollbackManagedProject(directory);
          }));
    co_return std::unexpected(std::move(stored.error()));
  }
  co_return project;
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
ProjectWorkspaceService::RegisterExternalProject(std::string path,
                                                 std::string label) {
  auto resolved =
      co_await huxerui::RunWorker([files = files_, path = std::move(path)] {
        return files->ResolveDirectory(path);
      });
  if (!resolved)
    co_return std::unexpected(std::move(resolved.error()));
  label = Trim(std::move(label));
  if (label.empty())
    label = Basename(*resolved);
  if (label.empty())
    label = "External workspace";

  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const std::string id = "external:" + *resolved;
  const auto now = clock_->NowMilliseconds();
  auto existing = FindProject(*catalog, id);
  if (existing == catalog->projects.end()) {
    catalog->projects.push_back(domain::ProjectRecord{
        .id = id,
        .label = label,
        .path = *resolved,
        .source = domain::ProjectSource::external,
        .description = *resolved,
        .selected = true,
        .created_at = now,
        .updated_at = now,
    });
  } else {
    existing->label = std::move(label);
    existing->path = *resolved;
    existing->source = domain::ProjectSource::external;
    existing->description = *resolved;
    existing->updated_at = now;
  }
  Select(*catalog, id, now);
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return *FindProject(*catalog, id);
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectRecord>>
ProjectWorkspaceService::SelectProject(std::string id) {
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  auto selected = FindProject(*catalog, id);
  if (selected == catalog->projects.end())
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::not_found, "Project was not found"));
  auto root =
      co_await huxerui::RunWorker([files = files_, path = selected->path] {
        return files->ResolveDirectory(path);
      });
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  selected->path = std::move(*root);
  Select(*catalog, id, clock_->NowMilliseconds());
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return *FindProject(*catalog, id);
}

huxerui::Task<ProjectWorkspaceResult<void>>
ProjectWorkspaceService::DeleteProject(std::string id) {
  if (id.empty())
    co_return std::unexpected(Error(ProjectWorkspaceErrorCode::invalid_argument,
                                    "Project id is empty"));
  if (id == domain::default_project_id)
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::protected_project,
              "The default project cannot be removed"));
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
    Select(*catalog, domain::default_project_id, clock_->NowMilliseconds());
  auto stored = co_await catalog_->ReplaceCatalog(*catalog);
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return ProjectWorkspaceResult<void>{};
}

huxerui::Task<ProjectWorkspaceResult<std::string>>
ProjectWorkspaceService::ProjectRoot(std::string project_id) {
  auto catalog = co_await LoadReadyCatalog();
  if (!catalog)
    co_return std::unexpected(std::move(catalog.error()));
  const auto project = FindProject(*catalog, project_id);
  if (project == catalog->projects.end())
    co_return std::unexpected(
        Error(ProjectWorkspaceErrorCode::not_found, "Project was not found"));
  co_return co_await huxerui::RunWorker([files = files_, path = project->path] {
    return files->ResolveDirectory(path);
  });
}

huxerui::Task<ProjectWorkspaceResult<domain::ProjectFileNode>>
ProjectWorkspaceService::LoadTree(std::string project_id) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root)] {
        return files->LoadTree(root);
      });
}

huxerui::Task<ProjectWorkspaceResult<void>>
ProjectWorkspaceService::CreateFile(std::string project_id,
                                    std::string relative_path) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       relative_path = std::move(relative_path)] {
        return files->CreateFile(root, relative_path);
      });
}

huxerui::Task<ProjectWorkspaceResult<void>>
ProjectWorkspaceService::CreateDirectory(std::string project_id,
                                         std::string relative_path) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       relative_path = std::move(relative_path)] {
        return files->CreateDirectory(root, relative_path);
      });
}

huxerui::Task<ProjectWorkspaceResult<void>>
ProjectWorkspaceService::Delete(std::string project_id,
                                std::string relative_path) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       relative_path = std::move(relative_path)] {
        return files->Delete(root, relative_path);
      });
}

huxerui::Task<ProjectWorkspaceResult<std::string>>
ProjectWorkspaceService::ReadText(std::string project_id,
                                  std::string relative_path) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       relative_path = std::move(relative_path)] {
        return files->ReadText(root, relative_path);
      });
}

huxerui::Task<ProjectWorkspaceResult<void>> ProjectWorkspaceService::WriteText(
    std::string project_id, std::string relative_path, std::string value) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       relative_path = std::move(relative_path), value = std::move(value)] {
        return files->WriteText(root, relative_path, std::move(value));
      });
}

huxerui::Task<ProjectWorkspaceResult<void>> ProjectWorkspaceService::Rename(
    std::string project_id, std::string relative_path, std::string new_name) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       relative_path = std::move(relative_path),
       new_name = std::move(new_name)] {
        return files->Rename(root, relative_path, new_name);
      });
}

huxerui::Task<ProjectWorkspaceResult<void>>
ProjectWorkspaceService::Copy(std::string project_id,
                              std::string source_relative_path,
                              std::string destination_relative_path) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       source = std::move(source_relative_path),
       destination = std::move(destination_relative_path)] {
        return files->Copy(root, source, destination);
      });
}

huxerui::Task<ProjectWorkspaceResult<void>>
ProjectWorkspaceService::Move(std::string project_id,
                              std::string source_relative_path,
                              std::string destination_relative_path) {
  auto root = co_await ProjectRoot(std::move(project_id));
  if (!root)
    co_return std::unexpected(std::move(root.error()));
  co_return co_await huxerui::RunWorker(
      [files = files_, root = std::move(*root),
       source = std::move(source_relative_path),
       destination = std::move(destination_relative_path)] {
        return files->Move(root, source, destination);
      });
}

} // namespace linecode::application
