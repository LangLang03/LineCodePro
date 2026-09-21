#include "infrastructure/ssh_config_codec.h"

#include <charconv>
#include <cmath>
#include <concepts>
#include <cstdint>
#include <limits>
#include <string>
#include <type_traits>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

using archive_json::Object;
using archive_json::Value;

std::string StringOr(const Object& object, std::string_view key,
                     std::string_view fallback) {
  const auto* value = archive_json::Find(object, key);
  if (const auto* text = archive_json::AsString(value))
    return *text;
  if (!value)
    return std::string{fallback};
  return std::visit(
      [value, fallback](const auto& typed) -> std::string {
        using Type = std::remove_cvref_t<decltype(typed)>;
        if constexpr (std::same_as<Type, bool> ||
                      std::same_as<Type, std::int64_t> ||
                      std::same_as<Type, double>) {
          return archive_json::Serialize(*value);
        }
        return std::string{fallback};
      },
      *value);
}

std::int32_t PortOr(const Object& object, std::string_view key,
                    std::int32_t fallback) {
  const auto* value = archive_json::Find(object, key);
  if (!value)
    return fallback;
  return std::visit(
      [fallback](const auto& typed) -> std::int32_t {
        using Type = std::remove_cvref_t<decltype(typed)>;
        if constexpr (std::same_as<Type, std::int64_t>) {
          return typed > 0 &&
                         typed <= std::numeric_limits<std::int32_t>::max()
                     ? static_cast<std::int32_t>(typed)
                     : fallback;
        } else if constexpr (std::same_as<Type, double>) {
          if (!std::isfinite(typed) || typed <= 0.0 ||
              typed > static_cast<double>(
                          std::numeric_limits<std::int32_t>::max()))
            return fallback;
          const auto integral = static_cast<std::int64_t>(typed);
          return typed == static_cast<double>(integral)
                     ? static_cast<std::int32_t>(integral)
                     : fallback;
        } else if constexpr (std::same_as<Type, std::string>) {
          return domain::ParseSshPort(typed);
        } else {
          return fallback;
        }
      },
      *value);
}

} // namespace

std::string EncodeSshConfig(const domain::SshConfig& config) {
  const auto normalized = domain::NormalizeSshConfig(config);
  return archive_json::Serialize(Object{
      {"host", normalized.host},
      {"port", static_cast<std::int64_t>(normalized.port)},
      {"username", normalized.username},
      {"password", normalized.password},
      {"privateKey", normalized.private_key},
      {"passphrase", normalized.passphrase},
  });
}

std::expected<domain::SshConfig, SshConfigCodecError>
DecodeSshConfig(std::string_view json) {
  auto parsed = archive_json::Parse(json);
  if (!parsed) {
    return std::unexpected(
        SshConfigCodecError{.message = std::move(parsed.error().message)});
  }
  const auto* object = archive_json::AsObject(&*parsed);
  if (!object) {
    return std::unexpected(
        SshConfigCodecError{.message = "SSH config is not an object"});
  }
  return domain::NormalizeSshConfig(domain::SshConfig{
      .host = StringOr(*object, "host", domain::kDefaultSshHost),
      .port = PortOr(*object, "port", domain::kDefaultSshPort),
      .username = StringOr(*object, "username", ""),
      .password = StringOr(*object, "password", ""),
      .private_key = StringOr(*object, "privateKey", ""),
      .passphrase = StringOr(*object, "passphrase", ""),
  });
}

} // namespace linecode::infrastructure
