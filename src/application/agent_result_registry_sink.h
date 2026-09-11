#pragma once

#include <memory>
#include <string>
#include <utility>

#include "application/agent_result_registry.h"
#include "application/sub_agent_runner.h"

namespace linecode::application {

// Bridges the execution engine's narrow result sink onto the shared result
// registry, so `agent` / `agent_pipeline` refs and `agent_output` reads see
// the same records.
class AgentResultRegistrySink final : public SubAgentResultSink {
public:
  explicit AgentResultRegistrySink(std::shared_ptr<AgentResultRegistry> results)
      : results_(std::move(results)) {}

  [[nodiscard]] std::string AllocateId() override {
    return results_ ? results_->AllocateId() : std::string{};
  }

  void Record(SubAgentRunRecord record) override {
    if (!results_)
      return;
    auto stored = CreateAgentResultRecord(
        record.agent_id, record.tool_call_id, std::string{"agent"},
        record.status, record.type, record.description,
        ClampAgentPreview(record.preview), record.output, std::string{},
        std::string{}, record.tool_call_count, record.error, record.async, 0,
        AgentResultNowMillis());
    results_->Put(stored);
  }

  [[nodiscard]] std::string
  ToCompactRef(const SubAgentRunRecord &record) const override {
    if (!results_)
      return std::string{};
    auto stored = CreateAgentResultRecord(
        record.agent_id, record.tool_call_id, std::string{"agent"},
        record.status, record.type, record.description,
        ClampAgentPreview(record.preview), record.output, std::string{},
        std::string{}, record.tool_call_count, record.error, record.async, 0,
        AgentResultNowMillis());
    return AgentResultRegistry::ToCompactJson(stored);
  }

private:
  std::shared_ptr<AgentResultRegistry> results_;
};

} // namespace linecode::application
