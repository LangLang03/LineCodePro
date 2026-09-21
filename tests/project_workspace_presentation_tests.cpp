#include "gtest_support.h"
#include <string>

#include "presentation/project_workspace_presentation.h"

TEST(project_workspace_presentation_tests, LegacySuite) {
  using linecode::domain::ProjectFileNode;
  using linecode::presentation::ToggleWorkspaceDirectory;
  using linecode::presentation::WorkspaceDisplayPath;
  using linecode::presentation::WorkspaceRelativePath;

  EXPECT_EXPRESSION(WorkspaceDisplayPath("file:///workspace/src") == "/workspace/src");
  EXPECT_EXPRESSION(WorkspaceDisplayPath("content://workspace/tree") ==
         "content://workspace/tree");
  EXPECT_EXPRESSION(WorkspaceDisplayPath("").empty());

  const auto child =
      WorkspaceRelativePath("/workspace", "/workspace/src/a.cpp");
  EXPECT_EXPRESSION(child && *child == "src/a.cpp");
  const auto root = WorkspaceRelativePath("/workspace", "/workspace");
  EXPECT_EXPRESSION(root && root->empty());
  EXPECT_EXPRESSION(!WorkspaceRelativePath("/workspace", "/workspace-other/a.cpp"));
  EXPECT_EXPRESSION(!WorkspaceRelativePath("/workspace", "/outside/a.cpp"));
  EXPECT_EXPRESSION(!WorkspaceRelativePath("workspace", "workspace/a.cpp"));

  ProjectFileNode tree{
      .name = "workspace",
      .path = "/workspace",
      .directory = true,
      .expanded = true,
      .children =
          {
              ProjectFileNode{.name = "src",
                              .path = "/workspace/src",
                              .directory = true,
                              .expanded = false,
                              .children = {}},
          },
  };
  EXPECT_EXPRESSION(ToggleWorkspaceDirectory(tree, "/workspace/src"));
  EXPECT_EXPRESSION(tree.children.front().expanded);
  EXPECT_EXPRESSION(!ToggleWorkspaceDirectory(tree, "/workspace/missing"));
}
