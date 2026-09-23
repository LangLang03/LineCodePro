#pragma once

#include <algorithm>
#include <memory>
#include <ranges>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "application/agent_result_registry.h"
#include "application/mcp_execution_settings.h"
#include "application/ports/agent_runner.h"
#include "application/ports/tool_registry.h"
#include "application/tool_text_catalog.h"
#include "domain/agent_pipeline.h"

namespace linecode::application {

// cn.lineai.tool.ToolNames.AGENT / AGENT_PIPELINE / AGENT_OUTPUT.
inline constexpr std::string_view kAgentToolName = "agent";
inline constexpr std::string_view kAgentPipelineToolName = "agent_pipeline";
inline constexpr std::string_view kAgentOutputToolName = "agent_output";

// The tool group id in domain::kMcpToolGroups; `supported_modes` is `all`, so
// the group is offered in every execution mode.
inline constexpr std::string_view kAgentToolGroupId = "agent";

// AgentTool.TYPE_EXPLORE / TYPE_SUB_CODING (lines 14-15).
inline constexpr std::string_view kAgentTypeExplore = "explore";
inline constexpr std::string_view kAgentTypeSubCoding = "sub-coding";

// Legacy `AgentTool.normalizeType` (lines 117-123): Java `trim()`, then
// `toLowerCase(Locale.US)` (ASCII-only, so non-ASCII bytes are preserved), then
// the three coding aliases map onto "sub-coding". Every other value is returned
// unchanged so an unknown type stays invalid instead of silently defaulting.
//
// Defined inline because the sub-agent execution engine also needs this port;
// one definition in the contract header lets it drop its private copy instead
// of leaving two implementations of the same legacy rule in the binary.
[[nodiscard]] inline std::string NormalizeAgentType(std::string_view value) {
  const auto visible = [](char character) {
    return static_cast<unsigned char>(character) > 0x20U;
  };
  const auto begin = std::ranges::find_if(value, visible);
  const auto end =
      std::ranges::find_if(value | std::views::reverse, visible).base();
  std::string type{begin < end ? std::string_view{begin, end}
                               : std::string_view{}};
  for (auto &character : type) {
    if (character >= 'A' && character <= 'Z')
      character = static_cast<char>(character - 'A' + 'a');
  }
  if (type == "sub_coding" || type == "subcoding" || type == "coding")
    return std::string{kAgentTypeSubCoding};
  return type;
}

// Legacy `AgentPipelineTool.normalizeScope` (lines 169-181): trim, backslashes
// to forward slashes, strip every leading "./", collapse "//", drop trailing
// slashes but keep a bare "/".
[[nodiscard]] std::string NormalizeAgentScope(std::string_view value);

// Legacy `AgentPipelineTool.scopesOverlap` (lines 183-193): "." and "/" overlap
// everything non-empty, equal scopes overlap, and a scope that contains the
// other as a path prefix overlaps it.
[[nodiscard]] bool AgentScopesOverlap(std::string_view left,
                                      std::string_view right) noexcept;

// Legacy `PipelineDependencyResolver.parsePipelineAgents` (lines 12-39).
//
// `agents_json` is the raw JSON text of the `agents` array of an
// `agent_pipeline` call. A malformed array (a non-object element, an empty id
// or a repeated id) fails the whole parse, which the legacy resolver signalled
// by returning an empty list; callers that need to distinguish "malformed"
// from "empty" run the tool-level validation first.
[[nodiscard]] std::vector<domain::PipelineAgent>
ParsePipelineAgents(std::string_view agents_json);

// Runtime tool source for the `agent` group: agent / agent_pipeline /
// agent_output.
//
// The registry owns the declarations, the argument validation and the result
// lookup. The model loop itself lives behind `AgentRunner` (see
// ports/agent_runner.h), which keeps this class free of execution policy.
class AgentToolRegistry final : public ToolRegistry {
public:
  // `settings` is required so the group can be gated. `results` and `runner`
  // may be null: the legacy tools reported a missing result store and a missing
  // runner as normal tool errors, which keeps those branches reachable while
  // the execution engine is not wired yet.
  AgentToolRegistry(std::shared_ptr<McpExecutionSettingsService> settings,
                    std::shared_ptr<AgentResultRegistry> results,
                    std::shared_ptr<AgentRunner> runner = nullptr,
                    ToolTextLanguage language = ToolTextLanguage::english);

  // Attaches or replaces the execution engine after construction, so the
  // composition root can build the runner once its own dependencies exist.
  void SetRunner(std::shared_ptr<AgentRunner> runner);

  // Attaches or replaces the shared result store.
  void SetResultRegistry(std::shared_ptr<AgentResultRegistry> results);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  InvokeWithContext(std::string name, std::string arguments_json,
                    ToolInvocationContext context) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<AgentResultRegistry> results_;
  std::shared_ptr<AgentRunner> runner_;
  ToolTextLanguage language_{ToolTextLanguage::english};
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
