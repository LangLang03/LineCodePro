#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <variant>
#include <vector>

namespace linecode::domain {

enum class AgentToolCallStatus : std::uint8_t {
  requested,
  running,
  completed,
  failed,
};

struct AgentToolCallResultSnapshot final {
  std::string content;
  bool error{};
  std::string diff_id;

  bool operator==(const AgentToolCallResultSnapshot &) const = default;
};

struct AgentToolCallSnapshot final {
  std::string id;
  std::string name;
  std::string arguments_json;
  AgentToolCallStatus status{AgentToolCallStatus::requested};
  // Retaining the lifecycle makes the final snapshot describe the requested
  // and running transitions as well as the terminal state.
  std::vector<AgentToolCallStatus> status_history{
      AgentToolCallStatus::requested};
  std::optional<AgentToolCallResultSnapshot> result;

  bool operator==(const AgentToolCallSnapshot &) const = default;
};

enum class AgentExecutionStatus : std::uint8_t {
  waiting,
  running,
  done,
  error,
};

// One typed execution snapshot shared by a standalone Agent and an Agent
// inside a pipeline. Pipeline-only fields remain harmlessly empty for a
// standalone run.
struct AgentExecutionSnapshot final {
  std::string id;
  std::string type;
  std::string description;
  std::vector<std::string> dependencies;
  AgentExecutionStatus status{AgentExecutionStatus::waiting};
  std::string thinking;
  std::string output;
  std::vector<AgentToolCallSnapshot> tool_calls;
  bool error{};

  bool operator==(const AgentExecutionSnapshot &) const = default;
};

struct AgentPipelineSnapshot final {
  AgentExecutionStatus status{AgentExecutionStatus::running};
  std::string summary;
  std::vector<AgentExecutionSnapshot> agents;
  bool error{};

  bool operator==(const AgentPipelineSnapshot &) const = default;
};

using AgentProgressSnapshot =
    std::variant<std::monostate, AgentExecutionSnapshot, AgentPipelineSnapshot>;

} // namespace linecode::domain
