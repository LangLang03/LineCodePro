#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::domain {

// One task in an agent pipeline, mirroring the legacy `PipelineAgent`.
struct PipelineAgent final {
  std::string id;
  // Normalized legacy type: "explore" or "sub-coding".
  std::string type;
  std::string description;
  std::string prompt;
  std::vector<std::string> read_scope;
  std::vector<std::string> write_scope;
  // Ids of agents that must finish before this one starts.
  std::vector<std::string> dependencies;

  bool operator==(const PipelineAgent &) const = default;
};

enum class PipelinePlanErrorCode : std::uint8_t {
  empty,
  self_dependency,
  unknown_dependency,
  cycle,
};

struct PipelinePlanError final {
  PipelinePlanErrorCode code{PipelinePlanErrorCode::empty};
  // Id of the agent the error is about, when the error names one.
  std::string agent_id;

  bool operator==(const PipelinePlanError &) const = default;
};

// Groups agents into execution levels: every agent in a level may run in
// parallel, and each level only starts once the previous one finished.
//
// Port of the legacy `PipelineDependencyResolver` level computation. The
// legacy resolver returned the levels it could build and left the caller to
// notice missing dependencies, so this port makes the failure explicit instead
// of silently dropping agents.
struct PipelinePlan final {
  std::vector<std::vector<PipelineAgent>> levels;
  // Set only when planning failed; a successful plan leaves it empty and
  // `levels` non-empty.
  PipelinePlanError error;

  [[nodiscard]] bool ok() const noexcept {
    return levels.size() > 0 && error.agent_id.empty() &&
           error.code == PipelinePlanErrorCode::empty;
  }
};

// Builds the execution plan. Agents keep their input order inside a level.
[[nodiscard]] PipelinePlan PlanPipeline(
    const std::vector<PipelineAgent> &agents);

} // namespace linecode::domain
