#pragma once

#include <memory>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/agent_pipeline.h"
#include "domain/agent_run_progress.h"

namespace linecode::application {

// Outcome of one dispatched sub-agent, mirroring the legacy `AgentRunResult`.
struct AgentRunResult final {
  std::string output;
  int tool_call_count{};
  bool error{};
  domain::AgentProgressSnapshot progress{};
};

// Everything a sub-agent run needs that the tool registry does not own: the
// model to use, the tool set it may call, and the project scope it may touch.
//
// The legacy `AgentTool`/`AgentPipelineTool` validated the arguments and then
// handed a normalized request to `AgentExecutionController`, which owned the
// completion loop. This port keeps that boundary: the registry owns contracts
// and validation, the runner owns execution.
struct AgentRunRequest final {
  // Normalized legacy type: "explore" or "sub-coding".
  std::string type;
  std::string agent_id;
  std::string description;
  std::string prompt;
  std::vector<std::string> read_scope;
  std::vector<std::string> write_scope;
  // explore-only: return the compact ref immediately and keep running.
  bool async{};
  // Tool and MCP names a custom Agent extension selected for itself
  // (`CustomAgentExtensionTool.java:92-95` delegated both). Empty means the
  // agent inherited the ordinary type-based tool set.
  std::vector<std::string> custom_tool_names{};
  std::vector<std::string> custom_mcp_ids{};
  // Tool call that requested this run, used to tie progress to the card.
  // The current ToolRegistry::Invoke boundary carries no invocation context,
  // so registry-dispatched runs leave this empty. Do not synthesize it; a
  // future invocation-context port must provide the real parent id.
  std::string tool_call_id;
};

struct AgentPipelineRunRequest final {
  std::vector<domain::PipelineAgent> agents;
  std::string tool_call_id;
};

// Dispatches sub-agents. Implementations own the model loop; the tool registry
// only depends on this boundary (DIP).
class AgentRunner {
public:
  virtual ~AgentRunner() = default;

  // Runs one sub-agent to completion (or returns immediately when
  // `request.async` is set) and returns the model-visible tool content.
  [[nodiscard]] virtual huxerui::Task<AgentRunResult>
  RunAgent(AgentRunRequest request) = 0;

  // Runs every pipeline agent in dependency order, one level at a time.
  [[nodiscard]] virtual huxerui::Task<AgentRunResult>
  RunAgentPipeline(AgentPipelineRunRequest request) = 0;
};

} // namespace linecode::application
