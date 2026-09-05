#pragma once

#include <string>
#include <vector>

namespace linecode::domain {

struct ProjectFileNode final {
  std::string name;
  std::string path;
  bool directory = false;
  bool expanded = false;
  std::vector<ProjectFileNode> children;

  bool operator==(const ProjectFileNode &) const = default;
};

struct ProjectWorkspace final {
  std::string label;
  std::string path;
  ProjectFileNode root;

  bool operator==(const ProjectWorkspace &) const = default;
};

} // namespace linecode::domain
