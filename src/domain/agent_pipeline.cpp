#include "domain/agent_pipeline.h"

#include <algorithm>
#include <ranges>
#include <string>
#include <unordered_set>
#include <vector>

namespace linecode::domain {

PipelinePlan PlanPipeline(const std::vector<PipelineAgent> &agents) {
  PipelinePlan plan;
  if (agents.empty()) {
    plan.error = {.code = PipelinePlanErrorCode::empty, .agent_id = {}};
    return plan;
  }

  // Legacy `validatePipelineDependencies`: an agent may not depend on itself.
  for (const auto &agent : agents) {
    if (std::ranges::contains(agent.dependencies, agent.id)) {
      plan.error = {.code = PipelinePlanErrorCode::self_dependency,
                    .agent_id = agent.id};
      return plan;
    }
  }

  // Legacy `dependencyLevels` rejects the whole plan when any dependency names
  // an agent that is not part of it.
  std::unordered_set<std::string> all_ids;
  all_ids.reserve(agents.size());
  for (const auto &agent : agents)
    all_ids.insert(agent.id);
  for (const auto &agent : agents) {
    for (const auto &dependency : agent.dependencies) {
      if (!all_ids.contains(dependency)) {
        plan.error = {.code = PipelinePlanErrorCode::unknown_dependency,
                      .agent_id = dependency};
        return plan;
      }
    }
  }

  // Repeatedly take every agent whose dependencies are already satisfied. A
  // pass that adds nothing means the remaining agents form a cycle, which the
  // legacy code reported as an empty plan.
  std::unordered_set<std::string> completed;
  completed.reserve(agents.size());
  while (completed.size() < agents.size()) {
    std::vector<PipelineAgent> level;
    for (const auto &agent : agents) {
      if (completed.contains(agent.id))
        continue;
      const bool ready = std::ranges::all_of(
          agent.dependencies,
          [&completed](const std::string &id) { return completed.contains(id); });
      if (ready)
        level.push_back(agent);
    }
    if (level.empty()) {
      plan.error = {.code = PipelinePlanErrorCode::cycle, .agent_id = {}};
      plan.levels.clear();
      return plan;
    }
    for (const auto &agent : level)
      completed.insert(agent.id);
    plan.levels.push_back(std::move(level));
  }
  return plan;
}

} // namespace linecode::domain
