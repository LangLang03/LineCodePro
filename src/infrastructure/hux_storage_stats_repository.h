#pragma once

#include <vector>

#include <huxerui/file.h>

#include "application/ports/storage_stats.h"

namespace linecode::infrastructure {

class HuxStorageStatsRepository final
    : public application::StorageStatsRepository {
public:
  HuxStorageStatsRepository(huxerui::File database_file,
                            std::vector<huxerui::File> config_roots,
                            huxerui::File home_root);

  [[nodiscard]] huxerui::Task<
      std::expected<domain::StorageStats, application::StorageStatsError>>
  Load() override;

private:
  huxerui::File database_file_;
  std::vector<huxerui::File> config_roots_;
  huxerui::File home_root_;
};

} // namespace linecode::infrastructure
