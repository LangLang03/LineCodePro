#include "application/agent_run_progress_codec.h"

#include <cstdint>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

std::string Bounded(std::string_view value, const std::size_t maximum) {
  if (value.size() <= maximum)
    return std::string{value};
  const auto half = maximum / 2U;
  std::string result{value.substr(0U, half)};
  result += "\n... nested value truncated ...\n";
  result += value.substr(value.size() - half);
  return result;
}

std::string StringAt(const json::Object &object, std::string_view key) {
  const auto *value = json::AsString(json::Find(object, key));
  return value == nullptr ? std::string{} : *value;
}

bool BoolAt(const json::Object &object, std::string_view key) {
  const auto *value = json::Find(object, key);
  const auto *flag = value == nullptr ? nullptr : std::get_if<bool>(value);
  return flag != nullptr && *flag;
}

domain::AgentToolCallStatus ToolStatus(std::string_view status) {
  if (status == "running")
    return domain::AgentToolCallStatus::running;
  if (status == "completed")
    return domain::AgentToolCallStatus::completed;
  if (status == "failed")
    return domain::AgentToolCallStatus::failed;
  return domain::AgentToolCallStatus::requested;
}

domain::AgentExecutionStatus ExecutionStatus(std::string_view status) {
  if (status == "running")
    return domain::AgentExecutionStatus::running;
  if (status == "done")
    return domain::AgentExecutionStatus::done;
  if (status == "error")
    return domain::AgentExecutionStatus::error;
  return domain::AgentExecutionStatus::waiting;
}

std::vector<std::string> StringArray(const json::Value *value) {
  std::vector<std::string> result;
  const auto *array = json::AsArray(value);
  if (array == nullptr)
    return result;
  result.reserve(array->size());
  for (const auto &item : *array) {
    if (const auto *text = json::AsString(&item))
      result.push_back(*text);
  }
  return result;
}

std::vector<domain::AgentToolCallSnapshot>
ParseToolCalls(const json::Value *value) {
  std::vector<domain::AgentToolCallSnapshot> calls;
  const auto *array = json::AsArray(value);
  if (array == nullptr)
    return calls;
  calls.reserve(array->size());
  for (const auto &item : *array) {
    const auto *object = json::AsObject(&item);
    if (object == nullptr)
      continue;
    const auto status = ToolStatus(StringAt(*object, "status"));
    auto history = StringArray(json::Find(*object, "status_history"));
    domain::AgentToolCallSnapshot call{
        .id = StringAt(*object, "id"),
        .name = StringAt(*object, "name"),
        .arguments_json = StringAt(*object, "arguments"),
        .status = status,
        .status_history = {},
        .result = std::nullopt,
    };
    for (const auto &entry : history)
      call.status_history.push_back(ToolStatus(entry));
    if (call.status_history.empty())
      call.status_history.push_back(status);
    if (const auto *result = json::AsObject(json::Find(*object, "result"))) {
      call.result = domain::AgentToolCallResultSnapshot{
          .content = StringAt(*result, "content"),
          .error = BoolAt(*result, "is_error"),
          .diff_id = StringAt(*result, "diff_id"),
      };
    }
    calls.push_back(std::move(call));
  }
  return calls;
}

domain::AgentExecutionSnapshot ParseExecution(const json::Object &object) {
  return domain::AgentExecutionSnapshot{
      .id = StringAt(object, "id"),
      .type = StringAt(object, "type"),
      .description = StringAt(object, "description"),
      .dependencies = StringArray(json::Find(object, "depends_on")),
      .status = ExecutionStatus(StringAt(object, "status")),
      .thinking = StringAt(object, "thinking"),
      .output = StringAt(object, "output"),
      .tool_calls = ParseToolCalls(json::Find(object, "tool_calls")),
      .error = BoolAt(object, "error"),
  };
}

json::Array Strings(const std::vector<std::string> &values) {
  json::Array array;
  array.reserve(values.size());
  for (const auto &value : values)
    array.emplace_back(value);
  return array;
}

json::Object ToolCallObject(const domain::AgentToolCallSnapshot &call) {
  json::Object object;
  object.emplace("id", json::Value{call.id});
  object.emplace("name", json::Value{call.name});
  object.emplace("arguments",
                 json::Value{Bounded(call.arguments_json,
                                     kAgentToolCallArgumentsMaxBytes)});
  object.emplace(
      "status", json::Value{std::string{AgentToolCallStatusName(call.status)}});
  json::Array history;
  history.reserve(call.status_history.size());
  for (const auto status : call.status_history) {
    history.emplace_back(std::string{AgentToolCallStatusName(status)});
  }
  object.emplace("status_history", json::Value{std::move(history)});
  if (call.result) {
    json::Object result;
    result.emplace("content",
                   json::Value{Bounded(call.result->content,
                                       kAgentToolCallResultMaxBytes)});
    result.emplace("is_error", json::Value{call.result->error});
    result.emplace("diff_id", json::Value{call.result->diff_id});
    result.emplace("review_state", json::Value{std::string{}});
    result.emplace("review_message", json::Value{std::string{}});
    object.emplace("result", json::Value{std::move(result)});
  }
  return object;
}

json::Array ToolCalls(const domain::AgentExecutionSnapshot &snapshot) {
  json::Array calls;
  calls.reserve(snapshot.tool_calls.size());
  for (const auto &call : snapshot.tool_calls)
    calls.emplace_back(ToolCallObject(call));
  return calls;
}

