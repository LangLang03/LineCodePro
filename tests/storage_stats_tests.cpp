#include <cassert>
#include <cstdint>
#include <string>

#include "application/storage_stats.h"
#include "domain/storage_stats.h"

int main() {
  using linecode::application::FormatStorageSize;
  using linecode::domain::StorageCategoryStats;
  using linecode::domain::StorageStats;

  assert(FormatStorageSize(0) == "0 B");
  assert(FormatStorageSize(1023) == "1023 B");
  assert(FormatStorageSize(1024) == "1 KB");
  assert(FormatStorageSize(2047) == "1 KB");
  assert(FormatStorageSize(1024ULL * 1024ULL) == "1 MB");
  assert(FormatStorageSize(3ULL * 1024ULL * 1024ULL + 999ULL) == "3 MB");
  assert(FormatStorageSize(1024ULL * 1024ULL * 1024ULL) == "1 GB");

  const StorageStats stats{
      .diff_cache = StorageCategoryStats{.bytes = 10, .count = 1},
      .chat = StorageCategoryStats{.bytes = 20, .count = 2},
      .config = StorageCategoryStats{.bytes = 30, .count = 3},
      .home = StorageCategoryStats{.bytes = 40, .count = 4},
  };
  assert(stats.TotalBytes() == 100);
  assert(stats.TotalCount() == 10);
}
