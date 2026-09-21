#pragma once

#include <cstddef>
#include <optional>
#include <string>
#include <string_view>

#include "domain/agent_run_progress.h"

namespace linecode::application {

inline constexpr std::size_t kAgentProgressTextMaxBytes = 50U * 1024U;
inline constexpr std::size_t kAgentToolCallArgumentsMaxBytes = 16U * 1024U;
inline constexpr std::size_t kAgentToolCallResultMaxBytes = 50U * 1024U;

[[nodiscard]] std::string_view
AgentToolCallStatusName(domain::AgentToolCallStatus status) noexcept;

[[nodiscard]] std::string_view
AgentExecutionStatusName(domain::AgentExecutionStatus status) noexcept;

// The only JSON writer for Agent runtime progress. Consumers inspect the
// schema, not tool names, so extension tools require no central branching.
[[nodiscard]] std::string
SerializeAgentProgress(const domain::AgentProgressSnapshot &snapshot);

[[nodiscard]] std::optional<domain::AgentProgressSnapshot>
ParseAgentProgress(std::string_view json);

[[nodiscard]] std::string
AgentProgressThinking(const domain::AgentProgressSnapshot &snapshot);

} // namespace linecode::application
