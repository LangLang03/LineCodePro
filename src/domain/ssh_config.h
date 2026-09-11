#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace linecode::domain {

inline constexpr std::string_view kDefaultSshHost = "127.0.0.1";
inline constexpr std::int32_t kDefaultSshPort = 8022;

struct SshConfig final {
  std::string host{std::string{kDefaultSshHost}};
  std::int32_t port{kDefaultSshPort};
  std::string username;
  std::string password;
  std::string private_key;
  std::string passphrase;

  [[nodiscard]] bool IsConfigured() const noexcept;
  bool operator==(const SshConfig&) const = default;
};

[[nodiscard]] SshConfig NormalizeSshConfig(SshConfig config);
[[nodiscard]] std::int32_t ParseSshPort(std::string_view value) noexcept;

} // namespace linecode::domain
