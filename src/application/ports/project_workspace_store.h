#pragma once

#include <expected>
#include <string>

#include <huxerui/task.h>

#include "domain/project_workspace.h"

namespace linecode::application {

struct ProjectWorkspaceError final {
  std::string message;

  bool operator==(const ProjectWorkspaceError &) const = default;
};

class ProjectWorkspaceStore {
public:
  virtual ~ProjectWorkspaceStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<domain::ProjectWorkspace, ProjectWorkspaceError>>
  Load() = 0;
};

} // namespace linecode::application
