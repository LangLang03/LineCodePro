#include "presentation/chat_timeline_presentation.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "application/agent_result_registry.h"
#include "application/agent_run_progress_codec.h"
#include "application/tool_result_display_policy.h"
#include "infrastructure/archive_json.h"

namespace linecode::presentation {
namespace {

namespace json = infrastructure::archive_json;

[[nodiscard]] bool IsRunning(domain::ToolCallStatus status) noexcept {
  return status == domain::ToolCallStatus::requested ||
         status == domain::ToolCallStatus::awaiting_review ||
         status == domain::ToolCallStatus::running;
}

[[nodiscard]] bool IsFailed(domain::ToolCallStatus status) noexcept {
  return status == domain::ToolCallStatus::failed ||
         status == domain::ToolCallStatus::rejected;
}

[[nodiscard]] const json::Object *ParseObject(std::string_view text,
                                              json::Value &storage) {
  auto parsed = json::Parse(text.empty() ? "{}" : text);
  if (!parsed)
    return nullptr;
  storage = std::move(*parsed);
  return json::AsObject(&storage);
}

[[nodiscard]] std::string StringAt(const json::Object *object,
                                   std::string_view key) {
  if (object == nullptr)
    return {};
  const auto *value = json::AsString(json::Find(*object, key));
  return value == nullptr ? std::string{} : *value;
}

[[nodiscard]] bool BoolAt(const json::Object *object, std::string_view key) {
  if (object == nullptr)
    return false;
  const auto *value = json::Find(*object, key);
  return value != nullptr && std::holds_alternative<bool>(*value) &&
         std::get<bool>(*value);
}

[[nodiscard]] int IntAt(const json::Object *object, std::string_view key) {
  if (object == nullptr)
    return 0;
  const auto *value = json::Find(*object, key);
  if (value == nullptr)
    return 0;
  if (const auto *integer = std::get_if<std::int64_t>(value))
    return static_cast<int>(*integer);
  if (const auto *number = std::get_if<double>(value))
    return static_cast<int>(*number);
  return 0;
}

[[nodiscard]] std::string Trim(std::string_view value) {
  const auto begin = value.find_first_not_of(" \t\r\n");
  if (begin == std::string_view::npos)
    return {};
  const auto end = value.find_last_not_of(" \t\r\n");
  return std::string{value.substr(begin, end - begin + 1U)};
}

[[nodiscard]] std::string FirstLabel(const json::Object *input,
                                     std::string_view fallback) {
  constexpr std::array keys{"file_path", "pattern", "query", "url", "path",
                            "command"};
  for (const auto key : keys) {
    auto value = StringAt(input, key);
    if (!value.empty())
      return value;
  }
  return std::string{fallback};
}

[[nodiscard]] std::string BaseName(std::string_view path) {
  const auto slash = path.find_last_of("/\\");
  return std::string{slash == std::string_view::npos ? path
                                                     : path.substr(slash + 1)};
}

[[nodiscard]] std::string ShellOutput(std::string_view command,
                                      std::string_view content,
                                      bool pending) {
  constexpr std::size_t kMaximum = 64U * 1024U;
  constexpr std::size_t kHead = 24U * 1024U;
  constexpr std::size_t kTail = 36U * 1024U;
  std::string folded;
  if (!pending && content.size() > kMaximum) {
    const auto omitted = content.size() - kHead - kTail;
    folded.reserve(kHead + kTail + 80U);
    folded.append(content.substr(0, kHead));
    folded.append("\n\n[LineCode folded ");
    folded.append(std::to_string(omitted));
    folded.append(" characters of shell output]\n\n");
    folded.append(content.substr(content.size() - kTail));
    content = folded;
  }
  std::string value{"$ "};
  value.append(command);
  if (!pending && !content.empty()) {
    value.append("\n\n");
    value.append(content);
  }
  return value;
}

void PresentDelete(const json::Object *input, const domain::AssistantToolEvent &event,
                   ToolTimelinePresentation &result) {
  std::vector<std::string> paths;
  if (input != nullptr) {
    if (const auto *array = json::AsArray(json::Find(*input, "paths"))) {
      for (const auto &item : *array) {
        if (const auto *path = json::AsString(&item); path != nullptr)
          paths.push_back(*path);
      }
    }
  }
  for (const auto key : {std::string_view{"file_path"}, std::string_view{"path"}}) {
    auto path = StringAt(input, key);
    if (!path.empty())
      paths.push_back(std::move(path));
  }
  result.item_count = static_cast<int>(paths.size());
  result.title = "Delete";
  result.detail = Trim(StringAt(input, "reason"));
  for (const auto &path : paths) {
    if (!result.detail.empty())
      result.detail.push_back('\n');
    result.detail.append(path);
  }
  if (result.failed && event.result && !event.result->content.empty()) {
    if (!result.detail.empty())
      result.detail.append("\n\n");
    result.detail.append(event.result->content);
  }
}

[[nodiscard]] ToolTimelineTodoItem::State TodoState(std::string_view state) {
  if (state == "completed")
    return ToolTimelineTodoItem::State::completed;
  if (state == "in_progress")
    return ToolTimelineTodoItem::State::in_progress;
  return ToolTimelineTodoItem::State::pending;
}

void PresentTodo(const json::Object *input, ToolTimelinePresentation &result) {
  result.expandable = false;
  result.title = "TODO";
  const auto *items = input == nullptr ? nullptr
                                       : json::AsArray(json::Find(*input, "items"));
  if (items == nullptr)
    return;
  result.todo_items.reserve(items->size());
  for (const auto &value : *items) {
    const auto *item = json::AsObject(&value);
    auto content = StringAt(item, "content");
    if (content.empty())
      continue;
    result.todo_items.push_back({.content = std::move(content),
                                 .state = TodoState(StringAt(item, "status"))});
  }
  result.item_count = static_cast<int>(result.todo_items.size());
}

void PresentAgent(const json::Object *input, const domain::AssistantToolEvent &event,
                  ToolTimelinePresentation &result) {
  json::Value progress_storage;
  const auto *progress = event.result
                             ? ParseObject(event.result->content, progress_storage)
                             : nullptr;
  const bool envelope = BoolAt(progress, "linecode_agent_ref") ||
                        BoolAt(progress, "linecode_agent_progress") ||
                        BoolAt(progress, "linecode_agent_pipeline_progress");
  result.title = StringAt(progress, "description");
  if (result.title.empty())
    result.title = FirstLabel(input, "Agent");
  result.auxiliary = StringAt(progress, "type");
  if (result.auxiliary.empty())
    result.auxiliary = StringAt(input, "type");
  if (result.auxiliary.empty())
    result.auxiliary = "explore";
  result.agent_id = StringAt(progress, "agent_id");
  result.tool_call_count = IntAt(progress, "tool_call_count");
  result.input_detail = StringAt(progress, "thinking");
  result.output_detail = StringAt(progress, "output");
  if (result.output_detail.empty())
    result.output_detail = StringAt(progress, "model_content");
  if (result.output_detail.empty())
    result.output_detail = StringAt(progress, "preview");
  if (result.output_detail.empty() && event.result && !envelope)
    result.output_detail = event.result->content;
  result.detail = result.output_detail;
  result.initially_expanded = true;
}

void PresentPipeline(const json::Object *input,
                     const domain::AssistantToolEvent &event,
                     ToolTimelinePresentation &result) {
  result.title = "Agent Pipeline";
  result.initially_expanded = true;
  json::Value progress_storage;
  const auto *progress = event.result
                             ? ParseObject(event.result->content, progress_storage)
                             : nullptr;
  const auto *definitions = input == nullptr
                                ? nullptr
                                : json::AsArray(json::Find(*input, "agents"));
  result.item_count = definitions == nullptr
                          ? 0
                          : static_cast<int>(definitions->size());
  const auto *agents = progress == nullptr
                           ? nullptr
                           : json::AsArray(json::Find(*progress, "agents"));
  if (agents != nullptr) {
    for (const auto &value : *agents) {
      const auto *agent = json::AsObject(&value);
      const auto status = StringAt(agent, "status");
      const bool failed = BoolAt(agent, "error") || status == "error";
      result.failed_count += failed ? 1 : 0;
      result.completed_count += status == "done" && !failed ? 1 : 0;
      result.running_count += status == "running" ? 1 : 0;
    }
  }
  result.agent_id = StringAt(progress, "agent_id");
  result.tool_call_count = IntAt(progress, "tool_call_count");
  result.output_detail = StringAt(progress, "summary");
  if (result.output_detail.empty())
    result.output_detail = StringAt(progress, "preview");
  if (result.output_detail.empty() && event.result &&
      !BoolAt(progress, "linecode_agent_pipeline_progress") &&
      !BoolAt(progress, "linecode_agent_ref"))
    result.output_detail = event.result->content;
  result.detail = result.output_detail;
}

[[nodiscard]] domain::ToolCallStatus
ToolCallStatus(const domain::AgentToolCallStatus status) noexcept {
  switch (status) {
  case domain::AgentToolCallStatus::requested:
    return domain::ToolCallStatus::requested;
  case domain::AgentToolCallStatus::running:
    return domain::ToolCallStatus::running;
  case domain::AgentToolCallStatus::completed:
    return domain::ToolCallStatus::completed;
  case domain::AgentToolCallStatus::failed:
    return domain::ToolCallStatus::failed;
  }
  return domain::ToolCallStatus::failed;
}

[[nodiscard]] AgentRunTimelinePresentation
PresentAgentRun(const domain::AgentExecutionSnapshot &snapshot) {
  AgentRunTimelinePresentation result{
      .id = snapshot.id,
      .type = snapshot.type,
      .description = snapshot.description,
      .dependencies = snapshot.dependencies,
      .status = std::string{
          application::AgentExecutionStatusName(snapshot.status)},
      .thinking = snapshot.thinking,
      .output = snapshot.output,
      .tool_calls = {},
      .failed = snapshot.error ||
                snapshot.status == domain::AgentExecutionStatus::error,
  };
  result.tool_calls.reserve(snapshot.tool_calls.size());
  for (const auto &call : snapshot.tool_calls) {
    domain::AssistantToolEvent event{
        .turn_index = 0,
        .call = {.id = call.id,
                 .name = call.name,
                 .arguments_json = call.arguments_json,
                 .status = ToolCallStatus(call.status),
                 .created_at_millis = 0,
                 .duration_millis = 0,
                 .error_message = {}},
        .result = std::nullopt,
    };
    if (call.result) {
      event.result = domain::ChatToolResult{
          .call_id = call.id,
          .name = call.name,
          .content = call.result->content,
          .error = call.result->error,
          .diff_id = call.result->diff_id,
          .review_state = {},
          .review_message = {},
      };
    }
    result.tool_calls.push_back(std::move(event));
  }
  return result;
}

void ApplyAgentResult(const application::AgentResultView &view,
                      ToolTimelinePresentation &result) {
  result.agent_id = view.agent_id;
  result.tool_call_count = view.tool_call_count;
  result.failed = result.failed || view.error;
  if (!view.type.empty())
    result.auxiliary = view.type;
  if (!view.description.empty())
    result.title = view.description;
  if (!view.thinking.empty())
    result.input_detail = view.thinking;
  if (!view.full_output.empty())
    result.output_detail = view.full_output;
  else if (!view.preview.empty())
    result.output_detail = view.preview;

  if (const auto *agent =
          std::get_if<domain::AgentExecutionSnapshot>(&view.progress)) {
    result.agent_runs.push_back(PresentAgentRun(*agent));
    result.input_detail = agent->thinking;
    result.output_detail = agent->output;
  } else if (const auto *pipeline =
                 std::get_if<domain::AgentPipelineSnapshot>(&view.progress)) {
    result.item_count = static_cast<int>(pipeline->agents.size());
    result.completed_count = 0;
    result.running_count = 0;
    result.failed_count = 0;
    result.agent_runs.reserve(pipeline->agents.size());
    for (const auto &agent : pipeline->agents) {
      result.completed_count +=
          agent.status == domain::AgentExecutionStatus::done && !agent.error
              ? 1
              : 0;
      result.running_count +=
          agent.status == domain::AgentExecutionStatus::running ? 1 : 0;
      result.failed_count +=
          agent.error || agent.status == domain::AgentExecutionStatus::error
              ? 1
              : 0;
      result.agent_runs.push_back(PresentAgentRun(agent));
    }
    if (!pipeline->summary.empty())
      result.output_detail = pipeline->summary;
  }
  result.detail = result.output_detail;
}

[[nodiscard]] std::string PrettyJson(const json::Value &value, int depth = 0) {
  if (const auto *object = json::AsObject(&value)) {
    if (object->empty())
      return "{}";
    std::string output{"{\n"};
    std::size_t index{};
    for (const auto &[key, child] : *object) {
      output.append(static_cast<std::size_t>(depth + 1) * 2U, ' ');
      output.append(json::Serialize(json::Value{key}));
      output.append(": ");
      output.append(PrettyJson(child, depth + 1));
      output.append(++index == object->size() ? "\n" : ",\n");
    }
    output.append(static_cast<std::size_t>(depth) * 2U, ' ');
    output.push_back('}');
    return output;
  }
  if (const auto *array = json::AsArray(&value)) {
    if (array->empty())
      return "[]";
    std::string output{"[\n"};
    for (std::size_t index{}; index < array->size(); ++index) {
      output.append(static_cast<std::size_t>(depth + 1) * 2U, ' ');
      output.append(PrettyJson((*array)[index], depth + 1));
      output.append(index + 1U == array->size() ? "\n" : ",\n");
    }
    output.append(static_cast<std::size_t>(depth) * 2U, ' ');
    output.push_back(']');
    return output;
  }
  return json::Serialize(value);
}

} // namespace

ToolTimelineRendererRegistry::ToolTimelineRendererRegistry(
    std::vector<ToolTimelineRendererRegistration> registrations)
    : registrations_(std::move(registrations)) {
  if (std::ranges::any_of(registrations_, [](const auto &entry) {
        return entry.name.empty();
      }))
    throw std::invalid_argument("Tool timeline registrations need a name");
}

ToolTimelineVisualKind ToolTimelineRendererRegistry::Resolve(
    std::string_view tool_name) const noexcept {
  const auto exact = std::ranges::find_if(registrations_, [&](const auto &entry) {
    return entry.match == ToolNameMatch::exact && entry.name == tool_name;
  });
  if (exact != registrations_.end())
    return exact->visual;
  const auto prefix = std::ranges::find_if(registrations_, [&](const auto &entry) {
    return entry.match == ToolNameMatch::prefix && tool_name.starts_with(entry.name);
  });
  return prefix == registrations_.end() ? ToolTimelineVisualKind::generic
                                         : prefix->visual;
}

ToolTimelineIconKind ToolTimelineRendererRegistry::ResolveIcon(
    std::string_view tool_name) const noexcept {
  const auto exact = std::ranges::find_if(registrations_, [&](const auto &entry) {
    return entry.match == ToolNameMatch::exact && entry.name == tool_name;
  });
  if (exact != registrations_.end())
    return exact->icon;
  const auto prefix = std::ranges::find_if(registrations_, [&](const auto &entry) {
    return entry.match == ToolNameMatch::prefix && tool_name.starts_with(entry.name);
  });
  return prefix == registrations_.end() ? ToolTimelineIconKind::file
                                         : prefix->icon;
}

const ToolTimelineRendererRegistry &DefaultToolTimelineRendererRegistry() {
  static const ToolTimelineRendererRegistry registry({
      {"shell_execute", ToolNameMatch::exact, ToolTimelineVisualKind::shell},
      {"terminal", ToolNameMatch::exact, ToolTimelineVisualKind::shell},
      {"file_read", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"read_file", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"list_dir", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::folder},
      {"list_files", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::folder},
      {"glob", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::search},
      {"web_search", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::search},
      {"web_fetch", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::globe},
      {"image_understanding", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::paintbrush},
      {"agent_output", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::bot},
      {"memory_update", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::book_open},
      {"file_write", ToolNameMatch::exact, ToolTimelineVisualKind::write},
      {"write_file", ToolNameMatch::exact, ToolTimelineVisualKind::write},
      {"file_edit", ToolNameMatch::exact, ToolTimelineVisualKind::write},
      {"file_delete", ToolNameMatch::exact, ToolTimelineVisualKind::remove},
      {"todo_update", ToolNameMatch::exact, ToolTimelineVisualKind::todo},
      {"agent", ToolNameMatch::exact, ToolTimelineVisualKind::agent},
      {"agentx_", ToolNameMatch::prefix, ToolTimelineVisualKind::agent},
      {"agent_pipeline", ToolNameMatch::exact,
       ToolTimelineVisualKind::agent_pipeline},
      {"phone_", ToolNameMatch::prefix, ToolTimelineVisualKind::read},
      {"image_generation", ToolNameMatch::exact, ToolTimelineVisualKind::read,
       ToolTimelineIconKind::sparkles},
      {"mcpx_", ToolNameMatch::prefix, ToolTimelineVisualKind::generic},
  });
  return registry;
}

std::string ToolCallTargetPath(const std::string_view arguments_json,
                               const std::string_view fallback) {
  json::Value storage;
  const auto *input = ParseObject(arguments_json, storage);
  if (input != nullptr) {
    for (const auto key : {std::string_view{"file_path"},
                           std::string_view{"path"}}) {
      const auto *value = json::AsString(json::Find(*input, key));
      if (value != nullptr && !value->empty())
        return *value;
    }
  }
  return std::string{fallback};
}

ToolTimelinePresentation
PresentToolTimeline(const domain::AssistantToolEvent &event,
                    const application::AgentResultReader *agent_results) {
  const auto display = event.result
                           ? application::DefaultToolResultDisplayProjector()
                                 ->Project(event.call.name, event.result->content,
                                           event.result->error)
                           : application::ToolResultDisplayProjection{};
  ToolTimelinePresentation result{
      .visual = DefaultToolTimelineRendererRegistry().Resolve(event.call.name),
      .icon = DefaultToolTimelineRendererRegistry().ResolveIcon(event.call.name),
      .status = event.call.status,
      .title = event.call.name,
      .detail = event.result ? event.result->content : event.call.arguments_json,
      .input_detail = {},
      .output_detail = {},
      .auxiliary = {},
      .tool_call_id = event.call.id,
      .todo_items = {},
      .agent_runs = {},
      .item_count = 0,
      .completed_count = 0,
      .running_count = 0,
      .failed_count = 0,
      .running = IsRunning(event.call.status),
      .failed = IsFailed(event.call.status) ||
                (event.result && event.result->error),
      .expandable = true,
      .initially_expanded = IsRunning(event.call.status),
      .visible = !(event.call.status == domain::ToolCallStatus::completed &&
                   display.hide_success_card),
  };

  json::Value input_storage;
  const auto *input = ParseObject(event.call.arguments_json, input_storage);
  switch (result.visual) {
  case ToolTimelineVisualKind::shell: {
    result.title = StringAt(input, "command");
    // ToolCallShellView keeps expansion in an initially empty map, so even a
    // running command starts collapsed and opens only after the header click.
    result.initially_expanded = false;
    const bool pending = event.call.status == domain::ToolCallStatus::awaiting_review;
    result.detail = ShellOutput(result.title,
                                event.result ? event.result->content : "", pending);
    break;
  }
  case ToolTimelineVisualKind::read:
    result.title = FirstLabel(input, event.call.name);
    result.expandable = false;
    result.initially_expanded = false;
    result.detail = result.failed && event.result ? event.result->content : "";
    break;
  case ToolTimelineVisualKind::write: {
    const auto path = FirstLabel(input, {});
    result.title = BaseName(path);
    if (result.title.empty())
      result.title = "Unnamed file";
    result.auxiliary = path;
    result.detail = event.result ? event.result->content : "";
    if (event.result) {
      result.diff_id = event.result->diff_id;
      result.review_state = event.result->review_state;
      result.review_message = event.result->review_message;
    }
    break;
  }
  case ToolTimelineVisualKind::remove:
    PresentDelete(input, event, result);
    break;
  case ToolTimelineVisualKind::todo:
    PresentTodo(input, result);
    break;
  case ToolTimelineVisualKind::agent:
    PresentAgent(input, event, result);
    // Legacy ConversationTimeline does not materialize Agent operations into
    // the process block until the tool has produced its result. Showing the
    // card while the call is still running exposes an extra "Running task…"
    // state that the original UI never renders.
    result.visible = result.visible && !result.running;
    break;
  case ToolTimelineVisualKind::agent_pipeline:
    PresentPipeline(input, event, result);
    result.visible = result.visible && !result.running;
    break;
  case ToolTimelineVisualKind::generic:
    if (input != nullptr && !input->empty())
      result.input_detail = PrettyJson(input_storage);
    result.output_detail = event.result ? event.result->content : "";
    if (result.input_detail.size() > 65'536U)
      result.input_detail = result.input_detail.substr(0, 65'536U) + "…";
    if (result.output_detail.size() > 65'536U)
      result.output_detail = result.output_detail.substr(0, 65'536U) + "…";
    result.detail = result.output_detail;
    break;
  }
  if (agent_results != nullptr && event.result) {
    if (const auto resolved = application::ResolveAgentResultReference(
            event.result->content, *agent_results)) {
      ApplyAgentResult(*resolved, result);
    }
  }
  return result;
}

AssistantProcessPresentation PresentAssistantProcess(
    const domain::ChatMessage &message, bool live,
    bool process_auto_expand, std::int64_t now_millis) noexcept {
  const bool running_tool = std::ranges::any_of(
      message.timeline, [](const auto &event) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        return tool != nullptr && IsRunning(tool->call.status);
      });
  const bool failed_tool = std::ranges::any_of(
      message.timeline, [](const auto &event) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        return tool != nullptr && IsFailed(tool->call.status);
      });
  const bool pending_review = std::ranges::any_of(
      message.timeline, [](const auto &event) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        return tool != nullptr &&
               tool->call.status == domain::ToolCallStatus::awaiting_review;
      });
  if (now_millis <= 0) {
    now_millis = std::chrono::duration_cast<std::chrono::milliseconds>(
                     std::chrono::system_clock::now().time_since_epoch())
                     .count();
  }
  const auto duration =
      message.processing_finished_at > message.processing_started_at
          ? message.processing_finished_at - message.processing_started_at
      : live && message.processing_started_at > 0 &&
              now_millis > message.processing_started_at
          ? now_millis - message.processing_started_at
          : 0;
  return AssistantProcessPresentation{
      .visible = !message.timeline.empty() ||
                 !message.reasoning_content.empty() || live,
      .running = live || running_tool,
      .pending_review = pending_review,
      .failed = message.error || failed_tool,
      .initially_expanded = process_auto_expand,
      .duration_millis = duration,
  };
}

std::string FormatProcessingDuration(const std::int64_t elapsed_millis) {
  const auto seconds = std::max<std::int64_t>(0, elapsed_millis) / 1'000;
  if (seconds < 60)
    return std::to_string(seconds) + "s";
  const auto minutes = seconds / 60;
  if (minutes < 60)
    return std::to_string(minutes) + "m " +
           std::to_string(seconds % 60) + "s";
  return std::to_string(minutes / 60) + "h " +
         std::to_string(minutes % 60) + "m " +
         std::to_string(seconds % 60) + "s";
}

namespace {

[[nodiscard]] bool HasToolEvent(const domain::ChatMessage &message) {
  return std::ranges::any_of(message.timeline, [](const auto &event) {
    return std::holds_alternative<domain::AssistantToolEvent>(event);
  });
}

[[nodiscard]] bool StartsAssistantProcess(const domain::ChatMessage &message) {
  return HasToolEvent(message) || message.retry_notice || message.error ||
         !message.compact_status.empty();
}

[[nodiscard]] bool IsCompletedAnswer(const domain::ChatMessage &message) {
  return !message.retry_notice && !message.error &&
         message.compact_status.empty() && !message.streaming &&
         !Trim(message.content).empty() &&
         (message.processing_started_at == 0 ||
          message.processing_finished_at > 0);
}

[[nodiscard]] bool TimelineContainsText(
    const domain::ChatMessage &message, std::string_view text) {
  return std::ranges::any_of(message.timeline, [text](const auto &event) {
    const auto *entry = std::get_if<domain::AssistantTextEvent>(&event);
    return entry != nullptr && entry->text == text;
  });
}

[[nodiscard]] bool TimelineContainsReasoning(
    const domain::ChatMessage &message, std::string_view text) {
  return std::ranges::any_of(message.timeline, [text](const auto &event) {
    const auto *entry = std::get_if<domain::AssistantReasoningEvent>(&event);
    return entry != nullptr && entry->text == text;
  });
}

[[nodiscard]] domain::ChatMessage MergeAssistantTurn(
    std::span<const domain::ChatMessage> turn) {
  domain::ChatMessage merged{};
  merged.id = turn.front().id;
  merged.role = domain::MessageRole::assistant;
  merged.processing_started_at = turn.front().processing_started_at;

  std::optional<std::size_t> answer_index;
  if (IsCompletedAnswer(turn.back()))
    answer_index = turn.size() - 1U;

  for (std::size_t index = 0; index < turn.size(); ++index) {
    const auto &message = turn[index];
    if (message.processing_started_at > 0 &&
        (merged.processing_started_at <= 0 ||
         message.processing_started_at < merged.processing_started_at)) {
      merged.processing_started_at = message.processing_started_at;
    }
    merged.processing_finished_at =
        std::max(merged.processing_finished_at,
                 message.processing_finished_at);
    merged.streaming = merged.streaming || message.streaming;
    merged.retry_notice = merged.retry_notice || message.retry_notice;
    merged.error = merged.error || message.error;
    if (!message.error_message.empty())
      merged.error_message = message.error_message;

    merged.timeline.insert(merged.timeline.end(), message.timeline.begin(),
                           message.timeline.end());
    if (!message.compact_status.empty()) {
      merged.timeline.push_back(domain::AssistantCompactEvent{
          .turn_index = index, .status = message.compact_status});
      continue;
    }
    if (!message.reasoning_content.empty() &&
        !TimelineContainsReasoning(message, message.reasoning_content)) {
      merged.timeline.push_back(domain::AssistantReasoningEvent{
          .turn_index = index,
          .text = message.reasoning_content,
          .kind = domain::ReasoningKind::thinking,
          .starts_new_segment = index != 0U});
    }

    if (answer_index == index) {
      merged.id = message.id; // actions operate on the visible final answer.
      merged.content = message.content;
      continue;
    }
    if (!message.content.empty() &&
        !TimelineContainsText(message, message.content)) {
      merged.timeline.push_back(domain::AssistantTextEvent{
          .turn_index = index, .text = message.content});
    }
  }
  return merged;
}

void PublishAssistantTurn(std::vector<domain::ChatMessage> &output,
                          std::vector<domain::ChatMessage> &turn) {
  if (turn.empty())
    return;
  if (std::ranges::any_of(turn, StartsAssistantProcess))
    output.push_back(MergeAssistantTurn(turn));
  else
    output.insert(output.end(), turn.begin(), turn.end());
  turn.clear();
}

} // namespace

std::vector<domain::ChatMessage> BuildConversationPresentationMessages(
    std::span<const domain::ChatMessage> messages) {
  std::vector<domain::ChatMessage> output;
  output.reserve(messages.size());
  std::vector<domain::ChatMessage> assistant_turn;
  for (const auto &message : messages) {
    if (message.hidden || message.role == domain::MessageRole::tool)
      continue;
    if (message.role != domain::MessageRole::assistant) {
      PublishAssistantTurn(output, assistant_turn);
      output.push_back(message);
      continue;
    }
    if (!message.compact_status.empty() && !assistant_turn.empty() &&
        IsCompletedAnswer(assistant_turn.back())) {
      PublishAssistantTurn(output, assistant_turn);
    }
    assistant_turn.push_back(message);
  }
  PublishAssistantTurn(output, assistant_turn);
  return output;
}

} // namespace linecode::presentation
