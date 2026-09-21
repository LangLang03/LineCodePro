#pragma once

#include <expected>
#include <string>
#include <string_view>

namespace linecode::infrastructure {

enum class ModelUrlError {
  invalid,
  cleartext_not_allowed,
};

struct ModelUrlValidationError final {
  ModelUrlError code{ModelUrlError::invalid};
  std::string message;

  bool operator==(const ModelUrlValidationError &) const = default;
};

[[nodiscard]] std::expected<std::string, ModelUrlValidationError>
ValidateModelBaseUrl(std::string_view value);

} // namespace linecode::infrastructure
