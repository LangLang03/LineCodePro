#pragma once

#include <utility>

#include <huxerui/file.h>

#include "application/ports/archive_database.h"

namespace linecode::infrastructure {

// Infrastructure adapter for the versioned database.json payload used by the
// Android LineCode .linecode container. Presentation and application code only
// depend on ArchiveDatabase.
class SqliteArchiveDatabase final : public application::ArchiveDatabase {
public:
  explicit SqliteArchiveDatabase(huxerui::File database_file)
      : database_file_(std::move(database_file)) {}

  [[nodiscard]] huxerui::Task<application::DataArchiveResult<
      application::ArchiveDatabaseExport>>
  ExportRedacted() override;

  [[nodiscard]] huxerui::Task<
      application::DataArchiveResult<domain::ArchiveSummary>>
  ReplaceFromSnapshot(std::string json) override;

private:
  huxerui::File database_file_;
};

} // namespace linecode::infrastructure
