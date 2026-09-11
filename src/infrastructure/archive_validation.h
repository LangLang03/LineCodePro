#pragma once

#include <cstdint>
#include <expected>
#include <span>
#include <string>
#include <string_view>

#include "application/legacy_data_archive.h"

namespace linecode::infrastructure {

struct ZipEntryData;

inline constexpr std::int64_t kCurrentArchiveDatabaseSchemaVersion = 4;

struct ArchiveValidationError final {
  std::string message;
  bool operator==(const ArchiveValidationError &) const = default;
};

struct ValidatedArchiveManifest final {
  bool contains_database{};
  bool operator==(const ValidatedArchiveManifest &) const = default;
};

[[nodiscard]] std::expected<ValidatedArchiveManifest, ArchiveValidationError>
ValidateArchiveManifest(std::string_view text);

[[nodiscard]] std::expected<void, ArchiveValidationError>
ValidateDatabaseSnapshot(std::string_view text,
                         std::int64_t maximum_schema_version);

[[nodiscard]] std::expected<application::LegacyArchiveData,
                            ArchiveValidationError>
DecodeLegacyArchive(std::span<const ZipEntryData> entries,
                    std::int64_t fallback_timestamp);

} // namespace linecode::infrastructure
