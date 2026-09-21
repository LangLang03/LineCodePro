#include "domain/ssh_config.h"

#include <algorithm>
#include <cctype>
#include <charconv>
#include <system_error>

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
