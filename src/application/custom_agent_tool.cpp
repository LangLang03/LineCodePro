#include "application/custom_agent_tool.h"

#include <algorithm>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::application {
namespace {

// `ToolRegistry.trimUnderscore` (`ToolRegistry.java:269-...`).
[[nodiscard]] std::string TrimUnderscores(std::string value) {
  while (value.find("__") != std::string::npos) {
    const auto at = value.find("__");
    value.replace(at, 2, "_");
  }
  const auto first = value.find_first_not_of('_');
  if (first == std::string::npos)
    return {};
  const auto last = value.find_last_not_of('_');
  return value.substr(first, last - first + 1);
}

[[nodiscard]] bool IsAsciiLetter(const char value) noexcept {
  return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
}

// Java `List.toString()`: "[a, b, c]". The legacy spliced the collection
// itself into the prompt, so the bracketed form is what the model used to see.
[[nodiscard]] std::string Joined(const std::vector<std::string> &values) {
  std::string joined = "[";
  for (std::size_t index = 0; index < values.size(); ++index) {
    if (index != 0U)
      joined += ", ";
    joined += values[index];
  }
  joined += "]";
  return joined;
}

// Java `String.trim()` on the supplementary context.
[[nodiscard]] std::string TrimCopy(const std::string_view value) {
  const auto first = value.find_first_not_of(" \t\n\r\f\v");
  if (first == std::string_view::npos)
    return {};
  const auto last = value.find_last_not_of(" \t\n\r\f\v");
  return std::string{value.substr(first, last - first + 1)};
}

} // namespace

std::string SafeCustomToolNamePart(const std::string_view value,
                                   const std::string_view fallback,
                                   const std::size_t max_length) {
  std::string built;
  for (std::size_t index = 0; index < value.size() && built.size() < max_length;
       ++index) {
    const char character = value[index];
    const bool allowed = IsAsciiLetter(character) ||
                         (character >= '0' && character <= '9') ||
                         character == '_' || character == '-';
    built.push_back(allowed ? character : '_');
  }
  auto clean = TrimUnderscores(std::move(built));
  if (clean.empty())
    clean = std::string{fallback};
  // A tool name has to start with a letter for the providers to accept it.
  if (!clean.empty() && !IsAsciiLetter(clean.front()))
    clean = std::string{fallback} + "_" + clean;
  return clean;
}

std::string CustomAgentToolName(const std::string_view slug) {
  return std::string{custom_agent_tool_prefix} +
         SafeCustomToolNamePart(slug, "agent", 55);
}

std::string CustomAgentToolDescription(const domain::AgentExtension &agent) {
  std::string description = "Invoke the custom Agent \"" + agent.name + "\".";
  if (!agent.trigger.empty())
    description += "\nTrigger: " + agent.trigger;
  // `substring(0, 900)` on the raw prompt: the legacy capped the excerpt but
  // did not trim it first.
  const auto excerpt = agent.prompt.size() > 900U
                           ? agent.prompt.substr(0, 900)
                           : agent.prompt;
  description += "\nCapabilities: " + excerpt;
  return description;
}

std::string BuildCustomAgentPrompt(const domain::AgentExtension &agent,
                                   const std::string_view task,
                                   const std::string_view extra_context) {
  std::string prompt =
      "You are the custom Agent \"" + agent.name + "\" (" + agent.slug +
      ").\n\n";
  prompt += "## Agent Definition\n" + agent.prompt + "\n\n";
  if (!agent.trigger.empty())
    prompt += "## Trigger\n" + agent.trigger + "\n\n";
  if (!agent.tool_names.empty()) {
    // The legacy appended the collection's `toString()`, i.e. its bracketed
    // form; the same shape keeps the prompt comparable.
    prompt += "## Expected Tool Scope\n" + Joined(agent.tool_names) + "\n\n";
  }
  if (!agent.mcp_ids.empty())
    prompt += "## Expected MCP Scope\n" + Joined(agent.mcp_ids) + "\n\n";
  prompt += "## Current Task\n" + std::string{task};
  const auto trimmed_context = TrimCopy(extra_context);
  if (!trimmed_context.empty())
    prompt += "\n\n## Supplementary Context\n" + trimmed_context;
  return prompt;
}

std::string CustomAgentToolSchemaJson() {
  // One raw literal, not several adjacent ones: mixing the `json` delimiter
  // with a bare `)"` terminator silently swallows the rest of the function
  // into a single string.
  return R"json({"type":"object","properties":{"task":{"type":"string","description":"Task assigned to the custom Agent"},"context":{"type":"string","description":"Optional supplementary context for the task"},"read_scope":{"type":"array","items":{"type":"string"},"description":"List of files or directories allowed to read"},"write_scope":{"type":"array","items":{"type":"string"},"description":"List of unique files or directories allowed to write. Without a write scope, the custom Agent cannot write files"}},"required":["task"]})json";
}

} // namespace linecode::application
