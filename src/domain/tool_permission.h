#pragma once

#include <cstdint>
#include <string_view>

namespace linecode::domain {

enum class ToolPermissionMode : std::uint8_t {
  automatic,
  confirm,
  read_only,
};

[[nodiscard]] constexpr ToolPermissionMode
ParseToolPermissionMode(std::string_view value) noexcept {
  if (value == "confirm" || value == "ask")
    return ToolPermissionMode::confirm;
  if (value == "readonly" || value == "manual")
    return ToolPermissionMode::read_only;
  return ToolPermissionMode::automatic;
}

[[nodiscard]] constexpr std::string_view
SerializeToolPermissionMode(ToolPermissionMode value) noexcept {
  switch (value) {
  case ToolPermissionMode::automatic: return "auto";
  case ToolPermissionMode::confirm: return "confirm";
  case ToolPermissionMode::read_only: return "readonly";
  }
  return "auto";
}

} // namespace linecode::domain
