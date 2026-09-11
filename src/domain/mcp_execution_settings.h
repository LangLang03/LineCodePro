#pragma once

#include <algorithm>
#include <array>
#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace linecode::domain {

enum class McpExecutionMode : std::uint8_t {
  local,
  ssh,
  terminal_provider,
};

enum class McpExecutionModeMask : std::uint8_t {
  none = 0,
  local = 1U << 0U,
  ssh = 1U << 1U,
  terminal_provider = 1U << 2U,
  remote = (1U << 1U) | (1U << 2U),
  all = (1U << 0U) | (1U << 1U) | (1U << 2U),
};

struct McpExecutionModeDefinition final {
  McpExecutionMode value;
  std::string_view storage_name;
  McpExecutionModeMask flag;
  bool requires_terminal_provider;
};

inline constexpr std::array kMcpExecutionModes{
    McpExecutionModeDefinition{McpExecutionMode::local, "local",
                               McpExecutionModeMask::local, false},
    McpExecutionModeDefinition{McpExecutionMode::ssh, "ssh",
                               McpExecutionModeMask::ssh, false},
    McpExecutionModeDefinition{McpExecutionMode::terminal_provider,
                               "terminal_provider",
                               McpExecutionModeMask::terminal_provider, true},
};

struct McpExecutionCapabilities final {
  bool terminal_provider{};

  bool operator==(const McpExecutionCapabilities&) const = default;
};

[[nodiscard]] constexpr const McpExecutionModeDefinition&
McpExecutionModeDefinitionFor(McpExecutionMode mode) noexcept {
  const auto found = std::ranges::find(kMcpExecutionModes, mode,
                                       &McpExecutionModeDefinition::value);
  return found != kMcpExecutionModes.end() ? *found
                                           : kMcpExecutionModes.front();
}

[[nodiscard]] constexpr std::string_view
SerializeMcpExecutionMode(McpExecutionMode mode) noexcept {
  return McpExecutionModeDefinitionFor(mode).storage_name;
}

[[nodiscard]] constexpr McpExecutionMode
ParseMcpExecutionMode(std::string_view value) noexcept {
  const auto found =
      std::ranges::find(kMcpExecutionModes, value,
                        &McpExecutionModeDefinition::storage_name);
  return found != kMcpExecutionModes.end() ? found->value
                                           : McpExecutionMode::local;
}

[[nodiscard]] constexpr bool IsMcpExecutionModeAvailable(
    McpExecutionMode mode, McpExecutionCapabilities capabilities) noexcept {
  const auto& definition = McpExecutionModeDefinitionFor(mode);
  return !definition.requires_terminal_provider ||
         capabilities.terminal_provider;
}

[[nodiscard]] constexpr McpExecutionMode NormalizeMcpExecutionMode(
    McpExecutionMode mode, McpExecutionCapabilities capabilities) noexcept {
  return IsMcpExecutionModeAvailable(mode, capabilities)
             ? mode
             : McpExecutionMode::local;
}

[[nodiscard]] constexpr McpExecutionModeMask
operator|(McpExecutionModeMask left, McpExecutionModeMask right) noexcept {
  return static_cast<McpExecutionModeMask>(
      std::to_underlying(left) | std::to_underlying(right));
}

[[nodiscard]] constexpr bool
SupportsMcpExecutionMode(McpExecutionModeMask modes,
                         McpExecutionMode mode) noexcept {
  const auto flag = McpExecutionModeDefinitionFor(mode).flag;
  return (std::to_underlying(modes) & std::to_underlying(flag)) != 0;
}

struct McpToolGroupDefinition final {
  std::string_view id;
  bool default_enabled;
  McpExecutionModeMask supported_modes;
};

// Phone-control groups are intentionally absent: the C++ product does not
// expose Accessibility-backed control on any platform.
inline constexpr std::array kMcpToolGroups{
    McpToolGroupDefinition{"file_ops", true, McpExecutionModeMask::local},
    McpToolGroupDefinition{"agent", true, McpExecutionModeMask::all},
    McpToolGroupDefinition{"todo", true, McpExecutionModeMask::all},
    McpToolGroupDefinition{"image_understanding", false,
                           McpExecutionModeMask::all},
    McpToolGroupDefinition{"image_generation", false,
                           McpExecutionModeMask::all},
    McpToolGroupDefinition{"shell", true, McpExecutionModeMask::remote},
    McpToolGroupDefinition{"web_search", true, McpExecutionModeMask::all},
    McpToolGroupDefinition{"memory", true, McpExecutionModeMask::all},
};

struct McpToolGroupState final {
  std::string id;
  bool enabled{};
  McpExecutionModeMask supported_modes{McpExecutionModeMask::none};

  bool operator==(const McpToolGroupState&) const = default;
};

struct McpExecutionSettings final {
  McpExecutionMode mode{McpExecutionMode::local};
  std::vector<McpToolGroupState> groups;

  bool operator==(const McpExecutionSettings&) const = default;
};

[[nodiscard]] inline std::string
McpEnabledSettingKey(McpExecutionMode mode, std::string_view id) {
  std::string key{"@linecode_mcp_enabled_"};
  if (mode != McpExecutionMode::local) {
    key += SerializeMcpExecutionMode(mode);
    key += '_';
  }
  key += id;
  return key;
}

[[nodiscard]] inline McpExecutionSettings
DefaultMcpExecutionSettings(McpExecutionMode mode = McpExecutionMode::local) {
  McpExecutionSettings settings{.mode = mode, .groups = {}};
  settings.groups.reserve(kMcpToolGroups.size());
  for (const auto& definition : kMcpToolGroups) {
    settings.groups.push_back(McpToolGroupState{
        .id = std::string{definition.id},
        .enabled = definition.default_enabled,
        .supported_modes = definition.supported_modes,
    });
  }
  return settings;
}

} // namespace linecode::domain
