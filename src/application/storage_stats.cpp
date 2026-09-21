#include "application/storage_stats.h"

#include <array>
#include <cstddef>
#include <string>

namespace linecode::application {

std::string FormatStorageSize(std::uint64_t bytes) {
  struct Unit final {
    std::uint64_t threshold;
    std::uint64_t divisor;
    const char *suffix;
  };
  constexpr std::array units{
      Unit{1024ULL * 1024ULL * 1024ULL, 1024ULL * 1024ULL * 1024ULL, " GB"},
      Unit{1024ULL * 1024ULL, 1024ULL * 1024ULL, " MB"},
      Unit{1024ULL, 1024ULL, " KB"},
  };
  for (const auto &unit : units) {
    if (bytes >= unit.threshold) {
      return std::to_string(bytes / unit.divisor) + unit.suffix;
    }
  }
  return std::to_string(bytes) + " B";
}

} // namespace linecode::application