json::Object ExecutionObject(const domain::AgentExecutionSnapshot &snapshot,
                             const bool include_marker) {
  json::Object object;
  if (include_marker)
    object.emplace("linecode_agent_progress", json::Value{true});
  if (!snapshot.id.empty())
    object.emplace("id", json::Value{snapshot.id});
  object.emplace("kind", json::Value{std::string{"agent"}});
  object.emplace("status", json::Value{std::string{
                               AgentExecutionStatusName(snapshot.status)}});
  object.emplace("type", json::Value{snapshot.type});
  object.emplace("description", json::Value{snapshot.description});
  object.emplace("depends_on", json::Value{Strings(snapshot.dependencies)});
  object.emplace("output", json::Value{Bounded(snapshot.output,
                                               kAgentProgressTextMaxBytes)});
  object.emplace("thinking", json::Value{Bounded(snapshot.thinking,
                                                 kAgentProgressTextMaxBytes)});
  object.emplace("tool_call_count", json::Value{static_cast<std::int64_t>(
                                        snapshot.tool_calls.size())});
  object.emplace(
      "model_content",
      json::Value{Bounded(snapshot.output, kAgentProgressTextMaxBytes)});
  object.emplace("error", json::Value{snapshot.error});
  object.emplace("tool_calls", json::Value{ToolCalls(snapshot)});
  return object;
}

json::Object PipelineObject(const domain::AgentPipelineSnapshot &snapshot) {
  json::Object object;
  object.emplace("linecode_agent_pipeline_progress", json::Value{true});
  object.emplace("kind", json::Value{std::string{"agent_pipeline"}});
  object.emplace("status", json::Value{std::string{
                               AgentExecutionStatusName(snapshot.status)}});
  object.emplace(
      "total", json::Value{static_cast<std::int64_t>(snapshot.agents.size())});
  std::int64_t completed{};
  std::int64_t running{};
  std::int64_t failed{};
  json::Array agents;
  agents.reserve(snapshot.agents.size());
  for (const auto &agent : snapshot.agents) {
    completed += agent.status == domain::AgentExecutionStatus::done ? 1 : 0;
    running += agent.status == domain::AgentExecutionStatus::running ? 1 : 0;
    failed += agent.error || agent.status == domain::AgentExecutionStatus::error
                  ? 1
                  : 0;
    agents.emplace_back(ExecutionObject(agent, false));
  }
  object.emplace("completed", json::Value{completed});
  object.emplace("running", json::Value{running});
  object.emplace("failed", json::Value{failed});
  object.emplace("error", json::Value{snapshot.error});
  if (!snapshot.summary.empty())
    object.emplace("summary", json::Value{Bounded(snapshot.summary,
                                                  kAgentProgressTextMaxBytes)});
  object.emplace("agents", json::Value{std::move(agents)});
  return object;
}

} // namespace

std::string_view
AgentToolCallStatusName(const domain::AgentToolCallStatus status) noexcept {
  switch (status) {
  case domain::AgentToolCallStatus::requested:
    return "requested";
  case domain::AgentToolCallStatus::running:
    return "running";
  case domain::AgentToolCallStatus::completed:
    return "completed";
  case domain::AgentToolCallStatus::failed:
    return "failed";
  }
  return "requested";
}

std::string_view
AgentExecutionStatusName(const domain::AgentExecutionStatus status) noexcept {
  switch (status) {
  case domain::AgentExecutionStatus::waiting:
    return "waiting";
  case domain::AgentExecutionStatus::running:
    return "running";
  case domain::AgentExecutionStatus::done:
    return "done";
  case domain::AgentExecutionStatus::error:
    return "error";
  }
  return "waiting";
}

std::string
SerializeAgentProgress(const domain::AgentProgressSnapshot &snapshot) {
  if (const auto *agent =
          std::get_if<domain::AgentExecutionSnapshot>(&snapshot)) {
    return json::Serialize(json::Value{ExecutionObject(*agent, true)});
  }
  if (const auto *pipeline =
          std::get_if<domain::AgentPipelineSnapshot>(&snapshot)) {
    return json::Serialize(json::Value{PipelineObject(*pipeline)});
  }
  return {};
}

std::optional<domain::AgentProgressSnapshot>
ParseAgentProgress(const std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr)
    return std::nullopt;
  if (BoolAt(*object, "linecode_agent_progress"))
    return domain::AgentProgressSnapshot{ParseExecution(*object)};
  if (!BoolAt(*object, "linecode_agent_pipeline_progress"))
    return std::nullopt;
  domain::AgentPipelineSnapshot pipeline{
      .status = ExecutionStatus(StringAt(*object, "status")),
      .summary = StringAt(*object, "summary"),
      .agents = {},
      .error = BoolAt(*object, "error"),
  };
  if (const auto *agents = json::AsArray(json::Find(*object, "agents"))) {
    pipeline.agents.reserve(agents->size());
    for (const auto &item : *agents) {
      if (const auto *agent = json::AsObject(&item))
        pipeline.agents.push_back(ParseExecution(*agent));
    }
  }
  return domain::AgentProgressSnapshot{std::move(pipeline)};
}

std::string
AgentProgressThinking(const domain::AgentProgressSnapshot &snapshot) {
  if (const auto *agent =
          std::get_if<domain::AgentExecutionSnapshot>(&snapshot)) {
    return Bounded(agent->thinking, kAgentProgressTextMaxBytes);
  }
  return {};
}

} // namespace linecode::application
