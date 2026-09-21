#include "gtest_support.h"
#include <cstdint>
#include <string>

#include "application/storage_stats.h"
#include "domain/storage_stats.h"

TEST(storage_stats_tests, LegacySuite) {
  using linecode::application::FormatStorageSize;
  using linecode::domain::StorageCategoryStats;
  using linecode::domain::StorageStats;

  EXPECT_EXPRESSION(FormatStorageSize(0) == "0 B");
  EXPECT_EXPRESSION(FormatStorageSize(1023) == "1023 B");
  EXPECT_EXPRESSION(FormatStorageSize(1024) == "1 KB");
  EXPECT_EXPRESSION(FormatStorageSize(2047) == "1 KB");
  EXPECT_EXPRESSION(FormatStorageSize(1024ULL * 1024ULL) == "1 MB");
  EXPECT_EXPRESSION(FormatStorageSize(3ULL * 1024ULL * 1024ULL + 999ULL) == "3 MB");
  EXPECT_EXPRESSION(FormatStorageSize(1024ULL * 1024ULL * 1024ULL) == "1 GB");

  const StorageStats stats{
      .diff_cache = StorageCategoryStats{.bytes = 10, .count = 1},
      .chat = StorageCategoryStats{.bytes = 20, .count = 2},
      .config = StorageCategoryStats{.bytes = 30, .count = 3},
      .home = StorageCategoryStats{.bytes = 40, .count = 4},
  };
  EXPECT_EXPRESSION(stats.TotalBytes() == 100);
  EXPECT_EXPRESSION(stats.TotalCount() == 10);
}
