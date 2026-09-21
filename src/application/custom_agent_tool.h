#pragma once

#include <string>
#include <string_view>

#include "domain/extension_config.h"

namespace linecode::application {

// Everything a custom Agent extension contributes as a callable tool.
//
// Port of `cn.lineai.tool.builtin.CustomAgentExtensionTool` together with the
// two naming helpers it depends on (`ToolRegistry.customAgentToolName` and
// `ToolRegistry.safeToolNamePart`). The pieces are pure so the wire contract
// can be pinned without building a registry.
inline constexpr std::string_view custom_agent_tool_prefix = "agentx_";

// Port of `ToolRegistry.safeToolNamePart` (`ToolRegistry.java:247-267`):
// characters outside `[A-Za-z0-9_-]` become `_`, runs of underscores collapse,
// leading and trailing underscores are trimmed, an empty result falls back, and
// a result that does not start with a letter is prefixed with the fallback.
[[nodiscard]] std::string SafeCustomToolNamePart(std::string_view value,
                                                 std::string_view fallback,
                                                 std::size_t max_length);

// Port of `ToolRegistry.customAgentToolName` (`ToolRegistry.java:198-200`).
[[nodiscard]] std::string CustomAgentToolName(std::string_view slug);

// Port of `CustomAgentExtensionTool.getDescription`
// (`CustomAgentExtensionTool.java:28-38`). The capability excerpt is capped at
// 900 characters exactly like the legacy `substring(0, 900)`.
[[nodiscard]] std::string CustomAgentToolDescription(
    const domain::AgentExtension &agent);

// Port of `CustomAgentExtensionTool.buildPrompt`
// (`CustomAgentExtensionTool.java:103-124`).
[[nodiscard]] std::string BuildCustomAgentPrompt(
    const domain::AgentExtension &agent, std::string_view task,
    std::string_view extra_context);

// The tool's JSON schema, ported from `getParameters`
// (`CustomAgentExtensionTool.java:53-66`): `task` is required; `context`,
// `read_scope` and `write_scope` are optional.
[[nodiscard]] std::string CustomAgentToolSchemaJson();

} // namespace linecode::application
