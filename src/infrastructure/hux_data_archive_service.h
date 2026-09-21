#pragma once

#include <array>
#include <chrono>
#include <memory>

#include <huxerui/data.h>
#include <huxerui/file.h>

#include "application/ports/archive_database.h"
#include "application/ports/data_archive.h"

namespace linecode::infrastructure {

class HuxDataArchiveService final : public application::DataArchiveService {
public:
  HuxDataArchiveService(
      std::shared_ptr<application::ArchiveDatabase> database,
      huxerui::File temporary_directory, huxerui::File home_root,
      huxerui::File project_root, huxerui::File skills_root);

  [[nodiscard]] huxerui::Task<
      application::DataArchiveResult<application::PreparedDataArchive>>
  PrepareExport() override;
  [[nodiscard]] huxerui::Task<
      application::DataArchiveResult<domain::ArchiveSummary>>
  Import(huxerui::FileReference source,
         domain::ArchiveImportMode mode) override;

  // Imports an already-owned archive payload. FileReference remains the UI
  // capability boundary; this overload keeps decode/transaction policy
  // independently testable and reusable by non-picker application flows.
  [[nodiscard]] huxerui::Task<
      application::DataArchiveResult<domain::ArchiveSummary>>
  ImportBytes(huxerui::Bytes archive, domain::ArchiveImportMode mode);

private:
  std::shared_ptr<application::ArchiveDatabase> database_;
  huxerui::File temporary_directory_;
  std::array<huxerui::File, 3> roots_;
};

} // namespace linecode::infrastructure
