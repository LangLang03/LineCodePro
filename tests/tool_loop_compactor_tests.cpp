// Contract tests for the mid-loop compactor that plugs the tool loop into the
// ported context-compaction service.

#include "gtest_support.h"
#include <cstdint>
#include <iostream>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <map>
#include <vector>

#include "application/chat_session.h"
#include "application/context_compaction.h"
#include "infrastructure/in_memory_conversation_store.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/model_store.h"
#include "application/prompt_template_repository.h"
#include "application/ports/settings_store.h"
#include "application/tool_loop_compactor.h"
#include "domain/app_state.h"
#include "domain/model_config.h"

#include <huxerui/testing/ui_test.h>

namespace {

using namespace linecode;

namespace {

// Returns one fixed summary so the compactor's rebuild path is exercised
// without a real model.
class StubGateway final : public application::CompletionGateway {
public:
  [[nodiscard]] huxerui::Task<
      std::expected<application::CompletionResponse, application::CompletionError>>
  Complete(application::CompletionRequest request,
           application::CompletionObserver observer) override {
    static_cast<void>(observer);
    requests.push_back(request);
    application::CompletionResponse response;
    response.text = kSummary;
    co_return response;
  }

  static constexpr std::string_view kSummary = "## Summary\nearlier work";
  std::vector<application::CompletionRequest> requests;
};

class MemorySettings final : public application::AsyncSettingsStore {
public:
  [[nodiscard]] huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string, std::string fallback) override {
    co_return std::move(fallback);
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string, bool fallback) override {
    co_return fallback;
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string, std::int64_t fallback) override {
    co_return fallback;
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  SetString(std::string, std::string) override {
    co_return application::SettingsResult<void>{};
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  SetBoolean(std::string, bool) override {
    co_return application::SettingsResult<void>{};
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  SetInteger(std::string, std::int64_t) override {
    co_return application::SettingsResult<void>{};
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  Remove(std::string) override {
    co_return application::SettingsResult<void>{};
  }
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  ClearLineCodeSettings() override {
    co_return application::SettingsResult<void>{};
  }
  [[nodiscard]] huxerui::Task<
      application::SettingsResult<std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return std::map<std::string, std::string, std::less<>>{};
  }
};

class StubModels final : public application::ModelStore {
public:
  [[nodiscard]] huxerui::Task<
      std::expected<std::vector<domain::ModelConfig>, application::ModelStoreError>>
  List() override {
    co_return std::vector<domain::ModelConfig>{model_};
  }
  [[nodiscard]] huxerui::Task<std::expected<std::optional<domain::ModelConfig>,
                                            application::ModelStoreError>>
  Find(std::string id) override {
    if (id != model_.id)
      co_return std::optional<domain::ModelConfig>{};
    co_return std::optional<domain::ModelConfig>{model_};
  }
  [[nodiscard]] huxerui::Task<
      std::expected<domain::ModelConfig, application::ModelStoreError>>
  Save(domain::ModelConfig model) override {
    co_return model;
  }
  [[nodiscard]] huxerui::Task<std::expected<void, application::ModelStoreError>>
  Delete(std::vector<std::string>) override {
    co_return std::expected<void, application::ModelStoreError>{};
  }
  [[nodiscard]] huxerui::Task<std::expected<void, application::ModelStoreError>>
  Select(std::string) override {
    co_return std::expected<void, application::ModelStoreError>{};
  }
  [[nodiscard]] huxerui::Task<
      std::expected<std::string, application::ModelStoreError>>
  SelectedId() override {
    co_return model_.id;
  }

  domain::ModelConfig model_{};
};

domain::ModelConfig SmallWindowModel() {
  domain::ModelConfig model;
  model.id = "m";
  model.model_id = "m";
  model.context_size = 200; // Small so a few messages cross the 80% trigger.
  return model;
}

application::CompletionMessage SystemMessage() {
  return application::CompletionMessage{.role = application::CompletionRole::system,
                                        .content = "system prompt"};
}

application::CompletionMessage UserMessage(std::string text) {
  return application::CompletionMessage{.role = application::CompletionRole::user,
                                        .content = std::move(text)};
}

application::CompletionMessage AssistantWithTool(std::string id) {
  application::CompletionMessage message;
  message.role = application::CompletionRole::assistant;
  message.content = "thinking";
  message.tool_calls.push_back(application::CompletionToolCall{
      .id = std::move(id), .name = "file_read", .arguments_json = "{}"});
  return message;
}

application::CompletionMessage ToolResult(std::string id, std::string content) {
  application::CompletionMessage message;
  message.role = application::CompletionRole::tool;
  application::CompletionToolResult result;
  result.call_id = std::move(id);
  result.name = "file_read";
  result.content = std::move(content);
  message.tool_result = std::move(result);
  return message;
}

struct Harness final {
  std::shared_ptr<StubGateway> gateway = std::make_shared<StubGateway>();
  std::shared_ptr<StubModels> models = std::make_shared<StubModels>();
  std::shared_ptr<application::PromptTemplateRepository> templates;
  std::shared_ptr<application::ContextCompactionService> compaction;
  std::shared_ptr<application::ToolLoopCompactor> compactor;
  std::shared_ptr<application::ChatSession> session;

  explicit Harness(domain::ModelConfig model, bool with_session = false) {
    models->model_ = std::move(model);
    templates = std::make_shared<application::PromptTemplateRepository>(
        std::make_shared<MemorySettings>());
    compaction = std::make_shared<application::ContextCompactionService>(
        gateway, templates, models);
    if (with_session) {
      session = std::make_shared<application::ChatSession>(
          std::make_unique<infrastructure::InMemoryConversationStore>());
    }
    compactor = std::make_shared<application::ToolLoopCompactor>(
        compaction, models, true, session);
  }
};


} // namespace

// The compactor is a coroutine, so the probe drives it inside a HuxerUI
// application exactly like the other async tests in this suite.
struct Probe final {
  // What the provider reported for the finished turn; 0 means it reported
  // nothing and the trigger falls back to the local estimate.
  std::int64_t observed_input_tokens{};
  std::shared_ptr<Harness> harness;
  application::CompletionRequest request;
  application::CompletionRequest result;
  bool run{};
  bool done{};
};

std::shared_ptr<Probe> probe;

huxerui::View CompactionProbe() {
  const auto current = probe;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    if (!current->run)
      return;
    tasks.Launch([current]() -> huxerui::Task<void> {
      current->result =
          co_await current->harness->compactor->CompactIfNeeded(
              std::move(current->request), current->observed_input_tokens);
      current->done = true;
    });
  });
  return huxerui::Text("tool-loop-compactor-probe");
}

application::CompletionRequest Run(Probe &target) {
  probe = std::make_shared<Probe>(target);
  const huxerui::Application application(CompactionProbe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  for (std::size_t frame = 0; frame < 20'000U && !probe->done; ++frame)
    ui.Pump(std::chrono::milliseconds{1});
  EXPECT_EXPRESSION(probe->done);
  const auto result = probe->result;
  probe.reset();
  return result;
}

void LongToolLoopCompactsAndKeepsTheInFlightGroup() {
  Probe target;
  target.harness = std::make_shared<Harness>(SmallWindowModel());
  target.run = true;
  target.request.model = target.harness->models->model_;
  target.request.messages = {SystemMessage()};
  // Enough history that the estimate crosses 80% of a 200-token window.
  for (int index = 0; index < 12; ++index) {
    target.request.messages.push_back(
        UserMessage(std::string(60, 'a') + std::to_string(index)));
    target.request.messages.push_back(
        AssistantWithTool("call" + std::to_string(index)));
    target.request.messages.push_back(
        ToolResult("call" + std::to_string(index), std::string(60, 'b')));
  }

  const auto result = Run(target);

  // The gateway saw exactly one summary request.
  EXPECT_EXPRESSION(target.harness->gateway->requests.size() == 1);
  // The system prompt survives, one summary is inserted, and the in-flight
  // assistant + tool group is kept verbatim.
  EXPECT_EXPRESSION(result.messages.front().role == application::CompletionRole::system);
  // The summary is rendered through the prompt template, so match on the body
  // the gateway returned rather than the whole message.
  bool has_summary = false;
  for (const auto &message : result.messages)
    has_summary = has_summary ||
                  message.content.find("earlier work") != std::string::npos;
  EXPECT_EXPRESSION(has_summary);
  bool has_history = false;
  for (const auto &message : result.messages) {
    if (message.role == application::CompletionRole::user &&
        message.content.find("earlier work") == std::string::npos)
      has_history = true;
  }
  // The summarized history is gone, not merely reordered.
  EXPECT_EXPRESSION(!has_history);
  EXPECT_EXPRESSION(result.messages.back().role == application::CompletionRole::tool);
  // Far fewer messages than the original 37.
  EXPECT_EXPRESSION(result.messages.size() < 8);
}

void ShortLoopIsLeftAlone() {
  Probe target;
  target.harness = std::make_shared<Harness>(SmallWindowModel());
  target.run = true;
  target.request.model = target.harness->models->model_;
  target.request.messages = {SystemMessage(), UserMessage("hi"),
                             AssistantWithTool("c1"), ToolResult("c1", "ok")};
  const auto before = target.request.messages.size();

  const auto result = Run(target);

  EXPECT_EXPRESSION(target.harness->gateway->requests.empty());
  EXPECT_EXPRESSION(result.messages.size() == before);
}

void CompactorWithoutACompactionServiceIsANoOp() {
  Probe target;
  target.harness = std::make_shared<Harness>(SmallWindowModel());
  target.harness->compactor =
      std::make_shared<application::ToolLoopCompactor>(nullptr, nullptr, true);
  target.run = true;
  target.request.messages = {UserMessage("a"), UserMessage("b")};

  const auto result = Run(target);

  EXPECT_EXPRESSION(result.messages.size() == 2);
}

} // namespace

// A mid-loop compaction has to leave the conversation changed. Rewriting only
// the request in flight would put the summary nowhere, so the next turn would
// rebuild from the untouched history and compact it all over again.
void MidLoopCompactionWritesBackToTheConversation() {
  Probe target;
  target.harness = std::make_shared<Harness>(SmallWindowModel(), true);
  target.run = true;
  target.request.model = target.harness->models->model_;
  target.request.messages = {SystemMessage()};

  // Seed the conversation and mirror it into the request the way
  // `BuildMessages` does, carrying each row's id as provenance.
  for (int index = 0; index < 12; ++index) {
    static_cast<void>(target.harness->session->Send(
        std::string(60, 'a') + std::to_string(index)));
  }
  const auto rows = std::vector<domain::ChatMessage>{
      target.harness->session->Messages().begin(),
      target.harness->session->Messages().end()};
  for (const auto &row : rows) {
    target.request.messages.push_back(
        application::CompletionMessage{.role = application::CompletionRole::user,
                                       .content = row.content,
                                       .source_id = row.id});
  }
  // The in-flight group: no session row behind it.
  target.request.messages.push_back(application::CompletionMessage::Assistant(
      "working", {application::CompletionToolCall{
                     .id = "c1", .name = "list_dir", .arguments_json = "{}"}}));
  target.request.messages.push_back(application::CompletionMessage::Tool(
      application::CompletionToolResult{
          .call_id = "c1", .name = "list_dir", .content = "[]", .error = false}));

  const auto before = target.harness->session->Messages().size();
  static_cast<void>(Run(target));

  const auto after = std::vector<domain::ChatMessage>{
      target.harness->session->Messages().begin(),
      target.harness->session->Messages().end()};
  // Summarized rows leave the context, the summary joins it, and the running
  // and done progress blocks were appended for the transcript.
  const auto hidden =
      std::count_if(after.begin(), after.end(),
                    [](const domain::ChatMessage &message) {
                      return message.exclude_from_context;
                    });
  EXPECT_EXPRESSION(hidden > 0);
  EXPECT_EXPRESSION(after.size() > before);
  const auto summaries = std::count_if(
      after.begin(), after.end(), [](const domain::ChatMessage &message) {
        return message.hidden && !message.exclude_from_context;
      });
  EXPECT_EXPRESSION(summaries == 1);
  const auto blocks = std::count_if(
      after.begin(), after.end(), [](const domain::ChatMessage &message) {
        return message.compact_status == std::string{"done"};
      });
  EXPECT_EXPRESSION(blocks == 1);
}

// The trigger has to measure what the provider reported.
void MidLoopTriggerUsesTheReportedCount() {
  Probe without;
  without.harness = std::make_shared<Harness>(SmallWindowModel());
  without.run = true;
  without.request.model = without.harness->models->model_;
  // Deliberately short: the local estimate (characters / 4) stays far below
  // the threshold, so only a reported count can cross it. Enough rows that a
  // compactable base remains once the preserved tail is set aside.
  const auto short_history = [] {
    std::vector<application::CompletionMessage> messages{SystemMessage()};
    for (int index = 0; index < 6; ++index)
      messages.push_back(UserMessage("s" + std::to_string(index)));
    return messages;
  };
  without.request.messages = short_history();
  without.observed_input_tokens = 0;
  const auto untouched = Run(without);

  Probe reported;
  reported.harness = std::make_shared<Harness>(SmallWindowModel());
  reported.run = true;
  reported.request.model = reported.harness->models->model_;
  // A short history, so only the reported count can cross the threshold.
  reported.request.messages = short_history();
  reported.observed_input_tokens = 190;
  const auto compacted = Run(reported);

  // The local estimate is nowhere near the threshold, so nothing changed.
  EXPECT_EXPRESSION(untouched.messages.size() == without.request.messages.size());
  EXPECT_EXPRESSION(compacted.messages.size() < reported.request.messages.size());
}

TEST(tool_loop_compactor_tests, LegacySuite) {
  LongToolLoopCompactsAndKeepsTheInFlightGroup();
  ShortLoopIsLeftAlone();
  CompactorWithoutACompactionServiceIsANoOp();
  MidLoopCompactionWritesBackToTheConversation();
  MidLoopTriggerUsesTheReportedCount();
  std::cout << "tool_loop_compactor_tests passed\n";
  return;
}
