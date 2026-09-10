#pragma once

#include <cstdint>
#include <expected>
#include <string>
#include <string_view>

namespace linecode::infrastructure {

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

} // namespace linecode::infrastructure
