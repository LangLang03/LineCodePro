#pragma once

#include <cstdint>

namespace linecode::domain {

struct ArchiveSummary final {
  std::uint64_t conversations{};
  std::uint64_t models{};
  std::uint64_t settings{};
  std::uint64_t restored_files{};

  bool operator==(const ArchiveSummary &) const = default;
};

enum class ArchiveImportMode : std::uint8_t { merge, replace };

} // namespace linecode::domain
