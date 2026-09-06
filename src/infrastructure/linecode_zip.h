#pragma once

#include <expected>
#include <span>
#include <string>
#include <vector>

#include <huxerui/data.h>

namespace linecode::infrastructure {

struct ZipEntryData final {
  std::string name;
  huxerui::Bytes content;

  bool operator==(const ZipEntryData &) const = default;
};

struct ZipError final {
  std::string message;
  bool operator==(const ZipError &) const = default;
};

using ZipResult = std::expected<std::vector<ZipEntryData>, ZipError>;

[[nodiscard]] std::expected<huxerui::Bytes, ZipError>
WriteLineCodeZip(std::span<const ZipEntryData> entries);
[[nodiscard]] ZipResult ReadLineCodeZip(std::span<const std::byte> archive);
[[nodiscard]] bool IsSafeArchivePath(std::string_view name) noexcept;

} // namespace linecode::infrastructure
