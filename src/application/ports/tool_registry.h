#pragma once

#include <cstdint>
#include <expected>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/tool_text_catalog.h"

namespace linecode::application {

struct ToolPresentation final {
  std::string english_name;
  std::string english_description;
  std::string chinese_name;
  std::string chinese_description;

  [[nodiscard]] std::string_view
  DisplayName(ToolTextLanguage language,
              std::string_view fallback) const noexcept {
    if (language == ToolTextLanguage::chinese && !chinese_name.empty())
      return chinese_name;
    if (!english_name.empty())
      return english_name;
    return fallback;
  }

  [[nodiscard]] std::string_view
  DisplayDescription(ToolTextLanguage language,
                     std::string_view fallback) const noexcept {
    if (language == ToolTextLanguage::chinese && !chinese_description.empty())
      return chinese_description;
    if (!english_description.empty())
      return english_description;
    return fallback;
  }

  bool operator==(const ToolPresentation &) const = default;
};

// Declares one JSON string argument that narrows a permanent permission grant.
// Registries own this policy so the permission service never needs to recognize
// a concrete tool name. Optional fields still participate with an empty value,
// keeping grants stable when callers omit them.
struct PermanentGrantArgument final {
  std::string name;
  bool required{};
  bool trim_whitespace{true};

  bool operator==(const PermanentGrantArgument &) const = default;
};

// Capability seen by delegated Agents. This is declared by the registry that
// owns the tool, rather than inferred centrally from group ids or tool names.
// The default is deliberately restrictive for third-party/extension tools.
enum class AgentToolCategory : std::uint8_t {
  read,
  generate,
  write,
  system,
};

struct RegisteredTool final {
  std::string name;
  std::string description;
  std::string parameters_json;
  // Read-only mode can expose only tools whose implementation explicitly
  // opts in. Unknown/extension tools therefore remain denied by default.
  bool allowed_in_read_only{};
  // Exact permanent grants are intentionally limited to implementations that
  // can derive a stable, narrow action key.
  std::vector<PermanentGrantArgument> permanent_grant_arguments{};
  AgentToolCategory agent_category{AgentToolCategory::system};
  // Editor metadata is supplied by each registry. The Agent editor projects
  // this catalog directly instead of switching on tool names or families.
  std::string category{"tool"};
  bool agent_selectable{true};
  bool agent_selected_by_default{};
  // Stable editor/runtime scope identifiers contributed by the registry.
  // Custom MCP tools, for example, all declare the same `custom:<extension>`
  // scope selected by the Agent editor; consumers never reverse a generated
  // protocol tool name to discover ownership.
  std::vector<std::string> agent_scope_ids{};
  // Model-facing protocol text stays in name/description. Editor-facing text
  // is owned by the registry that contributes the tool, keeping presentation
  // extensible without a central name switch.
  ToolPresentation presentation{};

  [[nodiscard]] bool SupportsPermanentGrant() const noexcept {
    return !permanent_grant_arguments.empty();
  }

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
  // Set by tools that record a revertable file change (legacy
  // `ToolResult.withDiffId`). Empty for everything else. The `{}` keeps
  // `-Wmissing-field-initializers` quiet at designated-initializer call sites.
  std::string diff_id{};

  bool operator==(const ToolInvocationResult &) const = default;
};

// A runtime tool source. Implementations own discovery, enablement policy and
// invocation; the completion loop only sees this DIP boundary.
class ToolRegistry {
public:
  virtual ~ToolRegistry() = default;

  [[nodiscard]] virtual huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() = 0;
  [[nodiscard]] virtual std::span<const RegisteredTool>
  Tools() const noexcept = 0;
  [[nodiscard]] virtual huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) = 0;
};

} // namespace linecode::application
