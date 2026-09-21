#pragma once

#include <expected>
#include <string>

#include <huxerui/task.h>

#include "application/legacy_data_archive.h"
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
  [[nodiscard]] virtual huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
  ImportLegacy(LegacyArchiveData data, domain::ArchiveImportMode mode) = 0;
  // The inverse of `ImportLegacy`: reads this installation's models,
  // conversations, messages and settings in the shapes the legacy app reads,
  // so an export is importable there. `ExportRedacted` alone is not enough --
  // it produces our own table snapshot, which the legacy never consults for
  // models, conversations or settings.
  [[nodiscard]] virtual huxerui::Task<DataArchiveResult<LegacyArchiveData>>
  ExportLegacy() = 0;
};

} // namespace linecode::application
