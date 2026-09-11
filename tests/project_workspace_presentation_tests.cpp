#include <cassert>
#include <string>

#include "presentation/project_workspace_presentation.h"

int main() {
  using linecode::domain::ProjectFileNode;
  using linecode::presentation::ToggleWorkspaceDirectory;
  using linecode::presentation::WorkspaceRelativePath;

  const auto child = WorkspaceRelativePath("/workspace", "/workspace/src/a.cpp");
  assert(child && *child == "src/a.cpp");
  const auto root = WorkspaceRelativePath("/workspace", "/workspace");
  assert(root && root->empty());
  assert(!WorkspaceRelativePath("/workspace", "/workspace-other/a.cpp"));
  assert(!WorkspaceRelativePath("/workspace", "/outside/a.cpp"));
  assert(!WorkspaceRelativePath("workspace", "workspace/a.cpp"));

  ProjectFileNode tree{
      .name = "workspace",
      .path = "/workspace",
      .directory = true,
      .expanded = true,
      .children = {
          ProjectFileNode{.name = "src",
                          .path = "/workspace/src",
                          .directory = true,
                          .expanded = false,
                          .children = {}},
      },
  };
  assert(ToggleWorkspaceDirectory(tree, "/workspace/src"));
  assert(tree.children.front().expanded);
  assert(!ToggleWorkspaceDirectory(tree, "/workspace/missing"));
}
