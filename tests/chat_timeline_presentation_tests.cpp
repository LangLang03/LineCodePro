#include <cassert>
#include <utility>

#include "application/tool_result_display_policy.h"
#include "presentation/chat_timeline_presentation.h"

namespace {

using namespace linecode;

void PresentsToolPoliciesWithoutRendererConditionals() {
  domain::AssistantToolEvent file{};
  file.call.name = "read_file";
  file.call.arguments_json = R"({"path":"a.cpp"})";
  file.call.status = domain::ToolCallStatus::running;
  auto file_presentation = presentation::PresentToolTimeline(file);
  assert(file_presentation.visual ==
         presentation::ToolTimelineVisualKind::read);
  assert(file_presentation.title == "a.cpp");
  assert(!file_presentation.expandable);
  assert(file_presentation.running);
  assert(!file_presentation.failed);

  domain::AssistantToolEvent extension{};
  extension.call.name = "mcpx_dynamic_tool";
  extension.call.status = domain::ToolCallStatus::failed;
  domain::ChatToolResult result{};
  result.content = "real failure";
  result.error = true;
  extension.result = std::move(result);
  auto extension_presentation = presentation::PresentToolTimeline(extension);
  assert(extension_presentation.visual ==
         presentation::ToolTimelineVisualKind::generic);
  assert(extension_presentation.failed);
  assert(extension_presentation.detail == "real failure");
}

void ReproducesLegacyToolFactoriesAndShellContract() {
  const auto &registry = presentation::DefaultToolTimelineRendererRegistry();
  using Visual = presentation::ToolTimelineVisualKind;
  assert(registry.Resolve("shell_execute") == Visual::shell);
  assert(registry.Resolve("file_read") == Visual::read);
  assert(registry.Resolve("file_write") == Visual::write);
  assert(registry.Resolve("file_edit") == Visual::write);
  assert(registry.Resolve("file_delete") == Visual::remove);
  assert(registry.Resolve("todo_update") == Visual::todo);
  assert(registry.Resolve("agent") == Visual::agent);
  assert(registry.Resolve("agentx_fixture") == Visual::agent);
  assert(registry.Resolve("agent_pipeline") == Visual::agent_pipeline);
  assert(registry.Resolve("phone_click") == Visual::read);
  assert(registry.Resolve("mcpx_fixture_tool") == Visual::generic);

  const auto shell_metrics = presentation::ToolTimelineMetrics(Visual::shell);
  assert(shell_metrics.header_height == 48.0F);
  assert(shell_metrics.icon_slot_width == 24.0F);
  assert(shell_metrics.icon_slot_height == 32.0F);
  assert(shell_metrics.title_size == 14.0F);
  assert(shell_metrics.detail_max_height == 240.0F);
  assert(shell_metrics.detail_radius == 12.0F);
  assert(shell_metrics.detail_text_size == 13.0F);
  assert(shell_metrics.detail_horizontal_padding == 14.0F);
  assert(shell_metrics.detail_vertical_padding == 12.0F);

  domain::AssistantToolEvent shell{};
  shell.call.name = "shell_execute";
  shell.call.arguments_json = R"({"command":"rg TODO"})";
  shell.call.status = domain::ToolCallStatus::completed;
  shell.result = domain::ChatToolResult{
      .call_id = {},
      .name = {},
      .content = "one\ntwo",
      .error = false,
      .diff_id = {},
      .review_state = {},
      .review_message = {},
  };
  const auto displayed = presentation::PresentToolTimeline(shell);
  assert(displayed.title == "rg TODO");
  assert(displayed.detail == "$ rg TODO\n\none\ntwo");
  assert(!displayed.initially_expanded);

  shell.result->content = std::string(70U * 1024U, 'x');
  const auto folded = presentation::PresentToolTimeline(shell).detail;
  assert(folded.starts_with("$ rg TODO\n\n" +
                            std::string(24U * 1024U, 'x')));
  assert(folded.contains("[LineCode folded 10240 characters"));
  assert(folded.ends_with(std::string(36U * 1024U, 'x')));
}

void PresentsLegacyDeleteTodoAgentAndGenericContent() {
  using Visual = presentation::ToolTimelineVisualKind;
  domain::AssistantToolEvent remove{};
  remove.call.name = "file_delete";
  remove.call.arguments_json =
      R"({"paths":["a.cpp","b.cpp"],"reason":"cleanup"})";
  remove.call.status = domain::ToolCallStatus::awaiting_review;
  const auto deletion = presentation::PresentToolTimeline(remove);
  assert(deletion.visual == Visual::remove);
  assert(deletion.item_count == 2);
  assert(deletion.detail == "cleanup\na.cpp\nb.cpp");
  assert(presentation::ToolTimelineMetrics(Visual::remove).detail_max_height ==
         200.0F);

  domain::AssistantToolEvent todo{};
  todo.call.name = "todo_update";
  todo.call.arguments_json =
      R"({"items":[{"content":"one","status":"completed"},{"content":"two","status":"in_progress"}]})";
  const auto todos = presentation::PresentToolTimeline(todo);
  assert(todos.visual == Visual::todo);
  assert(!todos.expandable);
  assert(todos.todo_items.size() == 2);
  assert(todos.todo_items.front().state ==
         presentation::ToolTimelineTodoItem::State::completed);

  domain::AssistantToolEvent agent{};
  agent.call.name = "agent";
  agent.call.arguments_json = R"({"description":"Inspect UI","type":"explore"})";
  agent.call.status = domain::ToolCallStatus::running;
  agent.result = domain::ChatToolResult{
      .call_id = {},
      .name = {},
      .content = R"({"linecode_agent_progress":true,"description":"Inspect UI","thinking":"reading","output":"found it"})",
      .error = false,
      .diff_id = {},
      .review_state = {},
      .review_message = {},
  };
  const auto agent_card = presentation::PresentToolTimeline(agent);
  assert(agent_card.visual == Visual::agent);
  assert(agent_card.title == "Inspect UI");
  assert(agent_card.input_detail == "reading");
  assert(agent_card.output_detail == "found it");
  assert(agent_card.initially_expanded);

  domain::AssistantToolEvent generic{};
  generic.call.name = "mcpx_fixture";
  generic.call.arguments_json = R"({"z":1,"a":"two"})";
  generic.call.status = domain::ToolCallStatus::completed;
  generic.result = domain::ChatToolResult{
      .call_id = {},
      .name = {},
      .content = "ok",
      .error = false,
      .diff_id = {},
      .review_state = {},
      .review_message = {},
  };
  const auto generic_card = presentation::PresentToolTimeline(generic);
  assert(generic_card.input_detail == "{\n  \"a\": \"two\",\n  \"z\": 1\n}");
  assert(generic_card.output_detail == "ok");
}

void PresentsLiveCompletedAndFailedProcessStates() {
  domain::ChatMessage message{};
  message.processing_started_at = 100;
  message.processing_finished_at = 350;
  message.timeline.push_back(domain::AssistantReasoningEvent{
      .turn_index = 0,
      .text = "reason",
      .kind = domain::ReasoningKind::thinking,
      .starts_new_segment = false});

  const auto completed =
      presentation::PresentAssistantProcess(message, false, true);
  assert(completed.visible);
  assert(!completed.running);
  assert(!completed.failed);
  assert(completed.initially_expanded);
  assert(completed.duration_millis == 250);

  const auto live =
      presentation::PresentAssistantProcess(message, true, false);
  assert(live.running);
  assert(live.initially_expanded);

  message.error = true;
  const auto failed =
      presentation::PresentAssistantProcess(message, false, false);
  assert(failed.failed);
  assert(!failed.initially_expanded);
}

void ProjectsImageGenerationForDisplayAndModelSeparately() {
  const std::string raw =
      R"json({"linecode_image_generation":true,"display_markdown":"![image](data:image/png;base64,AAAA)","model_content":"Generated image for: fixture"})json";
  const auto projector =
      application::DefaultToolResultDisplayProjector();
  const auto projected = projector->Project("image_generation", raw, false);
  assert(projected.display_markdown ==
         "![image](data:image/png;base64,AAAA)");
  assert(projected.model_content == "Generated image for: fixture");
  assert(!projected.model_content.contains("data:image/"));
  assert(projected.hide_success_card);

  domain::AssistantToolEvent image{};
  image.call.name = "image_generation";
  image.call.status = domain::ToolCallStatus::completed;
  image.result = domain::ChatToolResult{
      .call_id = "image-1",
      .name = "image_generation",
      .content = raw,
      .error = false,
      .diff_id = {},
      .review_state = {},
      .review_message = {},
  };
  assert(!presentation::PresentToolTimeline(image).visible);
  image.call.status = domain::ToolCallStatus::failed;
  image.result->error = true;
  assert(presentation::PresentToolTimeline(image).visible);

  const auto fallback = projector->Project(
      "other_tool", "![unsafe](data:image/png;base64,BBBB)", false);
  assert(!fallback.model_content.contains("data:image/"));
  assert(fallback.model_content.contains("linecode-inline-image"));
  assert(!fallback.hide_success_card);
}

} // namespace

int main() {
  PresentsToolPoliciesWithoutRendererConditionals();
  ReproducesLegacyToolFactoriesAndShellContract();
  PresentsLegacyDeleteTodoAgentAndGenericContent();
  PresentsLiveCompletedAndFailedProcessStates();
  ProjectsImageGenerationForDisplayAndModelSeparately();
}
