#pragma once

#include <expected>

#include <huxerui/file.h>

#include "application/ports/project_workspace_store.h"

namespace linecode::infrastructure {

class HuxProjectWorkspaceStore final
    : public application::ProjectWorkspaceStore {
public:
  explicit HuxProjectWorkspaceStore(huxerui::File root);

  [[nodiscard]] huxerui::Task<std::expected<domain::ProjectWorkspace,
                                            application::ProjectWorkspaceError>>
  Load() override;

private:
  [[nodiscard]] huxerui::Task<std::expected<domain::ProjectFileNode,
                                            application::ProjectWorkspaceError>>
  LoadRoot();

  huxerui::File root_;
};

} // namespace linecode::infrastructure
