#pragma once

#include <expected>
#include <string>

#include <huxerui/task.h>

#include "domain/storage_stats.h"

namespace linecode::application {

struct StorageStatsError final {
  std::string message;

  bool operator==(const StorageStatsError &) const = default;
};

class StorageStatsRepository {
public:
  virtual ~StorageStatsRepository() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<domain::StorageStats, StorageStatsError>>
  Load() = 0;
};

} // namespace linecode::application
