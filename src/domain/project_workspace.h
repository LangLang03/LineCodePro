#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::domain {

inline constexpr std::string_view default_project_id = "default";

enum class ProjectSource : std::uint8_t {
  default_home,
  managed,
  external,
  ssh,
};

struct ProjectRecord final {
  std::string id;
  std::string label;
  std::string path;
  ProjectSource source{ProjectSource::managed};
  std::string description;
  bool selected{};
  std::int64_t created_at{};
  std::int64_t updated_at{};

  bool operator==(const ProjectRecord &) const = default;
};

struct ProjectCatalog final {
  std::vector<ProjectRecord> projects;
  std::string selected_id;

  bool operator==(const ProjectCatalog &) const = default;
};

struct ProjectFileNode final {
  std::string name;
  std::string path;
  bool directory = false;
  bool symbolic_link = false;
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
