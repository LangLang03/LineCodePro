#pragma once

#include <memory>

#include <huxerui/file.h>

#include "application/ports/project_workspace_store.h"

namespace linecode::infrastructure {

class SqliteProjectCatalogStoreState;

enum class ProjectCatalogScope : std::uint8_t {
  local,
  ssh,
};

// Persists one isolated project catalog in the legacy LineCode `projects`
// table. Local and SSH records share the schema but never overwrite one
// another and retain their independent selected-project settings.
class SqliteProjectCatalogStore final
    : public application::ProjectCatalogStore {
public:
  explicit SqliteProjectCatalogStore(
      huxerui::File database_file,
      ProjectCatalogScope scope = ProjectCatalogScope::local);

  [[nodiscard]] huxerui::Task<
      application::ProjectWorkspaceResult<domain::ProjectCatalog>>
  LoadCatalog() override;
  [[nodiscard]] huxerui::Task<application::ProjectWorkspaceResult<void>>
  ReplaceCatalog(domain::ProjectCatalog catalog) override;

private:
  std::shared_ptr<SqliteProjectCatalogStoreState> state_;
};

} // namespace linecode::infrastructure
