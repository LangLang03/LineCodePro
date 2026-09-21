#include "gtest_support.h"
#include <optional>
#include <utility>

#include "application/agent_result_registry.h"
#include "application/tool_result_display_policy.h"
#include "domain/compaction_progress.h"
#include "presentation/chat_timeline_presentation.h"

namespace {

using namespace linecode;

class FixtureAgentResultReader final
    : public application::AgentResultReader {
public:
  application::AgentResultView result;

  [[nodiscard]] std::optional<application::AgentResultView>
  Read(std::string_view agent_id) const override {
    return agent_id == result.agent_id
               ? std::optional<application::AgentResultView>{result}
               : std::nullopt;
  }
};

void PresentsToolPoliciesWithoutRendererConditionals() {
  domain::AssistantToolEvent file{};
  file.call.name = "read_file";
  file.call.arguments_json = R"({"path":"a.cpp"})";
  file.call.status = domain::ToolCallStatus::running;
  auto file_presentation = presentation::PresentToolTimeline(file);
  EXPECT_EXPRESSION(file_presentation.visual ==
         presentation::ToolTimelineVisualKind::read);
  EXPECT_EXPRESSION(file_presentation.title == "a.cpp");
  EXPECT_EXPRESSION(!file_presentation.expandable);
  EXPECT_EXPRESSION(file_presentation.running);
  EXPECT_EXPRESSION(!file_presentation.failed);

  file.call.status = domain::ToolCallStatus::failed;
  file.result = domain::ChatToolResult{
      .call_id = {},
      .name = {},
      .content = "missing",
      .error = true,
      .diff_id = {},
      .review_state = {},
      .review_message = {},
  };
  const auto failed_file = presentation::PresentToolTimeline(file);
  EXPECT_EXPRESSION(failed_file.failed);
  EXPECT_EXPRESSION(!failed_file.expandable);
  EXPECT_EXPRESSION(!failed_file.initially_expanded);

  domain::AssistantToolEvent extension{};
  extension.call.name = "mcpx_dynamic_tool";
  extension.call.status = domain::ToolCallStatus::failed;
  domain::ChatToolResult result{};
  result.content = "real failure";
  result.error = true;
  extension.result = std::move(result);
  auto extension_presentation = presentation::PresentToolTimeline(extension);
  EXPECT_EXPRESSION(extension_presentation.visual ==
         presentation::ToolTimelineVisualKind::generic);
  EXPECT_EXPRESSION(extension_presentation.failed);
  EXPECT_EXPRESSION(extension_presentation.detail == "real failure");
}

void ReproducesLegacyToolFactoriesAndShellContract() {
  const auto &registry = presentation::DefaultToolTimelineRendererRegistry();
  using Visual = presentation::ToolTimelineVisualKind;
  EXPECT_EXPRESSION(registry.Resolve("shell_execute") == Visual::shell);
  EXPECT_EXPRESSION(registry.Resolve("file_read") == Visual::read);
  EXPECT_EXPRESSION(registry.ResolveIcon("file_read") ==
         presentation::ToolTimelineIconKind::file);
  EXPECT_EXPRESSION(registry.ResolveIcon("list_dir") ==
         presentation::ToolTimelineIconKind::folder);
  EXPECT_EXPRESSION(registry.ResolveIcon("web_search") ==
         presentation::ToolTimelineIconKind::search);
  EXPECT_EXPRESSION(registry.Resolve("file_write") == Visual::write);
  EXPECT_EXPRESSION(registry.Resolve("file_edit") == Visual::write);
  EXPECT_EXPRESSION(registry.Resolve("file_delete") == Visual::remove);
  EXPECT_EXPRESSION(registry.Resolve("todo_update") == Visual::todo);
  EXPECT_EXPRESSION(registry.Resolve("agent") == Visual::agent);
  EXPECT_EXPRESSION(registry.Resolve("agentx_fixture") == Visual::agent);
  EXPECT_EXPRESSION(registry.Resolve("agent_pipeline") == Visual::agent_pipeline);
  EXPECT_EXPRESSION(registry.Resolve("phone_click") == Visual::read);
  EXPECT_EXPRESSION(registry.Resolve("mcpx_fixture_tool") == Visual::generic);

  const auto shell_metrics = presentation::ToolTimelineMetrics(Visual::shell);
  EXPECT_EXPRESSION(shell_metrics.header_height == 48.0F);
  EXPECT_EXPRESSION(shell_metrics.icon_slot_width == 24.0F);
  EXPECT_EXPRESSION(shell_metrics.icon_slot_height == 32.0F);
  EXPECT_EXPRESSION(shell_metrics.title_size == 14.0F);
  EXPECT_EXPRESSION(shell_metrics.detail_max_height == 240.0F);
  EXPECT_EXPRESSION(shell_metrics.detail_radius == 12.0F);
  EXPECT_EXPRESSION(shell_metrics.detail_text_size == 13.0F);
  EXPECT_EXPRESSION(shell_metrics.detail_horizontal_padding == 14.0F);
  EXPECT_EXPRESSION(shell_metrics.detail_vertical_padding == 12.0F);

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
  EXPECT_EXPRESSION(displayed.title == "rg TODO");
  EXPECT_EXPRESSION(displayed.detail == "$ rg TODO\n\none\ntwo");
  EXPECT_EXPRESSION(!displayed.initially_expanded);

  // The legacy expansion map is empty until the user clicks the header. A
  // command must therefore remain collapsed while it is executing as well.
  shell.call.status = domain::ToolCallStatus::running;
  shell.result.reset();
  const auto running = presentation::PresentToolTimeline(shell);
  EXPECT_EXPRESSION(running.expandable);
  EXPECT_EXPRESSION(!running.initially_expanded);

  shell.call.status = domain::ToolCallStatus::completed;
  shell.result = domain::ChatToolResult{};
  shell.result->content = std::string(70U * 1024U, 'x');
  const auto folded = presentation::PresentToolTimeline(shell).detail;
  EXPECT_EXPRESSION(folded.starts_with("$ rg TODO\n\n" +
                            std::string(24U * 1024U, 'x')));
  EXPECT_EXPRESSION(folded.contains("[LineCode folded 10240 characters"));
  EXPECT_EXPRESSION(folded.ends_with(std::string(36U * 1024U, 'x')));
}

void PresentsLegacyDeleteTodoAgentAndGenericContent() {
  using Visual = presentation::ToolTimelineVisualKind;
  domain::AssistantToolEvent remove{};
  remove.call.name = "file_delete";
  remove.call.arguments_json =
      R"({"paths":["a.cpp","b.cpp"],"reason":"cleanup"})";
  remove.call.status = domain::ToolCallStatus::awaiting_review;
  const auto deletion = presentation::PresentToolTimeline(remove);
  EXPECT_EXPRESSION(deletion.visual == Visual::remove);
  EXPECT_EXPRESSION(deletion.item_count == 2);
  EXPECT_EXPRESSION(deletion.detail == "cleanup\na.cpp\nb.cpp");
  EXPECT_EXPRESSION(presentation::ToolTimelineMetrics(Visual::remove).detail_max_height ==
         200.0F);

  domain::AssistantToolEvent todo{};
  todo.call.name = "todo_update";
  todo.call.arguments_json =
      R"({"items":[{"content":"one","status":"completed"},{"content":"two","status":"in_progress"}]})";
  const auto todos = presentation::PresentToolTimeline(todo);
  EXPECT_EXPRESSION(todos.visual == Visual::todo);
  EXPECT_EXPRESSION(!todos.expandable);
  EXPECT_EXPRESSION(todos.todo_items.size() == 2);
  EXPECT_EXPRESSION(todos.todo_items.front().state ==
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
  EXPECT_EXPRESSION(agent_card.visual == Visual::agent);
  EXPECT_EXPRESSION(agent_card.title == "Inspect UI");
  EXPECT_EXPRESSION(agent_card.input_detail == "reading");
  EXPECT_EXPRESSION(agent_card.output_detail == "found it");
  EXPECT_EXPRESSION(agent_card.initially_expanded);

  agent.call.status = domain::ToolCallStatus::running;
  agent.result.reset();
  EXPECT_EXPRESSION(!presentation::PresentToolTimeline(agent).visible);
  agent.call.status = domain::ToolCallStatus::completed;
  agent.result = domain::ChatToolResult{};

  domain::AssistantToolEvent pipeline{};
  pipeline.call.name = "agent_pipeline";
  pipeline.call.status = domain::ToolCallStatus::running;
  const auto running_pipeline = presentation::PresentToolTimeline(pipeline);
  EXPECT_EXPRESSION(running_pipeline.visual == Visual::agent_pipeline);
  EXPECT_EXPRESSION(!running_pipeline.visible);

  agent.result->content =
      R"({"linecode_agent_ref":true,"agent_id":"agent-7","status":"done","type":"explore","description":"Inspect UI","preview":"compact preview","tool_call_count":3,"error":false})";
  const auto compact_agent = presentation::PresentToolTimeline(agent);
  EXPECT_EXPRESSION(compact_agent.agent_id == "agent-7");
  EXPECT_EXPRESSION(compact_agent.output_detail == "compact preview");
  EXPECT_EXPRESSION(compact_agent.tool_call_count == 3);

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
  EXPECT_EXPRESSION(generic_card.input_detail == "{\n  \"a\": \"two\",\n  \"z\": 1\n}");
  EXPECT_EXPRESSION(generic_card.output_detail == "ok");
}

void PresentsLiveCompletedAndFailedProcessStates() {
  EXPECT_EXPRESSION(presentation::FormatProcessingDuration(-100) == "0s");
  EXPECT_EXPRESSION(presentation::FormatProcessingDuration(59'999) == "59s");
  EXPECT_EXPRESSION(presentation::FormatProcessingDuration(61'000) == "1m 1s");
  EXPECT_EXPRESSION(presentation::FormatProcessingDuration(3'661'000) == "1h 1m 1s");

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
  EXPECT_EXPRESSION(completed.visible);
  EXPECT_EXPRESSION(!completed.running);
  EXPECT_EXPRESSION(!completed.pending_review);
  EXPECT_EXPRESSION(!completed.failed);
  EXPECT_EXPRESSION(completed.initially_expanded);
  EXPECT_EXPRESSION(completed.duration_millis == 250);

  message.processing_finished_at = 0;
  const auto live =
      presentation::PresentAssistantProcess(message, true, false, 600);
  EXPECT_EXPRESSION(live.running);
  EXPECT_EXPRESSION(!live.pending_review);
  EXPECT_EXPRESSION(!live.initially_expanded);
  EXPECT_EXPRESSION(live.duration_millis == 500);

  message.error = true;
  const auto failed =
      presentation::PresentAssistantProcess(message, false, false);
  EXPECT_EXPRESSION(failed.failed);
  EXPECT_EXPRESSION(!failed.initially_expanded);

  message.timeline.push_back(domain::AssistantToolEvent{
      .turn_index = 0,
      .call = {.id = "review-1",
               .name = "file_write",
               .arguments_json = "{}",
               .status = domain::ToolCallStatus::awaiting_review,
               .created_at_millis = 0,
               .duration_millis = 0,
               .error_message = {}},
      .result = std::nullopt});
  const auto pending =
      presentation::PresentAssistantProcess(message, true, false, 600);
  EXPECT_EXPRESSION(pending.running);
  EXPECT_EXPRESSION(pending.pending_review);
}

void GroupsRetriesAndTheFinalAnswerIntoOneAssistantTurn() {
  domain::ChatMessage user{};
  user.id = 1;
  user.role = domain::MessageRole::user;
  user.content = "question";

  domain::ChatMessage retry_two{};
  retry_two.id = 2;
  retry_two.role = domain::MessageRole::assistant;
  retry_two.content = "Retrying 2/3";
  retry_two.retry_notice = true;
  retry_two.processing_started_at = 100;
  retry_two.processing_finished_at = 100;

  domain::ChatMessage retry_three = retry_two;
  retry_three.id = 3;
  retry_three.content = "Retrying 3/3";
  retry_three.processing_started_at = 200;
  retry_three.processing_finished_at = 200;

  domain::ChatMessage answer{};
  answer.id = 4;
  answer.role = domain::MessageRole::assistant;
  answer.content = "final answer";
  answer.processing_started_at = 300;
  answer.processing_finished_at = 400;

  const std::vector messages{user, retry_two, retry_three, answer};
  const auto rows =
      presentation::BuildConversationPresentationMessages(messages);
  EXPECT_EXPRESSION(rows.size() == 2U);
  EXPECT_EXPRESSION(rows.front() == user);
  const auto &turn = rows.back();
  EXPECT_EXPRESSION(turn.id == answer.id);
  EXPECT_EXPRESSION(turn.content == "final answer");
  EXPECT_EXPRESSION(turn.retry_notice);
  EXPECT_EXPRESSION(turn.processing_started_at == 100);
  EXPECT_EXPRESSION(turn.processing_finished_at == 400);
  EXPECT_EXPRESSION(turn.timeline.size() == 2U);
  EXPECT_EXPRESSION(std::get<domain::AssistantTextEvent>(turn.timeline[0]).text ==
         "Retrying 2/3");
  EXPECT_EXPRESSION(std::get<domain::AssistantTextEvent>(turn.timeline[1]).text ==
         "Retrying 3/3");
}

void KeepsPlainAdjacentAssistantMessagesAsSeparateRows() {
  domain::ChatMessage first{};
  first.id = 1;
  first.role = domain::MessageRole::assistant;
  first.content = "one";
  domain::ChatMessage second = first;
  second.id = 2;
  second.content = "two";

  const std::vector messages{first, second};
  const auto rows =
      presentation::BuildConversationPresentationMessages(messages);
  EXPECT_EXPRESSION(rows == messages);
}

void KeepsCompactionInsideTheActiveAssistantTurn() {
  domain::ChatMessage user{};
  user.id = 1;
  user.role = domain::MessageRole::user;
  user.content = "question";

  domain::ChatMessage retry{};
  retry.id = 2;
  retry.role = domain::MessageRole::assistant;
  retry.content = "Retrying 2/3";
  retry.retry_notice = true;
  retry.processing_started_at = 100;

  domain::ChatMessage compact{};
  compact.id = 3;
  compact.role = domain::MessageRole::assistant;
  compact.compact_status = domain::compact_status_done;
  compact.exclude_from_context = true;
  compact.processing_started_at = 100;
  compact.processing_finished_at = 200;

  domain::ChatMessage answer{};
  answer.id = 4;
  answer.role = domain::MessageRole::assistant;
  answer.content = "final answer";
  answer.processing_started_at = 100;
  answer.processing_finished_at = 300;

  const std::vector messages{user, retry, compact, answer};
  const auto rows =
      presentation::BuildConversationPresentationMessages(messages);
  EXPECT_EXPRESSION(rows.size() == 2U);
  const auto &turn = rows.back();
  EXPECT_EXPRESSION(turn.id == answer.id);
  EXPECT_EXPRESSION(turn.content == answer.content);
  EXPECT_EXPRESSION(turn.timeline.size() == 2U);
  EXPECT_EXPRESSION(std::holds_alternative<domain::AssistantTextEvent>(
      turn.timeline.front()));
  const auto *compact_event =
      std::get_if<domain::AssistantCompactEvent>(&turn.timeline.back());
  EXPECT_EXPRESSION(compact_event != nullptr);
  EXPECT_EXPRESSION(compact_event->status == domain::compact_status_done);
}

void StartsANewTurnWhenCompactionFollowsACompletedAnswer() {
  domain::ChatMessage answer{};
  answer.id = 1;
  answer.role = domain::MessageRole::assistant;
  answer.content = "already complete";
  answer.processing_started_at = 100;
  answer.processing_finished_at = 200;

  domain::ChatMessage compact{};
  compact.id = 2;
  compact.role = domain::MessageRole::assistant;
  compact.compact_status = domain::compact_status_running;
  compact.streaming = true;
  compact.exclude_from_context = true;
  compact.processing_started_at = 300;

  const std::vector messages{answer, compact};
  const auto rows =
      presentation::BuildConversationPresentationMessages(messages);
  EXPECT_EXPRESSION(rows.size() == 2U);
  EXPECT_EXPRESSION(rows.front() == answer);
  EXPECT_EXPRESSION(rows.back().id == compact.id);
  EXPECT_EXPRESSION(rows.back().timeline.size() == 1U);
  EXPECT_EXPRESSION(std::holds_alternative<domain::AssistantCompactEvent>(
      rows.back().timeline.front()));
}

void ResolvesPersistedAgentSnapshotsIntoNestedTimelineCards() {
  FixtureAgentResultReader reader;
  reader.result.agent_id = "agent-7";
  reader.result.status = "done";
  reader.result.type = "explore";
  reader.result.description = "Inspect UI";
  reader.result.progress = domain::AgentExecutionSnapshot{
      .id = "agent-7",
      .type = "explore",
      .description = "Inspect UI",
      .dependencies = {},
      .status = domain::AgentExecutionStatus::done,
      .thinking = "reading",
      .output = "found it",
      .tool_calls = {domain::AgentToolCallSnapshot{
          .id = "nested-1",
          .name = "read_file",
          .arguments_json = R"({"path":"src/app.cpp"})",
          .status = domain::AgentToolCallStatus::completed,
          .status_history = {domain::AgentToolCallStatus::requested,
                             domain::AgentToolCallStatus::running,
                             domain::AgentToolCallStatus::completed},
          .result = domain::AgentToolCallResultSnapshot{
              .content = "contents", .error = false, .diff_id = {}}}},
  };

  domain::AssistantToolEvent event{};
  event.call.id = "outer-1";
  event.call.name = "agent";
  event.call.status = domain::ToolCallStatus::completed;
  event.result = domain::ChatToolResult{
      .call_id = "outer-1",
      .name = "agent",
      .content = R"({"linecode_agent_ref":true,"agent_id":"agent-7"})",
      .error = false,
      .diff_id = {},
      .review_state = {},
      .review_message = {}};

  const auto card = presentation::PresentToolTimeline(event, &reader);
  EXPECT_EXPRESSION(card.agent_runs.size() == 1U);
  EXPECT_EXPRESSION(card.agent_runs.front().thinking == "reading");
  EXPECT_EXPRESSION(card.agent_runs.front().output == "found it");
  EXPECT_EXPRESSION(card.agent_runs.front().tool_calls.size() == 1U);
  const auto nested = presentation::PresentToolTimeline(
      card.agent_runs.front().tool_calls.front());
  EXPECT_EXPRESSION(nested.visual == presentation::ToolTimelineVisualKind::read);
  EXPECT_EXPRESSION(nested.title == "src/app.cpp");
}

void ResolvesPersistedPipelineAgentsWithDependencies() {
  FixtureAgentResultReader reader;
  reader.result.agent_id = "pipeline-3";
  reader.result.status = "done";
  reader.result.progress = domain::AgentPipelineSnapshot{
      .status = domain::AgentExecutionStatus::done,
      .summary = "all done",
      .agents = {
          domain::AgentExecutionSnapshot{
              .id = "research",
              .type = "explore",
              .description = "Research",
              .dependencies = {},
              .status = domain::AgentExecutionStatus::done,
              .thinking = {},
              .output = "facts",
              .tool_calls = {},
              .error = false},
          domain::AgentExecutionSnapshot{
              .id = "writer",
              .type = "code",
              .description = "Write",
              .dependencies = {"research"},
              .status = domain::AgentExecutionStatus::error,
              .thinking = "blocked",
              .output = {},
              .tool_calls = {},
              .error = true}},
      .error = true,
  };

  domain::AssistantToolEvent event{};
  event.call.name = "agent_pipeline";
  event.call.status = domain::ToolCallStatus::completed;
  event.result = domain::ChatToolResult{
      .call_id = {},
      .name = {},
      .content =
          R"({"linecode_agent_ref":true,"agent_id":"pipeline-3"})",
      .error = false,
      .diff_id = {},
      .review_state = {},
      .review_message = {}};

  const auto card = presentation::PresentToolTimeline(event, &reader);
  EXPECT_EXPRESSION(card.agent_runs.size() == 2U);
  EXPECT_EXPRESSION(card.completed_count == 1);
  EXPECT_EXPRESSION(card.failed_count == 1);
  EXPECT_EXPRESSION(card.output_detail == "all done");
  EXPECT_EXPRESSION(card.agent_runs.back().dependencies ==
         std::vector<std::string>{"research"});
  EXPECT_EXPRESSION(card.agent_runs.back().failed);
}

void ProjectsImageGenerationForDisplayAndModelSeparately() {
  const std::string raw =
      R"json({"linecode_image_generation":true,"display_markdown":"![image](data:image/png;base64,AAAA)","model_content":"Generated image for: fixture"})json";
  const auto projector =
      application::DefaultToolResultDisplayProjector();
  const auto projected = projector->Project("image_generation", raw, false);
  EXPECT_EXPRESSION(projected.display_markdown ==
         "![image](data:image/png;base64,AAAA)");
  EXPECT_EXPRESSION(projected.model_content == "Generated image for: fixture");
  EXPECT_EXPRESSION(!projected.model_content.contains("data:image/"));
  EXPECT_EXPRESSION(projected.hide_success_card);

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
  EXPECT_EXPRESSION(!presentation::PresentToolTimeline(image).visible);
  image.call.status = domain::ToolCallStatus::failed;
  image.result->error = true;
  EXPECT_EXPRESSION(presentation::PresentToolTimeline(image).visible);

  const auto fallback = projector->Project(
      "other_tool", "![unsafe](data:image/png;base64,BBBB)", false);
  EXPECT_EXPRESSION(!fallback.model_content.contains("data:image/"));
  EXPECT_EXPRESSION(fallback.model_content.contains("linecode-inline-image"));
  EXPECT_EXPRESSION(!fallback.hide_success_card);
}

} // namespace

TEST(chat_timeline_presentation_tests, LegacySuite) {
  PresentsToolPoliciesWithoutRendererConditionals();
  ReproducesLegacyToolFactoriesAndShellContract();
  PresentsLegacyDeleteTodoAgentAndGenericContent();
  PresentsLiveCompletedAndFailedProcessStates();
  GroupsRetriesAndTheFinalAnswerIntoOneAssistantTurn();
  KeepsPlainAdjacentAssistantMessagesAsSeparateRows();
  KeepsCompactionInsideTheActiveAssistantTurn();
  StartsANewTurnWhenCompactionFollowsACompletedAnswer();
  ResolvesPersistedAgentSnapshotsIntoNestedTimelineCards();
  ResolvesPersistedPipelineAgentsWithDependencies();
  ProjectsImageGenerationForDisplayAndModelSeparately();
}
