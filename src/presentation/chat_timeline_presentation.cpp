#include "presentation/chat_timeline_presentation.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>

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
  result.input_detail = StringAt(progress, "thinking");
  result.output_detail = StringAt(progress, "output");
  if (result.output_detail.empty())
    result.output_detail = StringAt(progress, "model_content");
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
  result.output_detail = StringAt(progress, "summary");
  if (result.output_detail.empty() && event.result &&
      !BoolAt(progress, "linecode_agent_pipeline_progress"))
    result.output_detail = event.result->content;
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

const ToolTimelineRendererRegistry &DefaultToolTimelineRendererRegistry() {
  static const ToolTimelineRendererRegistry registry({
      {"shell_execute", ToolNameMatch::exact, ToolTimelineVisualKind::shell},
      {"terminal", ToolNameMatch::exact, ToolTimelineVisualKind::shell},
      {"file_read", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"read_file", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"list_dir", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"list_files", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"glob", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"web_search", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"web_fetch", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"image_understanding", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"agent_output", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"memory_update", ToolNameMatch::exact, ToolTimelineVisualKind::read},
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
      {"image_generation", ToolNameMatch::exact, ToolTimelineVisualKind::read},
      {"mcpx_", ToolNameMatch::prefix, ToolTimelineVisualKind::generic},
  });
  return registry;
}

ToolTimelinePresentation
PresentToolTimeline(const domain::AssistantToolEvent &event) {
  const auto display = event.result
                           ? application::DefaultToolResultDisplayProjector()
                                 ->Project(event.call.name, event.result->content,
                                           event.result->error)
                           : application::ToolResultDisplayProjection{};
  ToolTimelinePresentation result{
      .visual = DefaultToolTimelineRendererRegistry().Resolve(event.call.name),
      .status = event.call.status,
      .title = event.call.name,
      .detail = event.result ? event.result->content : event.call.arguments_json,
      .input_detail = {},
      .output_detail = {},
      .auxiliary = {},
      .tool_call_id = event.call.id,
      .todo_items = {},
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
    const bool pending = event.call.status == domain::ToolCallStatus::awaiting_review;
    result.detail = ShellOutput(result.title,
                                event.result ? event.result->content : "", pending);
    break;
  }
  case ToolTimelineVisualKind::read:
    result.title = FirstLabel(input, event.call.name);
    result.expandable = result.failed;
    result.initially_expanded = result.failed;
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
    break;
  case ToolTimelineVisualKind::agent_pipeline:
    PresentPipeline(input, event, result);
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
  return result;
}

AssistantProcessPresentation PresentAssistantProcess(
    const domain::ChatMessage &message, bool live,
    bool process_auto_expand) noexcept {
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
  const auto duration = message.processing_finished_at > message.processing_started_at
                            ? message.processing_finished_at -
                                  message.processing_started_at
                            : 0;
  return AssistantProcessPresentation{
      .visible = !message.timeline.empty() ||
                 !message.reasoning_content.empty() || live,
      .running = live || running_tool,
      .failed = message.error || failed_tool,
      .initially_expanded = live || process_auto_expand,
      .duration_millis = duration,
  };
}

} // namespace linecode::presentation
