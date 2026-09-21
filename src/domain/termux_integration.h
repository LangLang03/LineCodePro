#pragma once

#include <cstdint>
#include <expected>
#include <string>
#include <string_view>

#include "domain/ssh_config.h"

namespace linecode::domain {

enum class TermuxErrorCode : std::uint8_t {
  unsupported,
  not_installed,
  permission_denied,
  no_result,
  command_failed,
  timeout,
  parse_failed,
  bridge_closed,
  platform_error,
  persistence_failed,
  verification_failed,
};

struct TermuxError final {
  TermuxErrorCode code{TermuxErrorCode::platform_error};
  std::string detail;

  bool operator==(const TermuxError &) const = default;
};

template <class Value>
using TermuxResult = std::expected<Value, TermuxError>;

struct TermuxPlatformState final {
  bool installed{};
  bool run_command_permission_granted{};

  bool operator==(const TermuxPlatformState &) const = default;
};

struct TermuxSetupResult final {
  SshConfig config;
  std::string shell;
  std::string rc_path;
  std::string verification_output;
  std::int32_t verification_exit_code{-1};

  [[nodiscard]] bool ConnectionVerified() const noexcept {
    return verification_exit_code == 0;
  }

  bool operator==(const TermuxSetupResult &) const = default;
};

[[nodiscard]] TermuxResult<TermuxSetupResult>
ParseTermuxSetupOutput(std::string_view output);

[[nodiscard]] std::string RedactTermuxPrivateKey(
    std::string_view output,
    std::string_view replacement =
        "LINEAI_PRIVATE_KEY=[saved to SSH Private key]");

} // namespace linecode::domain
