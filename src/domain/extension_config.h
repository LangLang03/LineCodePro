#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::domain {

struct AgentExtension final {
  std::string id;
  bool enabled{true};
  std::string name;
  std::string slug;
  std::string prompt;
  std::string trigger;
  std::vector<std::string> tool_names;
  std::vector<std::string> mcp_ids;
  std::int64_t created_at{};
  std::int64_t updated_at{};

  bool operator==(const AgentExtension &) const = default;
};

struct McpRequestHeader final {
  std::string name;
  std::string value;

  bool operator==(const McpRequestHeader &) const = default;
};

struct McpToolSummary final {
  std::string name;
  bool enabled{true};
  std::string description;
  std::string input_schema_json;

  bool operator==(const McpToolSummary &) const = default;
};

struct McpExtension final {
  std::string id;
  bool enabled{true};
  std::string name;
  std::string url;
  std::vector<McpRequestHeader> request_headers;
  std::vector<McpToolSummary> tools;
  std::int64_t created_at{};
  std::int64_t updated_at{};

  bool operator==(const McpExtension &) const = default;
};

[[nodiscard]] std::string NormalizeAgentSlug(std::string_view raw,
                                             std::string_view fallback);
[[nodiscard]] std::string NormalizeAgentEditorSlug(std::string_view value);
[[nodiscard]] AgentExtension NormalizeAgentExtension(AgentExtension value);
[[nodiscard]] McpExtension NormalizeMcpExtension(McpExtension value);
[[nodiscard]] bool IsHttpMcpUrl(std::string_view value);
[[nodiscard]] std::string McpExtensionToolName(std::string_view extension_id,
                                               std::string_view tool_name);

} // namespace linecode::domain
