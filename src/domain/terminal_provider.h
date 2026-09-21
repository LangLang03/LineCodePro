#pragma once

#include <cstdint>
#include <string>

namespace linecode::domain {

inline constexpr auto kTerminalProviderType = "terminal";

struct ScannedTerminalProvider final {
  std::string package_name;
  std::string service_class;
  std::string label;

  bool operator==(const ScannedTerminalProvider &) const = default;
};

struct TerminalProviderConfig final {
  std::string id;
  bool enabled{true};
  std::string provider_type{kTerminalProviderType};
  std::string name;
  std::string package_name;
  std::string service_class;
  std::int64_t created_at{};
  std::int64_t updated_at{};

  bool operator==(const TerminalProviderConfig &) const = default;
};

} // namespace linecode::domain
