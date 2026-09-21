// Contract tests for the ported pipeline dependency resolver.

#include "gtest_support.h"
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
  EXPECT_EXPRESSION(plan.ok());
  EXPECT_EXPRESSION(plan.levels.size() == 1);
  // Input order is preserved inside a level.
  EXPECT_EXPRESSION(LevelIds(plan.levels[0]) ==
         (std::vector<std::string>{"a", "b", "c"}));
}

void LinearChainProducesOneLevelPerAgent() {
  const auto plan =
      PlanPipeline({Agent("a"), Agent("b", {"a"}), Agent("c", {"b"})});
  EXPECT_EXPRESSION(plan.ok());
  EXPECT_EXPRESSION(plan.levels.size() == 3);
  EXPECT_EXPRESSION(LevelIds(plan.levels[0]) == std::vector<std::string>{"a"});
  EXPECT_EXPRESSION(LevelIds(plan.levels[1]) == std::vector<std::string>{"b"});
  EXPECT_EXPRESSION(LevelIds(plan.levels[2]) == std::vector<std::string>{"c"});
}

void DiamondGroupsByDepth() {
  // a -> {b, c} -> d
  const auto plan = PlanPipeline(
      {Agent("a"), Agent("b", {"a"}), Agent("c", {"a"}), Agent("d", {"b", "c"})});
  EXPECT_EXPRESSION(plan.ok());
  EXPECT_EXPRESSION(plan.levels.size() == 3);
  EXPECT_EXPRESSION(LevelIds(plan.levels[0]) == std::vector<std::string>{"a"});
  EXPECT_EXPRESSION(LevelIds(plan.levels[1]) == (std::vector<std::string>{"b", "c"}));
  EXPECT_EXPRESSION(LevelIds(plan.levels[2]) == std::vector<std::string>{"d"});
}

void MultipleDependenciesWaitForAll() {
  // d depends on b and c, so it cannot share b's level even though b is ready.
  const auto plan =
      PlanPipeline({Agent("a"), Agent("b", {"a"}), Agent("c"), Agent("d", {"b", "c"})});
  EXPECT_EXPRESSION(plan.ok());
  EXPECT_EXPRESSION(plan.levels.size() == 3);
  EXPECT_EXPRESSION(LevelIds(plan.levels[1]) == std::vector<std::string>{"b"});
  EXPECT_EXPRESSION(LevelIds(plan.levels[2]) == std::vector<std::string>{"d"});
}

void EmptyPipelineIsRejected() {
  const auto plan = PlanPipeline({});
  EXPECT_EXPRESSION(!plan.ok());
  EXPECT_EXPRESSION(plan.error.code == PipelinePlanErrorCode::empty);
  EXPECT_EXPRESSION(plan.levels.empty());
}

void SelfDependencyIsRejected() {
  const auto plan = PlanPipeline({Agent("a", {"a"})});
  EXPECT_EXPRESSION(!plan.ok());
  EXPECT_EXPRESSION(plan.error.code == PipelinePlanErrorCode::self_dependency);
  EXPECT_EXPRESSION(plan.error.agent_id == "a");
}

void UnknownDependencyIsRejected() {
  // The legacy resolver discarded the whole plan when a dependency named an
  // agent that was not part of it.
  const auto plan = PlanPipeline({Agent("a", {"ghost"})});
  EXPECT_EXPRESSION(!plan.ok());
  EXPECT_EXPRESSION(plan.error.code == PipelinePlanErrorCode::unknown_dependency);
  EXPECT_EXPRESSION(plan.error.agent_id == "ghost");
  EXPECT_EXPRESSION(plan.levels.empty());
}

void CyclesAreRejected() {
  const auto plan =
      PlanPipeline({Agent("a", {"b"}), Agent("b", {"a"})});
  EXPECT_EXPRESSION(!plan.ok());
  EXPECT_EXPRESSION(plan.error.code == PipelinePlanErrorCode::cycle);
  EXPECT_EXPRESSION(plan.levels.empty());

  // A longer cycle behaves the same way.
  const auto longer = PlanPipeline(
      {Agent("a", {"c"}), Agent("b", {"a"}), Agent("c", {"b"})});
  EXPECT_EXPRESSION(!longer.ok());
  EXPECT_EXPRESSION(longer.error.code == PipelinePlanErrorCode::cycle);
}

void CycleDoesNotDiscardAnAlreadyBuiltPrefix() {
  // a and b are acyclic; c and d form a cycle. Planning still reports the
  // cycle rather than returning a partial plan as success.
  const auto plan = PlanPipeline(
      {Agent("a"), Agent("b"), Agent("c", {"d"}), Agent("d", {"c"})});
  EXPECT_EXPRESSION(!plan.ok());
  EXPECT_EXPRESSION(plan.error.code == PipelinePlanErrorCode::cycle);
}

void ScopeListsAreCarriedThrough() {
  auto agent = Agent("a");
  agent.read_scope = {"src"};
  agent.write_scope = {"src/a.cpp"};
  agent.type = "sub-coding";
  const auto plan = PlanPipeline({agent});
  EXPECT_EXPRESSION(plan.ok());
  EXPECT_EXPRESSION(plan.levels.front().front().write_scope ==
         std::vector<std::string>{"src/a.cpp"});
  EXPECT_EXPRESSION(plan.levels.front().front().type == "sub-coding");
}

} // namespace

TEST(agent_pipeline_tests, LegacySuite) {
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
  return;
}
