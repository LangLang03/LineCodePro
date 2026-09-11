// Contract tests for the ported pipeline dependency resolver.

#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "domain/agent_pipeline.h"

namespace {

using linecode::domain::PipelineAgent;
using linecode::domain::PipelinePlanErrorCode;
using linecode::domain::PlanPipeline;

PipelineAgent Agent(std::string id,
                    std::vector<std::string> dependencies = {}) {
  PipelineAgent agent;
  agent.id = std::move(id);
  agent.type = "explore";
  agent.description = "task";
  agent.prompt = "do it";
  agent.dependencies = std::move(dependencies);
  return agent;
}

std::vector<std::string> LevelIds(const std::vector<PipelineAgent> &level) {
  std::vector<std::string> ids;
  ids.reserve(level.size());
  for (const auto &agent : level)
    ids.push_back(agent.id);
  return ids;
}

void IndependentAgentsShareOneLevel() {
  const auto plan = PlanPipeline({Agent("a"), Agent("b"), Agent("c")});
  assert(plan.ok());
  assert(plan.levels.size() == 1);
  // Input order is preserved inside a level.
  assert(LevelIds(plan.levels[0]) ==
         (std::vector<std::string>{"a", "b", "c"}));
}

void LinearChainProducesOneLevelPerAgent() {
  const auto plan =
      PlanPipeline({Agent("a"), Agent("b", {"a"}), Agent("c", {"b"})});
  assert(plan.ok());
  assert(plan.levels.size() == 3);
  assert(LevelIds(plan.levels[0]) == std::vector<std::string>{"a"});
  assert(LevelIds(plan.levels[1]) == std::vector<std::string>{"b"});
  assert(LevelIds(plan.levels[2]) == std::vector<std::string>{"c"});
}

void DiamondGroupsByDepth() {
  // a -> {b, c} -> d
  const auto plan = PlanPipeline(
      {Agent("a"), Agent("b", {"a"}), Agent("c", {"a"}), Agent("d", {"b", "c"})});
  assert(plan.ok());
  assert(plan.levels.size() == 3);
  assert(LevelIds(plan.levels[0]) == std::vector<std::string>{"a"});
  assert(LevelIds(plan.levels[1]) == (std::vector<std::string>{"b", "c"}));
  assert(LevelIds(plan.levels[2]) == std::vector<std::string>{"d"});
}

void MultipleDependenciesWaitForAll() {
  // d depends on b and c, so it cannot share b's level even though b is ready.
  const auto plan =
      PlanPipeline({Agent("a"), Agent("b", {"a"}), Agent("c"), Agent("d", {"b", "c"})});
  assert(plan.ok());
  assert(plan.levels.size() == 3);
  assert(LevelIds(plan.levels[1]) == std::vector<std::string>{"b"});
  assert(LevelIds(plan.levels[2]) == std::vector<std::string>{"d"});
}

void EmptyPipelineIsRejected() {
  const auto plan = PlanPipeline({});
  assert(!plan.ok());
  assert(plan.error.code == PipelinePlanErrorCode::empty);
  assert(plan.levels.empty());
}

void SelfDependencyIsRejected() {
  const auto plan = PlanPipeline({Agent("a", {"a"})});
  assert(!plan.ok());
  assert(plan.error.code == PipelinePlanErrorCode::self_dependency);
  assert(plan.error.agent_id == "a");
}

void UnknownDependencyIsRejected() {
  // The legacy resolver discarded the whole plan when a dependency named an
  // agent that was not part of it.
  const auto plan = PlanPipeline({Agent("a", {"ghost"})});
  assert(!plan.ok());
  assert(plan.error.code == PipelinePlanErrorCode::unknown_dependency);
  assert(plan.error.agent_id == "ghost");
  assert(plan.levels.empty());
}

void CyclesAreRejected() {
  const auto plan =
      PlanPipeline({Agent("a", {"b"}), Agent("b", {"a"})});
  assert(!plan.ok());
  assert(plan.error.code == PipelinePlanErrorCode::cycle);
  assert(plan.levels.empty());

  // A longer cycle behaves the same way.
  const auto longer = PlanPipeline(
      {Agent("a", {"c"}), Agent("b", {"a"}), Agent("c", {"b"})});
  assert(!longer.ok());
  assert(longer.error.code == PipelinePlanErrorCode::cycle);
}

void CycleDoesNotDiscardAnAlreadyBuiltPrefix() {
  // a and b are acyclic; c and d form a cycle. Planning still reports the
  // cycle rather than returning a partial plan as success.
  const auto plan = PlanPipeline(
      {Agent("a"), Agent("b"), Agent("c", {"d"}), Agent("d", {"c"})});
  assert(!plan.ok());
  assert(plan.error.code == PipelinePlanErrorCode::cycle);
}

void ScopeListsAreCarriedThrough() {
  auto agent = Agent("a");
  agent.read_scope = {"src"};
  agent.write_scope = {"src/a.cpp"};
  agent.type = "sub-coding";
  const auto plan = PlanPipeline({agent});
  assert(plan.ok());
  assert(plan.levels.front().front().write_scope ==
         std::vector<std::string>{"src/a.cpp"});
  assert(plan.levels.front().front().type == "sub-coding");
}

} // namespace

int main() {
  IndependentAgentsShareOneLevel();
  LinearChainProducesOneLevelPerAgent();
  DiamondGroupsByDepth();
  MultipleDependenciesWaitForAll();
  EmptyPipelineIsRejected();
  SelfDependencyIsRejected();
  UnknownDependencyIsRejected();
  CyclesAreRejected();
  CycleDoesNotDiscardAnAlreadyBuiltPrefix();
  ScopeListsAreCarriedThrough();
  std::cout << "agent_pipeline_tests passed\n";
  return 0;
}
