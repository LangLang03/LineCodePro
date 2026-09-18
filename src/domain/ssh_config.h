#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace linecode::domain {

inline constexpr std::string_view kDefaultSshHost = "127.0.0.1";
inline constexpr std::int32_t kDefaultSshPort = 8022;

enum class SshConfigGap : std::uint8_t {
  none,
  host,
  port,
  username,
  credentials,
};

[[nodiscard]] std::string_view DescribeSshConfigGap(SshConfigGap gap) noexcept;

struct SshConfig final {
  std::string host{std::string{kDefaultSshHost}};
  std::int32_t port{kDefaultSshPort};
  std::string username;
  std::string password;
  std::string private_key;
  std::string passphrase;

  [[nodiscard]] bool IsConfigured() const noexcept;
  // What still has to be filled in before this config can connect. `IsConfigured`
  // alone reports "no" for several different situations, and telling a user who
  // has just saved a form that SSH "is not configured" sends them looking in the
  // wrong place: the usual cause is a missing password or key, not a missing
  // host.
  [[nodiscard]] SshConfigGap Gap() const noexcept;
  bool operator==(const SshConfig &) const = default;
};


[[nodiscard]] SshConfig NormalizeSshConfig(SshConfig config);
[[nodiscard]] std::int32_t ParseSshPort(std::string_view value) noexcept;
[[nodiscard]] bool IsTermuxSshHost(std::string_view host) noexcept;

} // namespace linecode::domain
