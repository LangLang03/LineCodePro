#pragma once

#include <expected>
#include <string>
#include <string_view>

#include "domain/ssh_config.h"

namespace linecode::infrastructure {

struct SshConfigCodecError final {
  std::string message;
};

[[nodiscard]] std::string EncodeSshConfig(const domain::SshConfig& config);
[[nodiscard]] std::expected<domain::SshConfig, SshConfigCodecError>
DecodeSshConfig(std::string_view json);

} // namespace linecode::infrastructure
