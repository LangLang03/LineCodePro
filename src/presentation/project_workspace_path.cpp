#include "presentation/project_workspace_presentation.h"

#include <filesystem>
#include <iterator>
#include <ranges>
#include <utility>

namespace linecode::presentation {

std::expected<std::string, std::string>
WorkspaceRelativePath(std::string_view root, std::string_view absolute_path) {
  if (root.empty() || absolute_path.empty())
    return std::unexpected("Workspace path is empty");
  const auto normalized_root = std::filesystem::path{root}.lexically_normal();
  const auto normalized_path =
      std::filesystem::path{absolute_path}.lexically_normal();
  if (!normalized_root.is_absolute() || !normalized_path.is_absolute())
    return std::unexpected("Workspace paths must be absolute");
  const auto relative = normalized_path.lexically_relative(normalized_root);
  if (relative.empty() && normalized_path != normalized_root)
    return std::unexpected("Path is outside the workspace");
  if (relative.is_absolute() ||
      std::ranges::any_of(relative, [](const auto &part) {
        return part == std::filesystem::path{".."};
      }))
    return std::unexpected("Path is outside the workspace");
  if (relative == std::filesystem::path{"."})
    return std::string{};
  return relative.generic_string();
}

DrawerFileNode ToDrawerFileNode(const domain::ProjectFileNode &node) {
  std::vector<DrawerFileNode> children;
  children.reserve(node.children.size());
  std::ranges::transform(node.children, std::back_inserter(children),
                         ToDrawerFileNode);
  return DrawerFileNode{
      .name = node.name,
      .path = node.path,
      .directory = node.directory,
      .expanded = node.expanded,
      .children = std::move(children),
  };
}

bool ToggleWorkspaceDirectory(domain::ProjectFileNode &node,
                              std::string_view absolute_path) {
  if (node.directory && node.path == absolute_path) {
    node.expanded = !node.expanded;
    return true;
  }
  return std::ranges::any_of(node.children, [&](auto &child) {
    return ToggleWorkspaceDirectory(child, absolute_path);
  });
}

} // namespace linecode::presentation
