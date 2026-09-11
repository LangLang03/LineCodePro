#pragma once

#include <cstdint>
#include <expected>
#include <span>
#include <string>

#include <huxerui/task.h>

namespace linecode::application {

struct RegisteredTool final {
  std::string name;
  std::string description;
  std::string parameters_json;
  // Read-only mode can expose only tools whose implementation explicitly
  // opts in. Unknown/extension tools therefore remain denied by default.
  bool allowed_in_read_only{};
  // Exact permanent grants are intentionally limited to implementations that
  // can derive a stable, narrow action key (currently shell commands).
  bool permanent_grant_supported{};
  // Editor metadata is supplied by each registry. The Agent editor projects
  // this catalog directly instead of switching on tool names or families.
  std::string category{"tool"};
  bool agent_selectable{true};
  bool agent_selected_by_default{};

  bool operator==(const RegisteredTool &) const = default;
};

enum class ToolRegistryErrorCode : std::uint8_t {
  load_failed,
  duplicate_tool,
  unknown_tool,
  invalid_arguments,
  unavailable,
  invocation_failed,
};

struct ToolRegistryError final {
  ToolRegistryErrorCode code{ToolRegistryErrorCode::load_failed};
  std::string message;

  bool operator==(const ToolRegistryError &) const = default;
};

struct ToolInvocationResult final {
  std::string content;
  bool error{};

  bool operator==(const ToolInvocationResult &) const = default;
};

// A runtime tool source. Implementations own discovery, enablement policy and
// invocation; the completion loop only sees this DIP boundary.
class ToolRegistry {
public:
  virtual ~ToolRegistry() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<void, ToolRegistryError>>
  Refresh() = 0;
  [[nodiscard]] virtual std::span<const RegisteredTool>
  Tools() const noexcept = 0;
  [[nodiscard]] virtual huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) = 0;
};

} // namespace linecode::application
