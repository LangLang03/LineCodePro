#pragma once

#include <expected>
#include <string>

#include <huxerui/task.h>

#include "application/ports/data_archive.h"

namespace linecode::application {

struct ArchiveDatabaseExport final {
  std::string json;
  domain::ArchiveSummary summary;
};

class ArchiveDatabase {
public:
  virtual ~ArchiveDatabase() = default;
  [[nodiscard]] virtual huxerui::Task<DataArchiveResult<ArchiveDatabaseExport>>
  ExportRedacted() = 0;
  [[nodiscard]] virtual huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
  ReplaceFromSnapshot(std::string json) = 0;
};

} // namespace linecode::application
