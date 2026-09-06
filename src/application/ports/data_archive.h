#pragma once

#include <expected>
#include <string>

#include <huxerui/file.h>
#include <huxerui/task.h>

#include "domain/data_archive.h"

namespace linecode::application {

struct DataArchiveError final {
  std::string message;
  bool operator==(const DataArchiveError &) const = default;
};

template <class T>
using DataArchiveResult = std::expected<T, DataArchiveError>;

struct PreparedDataArchive final {
  huxerui::File file;
  std::string suggested_name;
  domain::ArchiveSummary summary;
};

class DataArchiveService {
public:
  virtual ~DataArchiveService() = default;
  [[nodiscard]] virtual huxerui::Task<DataArchiveResult<PreparedDataArchive>>
  PrepareExport() = 0;
  [[nodiscard]] virtual huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
  Import(huxerui::FileReference source, domain::ArchiveImportMode mode) = 0;
};

} // namespace linecode::application
