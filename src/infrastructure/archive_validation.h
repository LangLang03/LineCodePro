#pragma once

#include <cstdint>
#include <expected>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

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

// The legacy-compatible half of an export.
//
// `DecodeLegacyArchive` reads the shapes the legacy app writes; this is the
// matching writer. Without it an export carries no models, conversations or
// settings in `async-storage.json`, so the legacy app -- which reads exactly
// that entry on import -- restores nothing.
struct LegacyArchiveEncoding final {
  // Contents of `async-storage.json`.
  std::string async_storage_json;
  // `conversations/<safe name>` entries, in the same order as the input.
  std::vector<std::pair<std::string, std::string>> conversation_files;
};

[[nodiscard]] LegacyArchiveEncoding
EncodeLegacyArchive(const application::LegacyArchiveData &data);

// The legacy `ModelConfig.toJson()` shape, including the upper-snake
// `protocolType` the legacy app expects. Exposed because the export reads
// models from their own table and has to hand back this shape, not ours.
[[nodiscard]] std::string
EncodeLegacyModelJson(const domain::ModelConfig &model);

} // namespace linecode::infrastructure
