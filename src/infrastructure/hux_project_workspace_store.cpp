#include "infrastructure/hux_project_workspace_store.h"

#include <algorithm>
#include <ranges>
#include <utility>
#include <vector>

namespace linecode::infrastructure {
namespace {

application::ProjectWorkspaceError WorkspaceError(std::string message) {
  return application::ProjectWorkspaceError{.message = std::move(message)};
}

} // namespace

HuxProjectWorkspaceStore::HuxProjectWorkspaceStore(huxerui::File root)
    : root_(std::move(root)) {}

huxerui::Task<
    std::expected<domain::ProjectWorkspace, application::ProjectWorkspaceError>>
HuxProjectWorkspaceStore::Load() {
  if (!co_await root_.CreateDirectoriesAsync()) {
    co_return std::unexpected(
        WorkspaceError("Unable to create the LineCode workspace"));
  }
  if (!co_await root_.Child(".linecode").CreateDirectoriesAsync()) {
    co_return std::unexpected(
        WorkspaceError("Unable to create project metadata directory"));
  }

  auto root = co_await LoadRoot();
  if (!root) {
    co_return std::unexpected(std::move(root.error()));
  }
  co_return domain::ProjectWorkspace{
      .label = "LineCode",
      .path = root_.Path(),
      .root = std::move(*root),
  };
}

huxerui::Task<
    std::expected<domain::ProjectFileNode, application::ProjectWorkspaceError>>
HuxProjectWorkspaceStore::LoadRoot() {
  auto listed = co_await root_.ListChildrenAsync();
  if (!listed.Succeeded()) {
    co_return std::unexpected(WorkspaceError(listed.Error().message));
  }

  std::vector<domain::ProjectFileNode> children;
  children.reserve(listed.Value().size());
  for (const auto &file : listed.Value()) {
    auto info = co_await file.StatAsync();
    if (!info.Succeeded()) {
      continue;
    }
    children.push_back(domain::ProjectFileNode{
        .name = file.Name(),
        .path = file.Path(),
        .directory = info.Value().type == huxerui::FileType::Directory,
    });
  }
  std::ranges::sort(children, [](const auto &left, const auto &right) {
    if (left.directory != right.directory) {
      return left.directory > right.directory;
    }
    return left.name < right.name;
  });

  co_return domain::ProjectFileNode{
      .name = root_.Name(),
      .path = root_.Path(),
      .directory = true,
      .expanded = true,
      .children = std::move(children),
  };
}

} // namespace linecode::infrastructure
