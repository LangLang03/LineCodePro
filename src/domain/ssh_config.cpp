#include "domain/ssh_config.h"

#include <algorithm>
#include <cctype>
#include <charconv>
#include <system_error>
#include <vector>

namespace linecode::domain {
namespace {

void Trim(std::string &value) {
  const auto whitespace = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && whitespace(value.front()))
    value.erase(value.begin());
  while (!value.empty() && whitespace(value.back()))
    value.pop_back();
}

} // namespace

bool SshConfig::IsConfigured() const noexcept {
  return !host.empty() && port > 0 && !username.empty() &&
         (!password.empty() || !private_key.empty());
}

SshConfigGap SshConfig::Gap() const noexcept {
  if (host.empty())
    return SshConfigGap::host;
  if (port <= 0)
    return SshConfigGap::port;
  if (username.empty())
    return SshConfigGap::username;
  if (password.empty() && private_key.empty())
    return SshConfigGap::credentials;
  return SshConfigGap::none;
}

std::string_view DescribeSshConfigGap(const SshConfigGap gap) noexcept {
  switch (gap) {
  case SshConfigGap::host:
    return "host";
  case SshConfigGap::port:
    return "port";
  case SshConfigGap::username:
    return "username";
  case SshConfigGap::credentials:
    return "credentials";
  case SshConfigGap::none:
    break;
  }
  return "none";
}

SshConfig NormalizeSshConfig(SshConfig config) {
  Trim(config.host);
  if (config.host.empty())
    config.host = kDefaultSshHost;
  if (config.port <= 0)
    config.port = kDefaultSshPort;
  Trim(config.username);
  return config;
}

std::int32_t ParseSshPort(std::string_view value) noexcept {
  while (!value.empty() && static_cast<unsigned char>(value.front()) <= 0x20U)
    value.remove_prefix(1);
  while (!value.empty() && static_cast<unsigned char>(value.back()) <= 0x20U)
    value.remove_suffix(1);
  if (value.empty())
    return kDefaultSshPort;

  std::int32_t port{};
  const auto [end, error] =
      std::from_chars(value.data(), value.data() + value.size(), port);
  return error == std::errc{} && end == value.data() + value.size() && port > 0
             ? port
             : kDefaultSshPort;
}

SshAuthMethods ParseSshAuthMethods(std::string_view advertised) noexcept {
  SshAuthMethods methods;
  while (!advertised.empty()) {
    const auto separator = advertised.find(',');
    auto name = advertised.substr(
        0, separator == std::string_view::npos ? advertised.size() : separator);
    while (!name.empty() && static_cast<unsigned char>(name.front()) <= 0x20U)
      name.remove_prefix(1);
    while (!name.empty() && static_cast<unsigned char>(name.back()) <= 0x20U)
      name.remove_suffix(1);
    if (name == "publickey")
      methods.public_key = true;
    else if (name == "password")
      methods.password = true;
    else if (name == "keyboard-interactive")
      methods.keyboard_interactive = true;
    if (separator == std::string_view::npos)
      break;
    advertised.remove_prefix(separator + 1U);
  }
  return methods;
}

std::vector<SshAuthMethod>
PlanSshAuthentication(const SshConfig &config, SshAuthMethods advertised) {
  std::vector<SshAuthMethod> plan;
  const bool has_key = !config.private_key.empty();
  const bool has_password = !config.password.empty();
  const bool advertised_anything = advertised.Any();
  // Without an advertisement every configured credential is worth trying: this
  // is the path taken when the server denies the list query itself.
  const auto allows = [&](SshAuthMethod method, bool advertised_flag,
                          bool available) {
    if (!available)
      return false;
    return !advertised_anything || advertised_flag;
  };
  if (allows(SshAuthMethod::public_key, advertised.public_key, has_key))
    plan.push_back(SshAuthMethod::public_key);
  if (allows(SshAuthMethod::password, advertised.password, has_password))
    plan.push_back(SshAuthMethod::password);
  // `password` and `keyboard-interactive` carry the same secret. Falling back
  // to the interactive exchange is what makes password login work against the
  // PAM-backed default configuration on most servers and on Termux sshd.
  if (allows(SshAuthMethod::keyboard_interactive,
             advertised.keyboard_interactive, has_password))
    plan.push_back(SshAuthMethod::keyboard_interactive);
  return plan;
}

std::string DescribeSshAuthMethods(SshAuthMethods methods) {
  std::string result;
  const auto append = [&result](std::string_view name) {
    if (!result.empty())
      result += ", ";
    result += name;
  };
  if (methods.public_key)
    append("publickey");
  if (methods.password)
    append("password");
  if (methods.keyboard_interactive)
    append("keyboard-interactive");
  return result.empty() ? std::string{"none"} : result;
}

std::chrono::milliseconds
ConnectAttemptBudget(std::chrono::milliseconds remaining,
                     std::size_t addresses_remaining) noexcept {
  if (addresses_remaining <= 1U)
    return remaining;
  const auto share = remaining / static_cast<std::int64_t>(addresses_remaining);
  const auto floor = std::chrono::milliseconds{5'000};
  if (share >= floor)
    return share;
  return std::min(remaining, floor);
}

bool IsTermuxSshHost(std::string_view host) noexcept {
  while (!host.empty() && static_cast<unsigned char>(host.front()) <= 0x20U)
    host.remove_prefix(1);
  while (!host.empty() && static_cast<unsigned char>(host.back()) <= 0x20U)
    host.remove_suffix(1);
  if (host == kDefaultSshHost)
    return true;
  constexpr std::string_view localhost = "localhost";
  return host.size() == localhost.size() &&
         std::ranges::equal(host, localhost, [](char lhs, char rhs) {
           return std::tolower(static_cast<unsigned char>(lhs)) ==
                  std::tolower(static_cast<unsigned char>(rhs));
         });
}

} // namespace linecode::domain
