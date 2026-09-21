#pragma once

#include <cstdint>

namespace linecode::domain {

struct StorageCategoryStats final {
  std::uint64_t bytes{};
  std::uint64_t count{};

  bool operator==(const StorageCategoryStats &) const = default;
};

struct StorageStats final {
  StorageCategoryStats diff_cache;
  StorageCategoryStats chat;
  StorageCategoryStats config;
  StorageCategoryStats home;

  [[nodiscard]] constexpr std::uint64_t TotalBytes() const noexcept {
    return diff_cache.bytes + chat.bytes + config.bytes + home.bytes;
  }

  [[nodiscard]] constexpr std::uint64_t TotalCount() const noexcept {
    return diff_cache.count + chat.count + config.count + home.count;
  }

  bool operator==(const StorageStats &) const = default;
};

} // namespace linecode::domain
