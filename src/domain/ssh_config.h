#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

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


// Authentication capabilities a server advertises in `SSH_MSG_USERAUTH_FAILURE`.
// A server that enables only `keyboard-interactive` (the OpenSSH/PAM default on
// many distributions and Termux builds) refuses `password` outright, which reads
// as "wrong password" in a client that only implements password auth.
struct SshAuthMethods final {
  bool public_key{};
  bool password{};
  bool keyboard_interactive{};

  [[nodiscard]] bool Any() const noexcept {
    return public_key || password || keyboard_interactive;
  }
  bool operator==(const SshAuthMethods &) const = default;
};

enum class SshAuthMethod : std::uint8_t {
  public_key,
  password,
  keyboard_interactive,
};

// Parses the comma-separated list returned by `libssh2_userauth_list`.
// Unknown names (gssapi-with-mic, hostbased, ...) are ignored.
[[nodiscard]] SshAuthMethods
ParseSshAuthMethods(std::string_view advertised) noexcept;

// The ordered plan for one connection attempt. Keys go first because a key that
// the server accepts costs one round trip, while password auth can be rate
// limited or locked out. Methods the server did not advertise are dropped; an
// empty advertisement (list request still in flight or refused) falls back to
// whatever credentials the config carries, so a server that answers late still
// gets a chance.
[[nodiscard]] std::vector<SshAuthMethod>
PlanSshAuthentication(const SshConfig &config, SshAuthMethods advertised);

// Human-readable form for diagnostics. Never contains credentials.
[[nodiscard]] std::string DescribeSshAuthMethods(SshAuthMethods methods);

// How much of the remaining connect budget a single resolved address may use.
// `getaddrinfo` commonly returns IPv6 first on hosts that only route IPv4; if
// the first address consumed the whole budget, every later address was skipped
// and the connection failed although a reachable address was known. Splitting
// the budget keeps at least five seconds per address while still bounding the
// total.
[[nodiscard]] std::chrono::milliseconds
ConnectAttemptBudget(std::chrono::milliseconds remaining,
                     std::size_t addresses_remaining) noexcept;

[[nodiscard]] SshConfig NormalizeSshConfig(SshConfig config);
[[nodiscard]] std::int32_t ParseSshPort(std::string_view value) noexcept;
[[nodiscard]] bool IsTermuxSshHost(std::string_view host) noexcept;

} // namespace linecode::domain
