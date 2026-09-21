#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace linecode::domain {

enum class ExtensionKind : std::uint8_t {
  agent,
  mcp,
  skills,
  linecode,
  terminal_provider,
};

struct ExtensionDetailRoute final {
  ExtensionKind kind{ExtensionKind::linecode};

  bool operator==(const ExtensionDetailRoute &) const = default;
};

struct AgentExtensionEditorRoute final {
  std::optional<std::string> id;

  bool operator==(const AgentExtensionEditorRoute &) const = default;
};

struct McpExtensionEditorRoute final {
  std::optional<std::string> id;

  bool operator==(const McpExtensionEditorRoute &) const = default;
};

} // namespace linecode::domain
