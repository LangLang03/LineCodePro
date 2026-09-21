// Contract tests for the ported context compaction service
// (`cn.lineai.context.ContextCompactionService`). Threshold, split, transcript
// and formatting behaviour is checked directly; every model interaction goes
// through an in-memory fake completion gateway, so no network or real model is
// involved.

#include "gtest_support.h"
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <functional>
#include <iostream>
#include <map>
#include <memory>
#include <optional>
#include <stdexcept>
#include <stop_token>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/context_compaction.h"
#include "application/ports/settings_store.h"
#include "domain/context_usage.h"

namespace {

using namespace linecode;

using application::CompletionError;
using application::CompletionErrorCode;
using application::CompletionObserver;
using application::CompletionRequest;
using application::CompletionResponse;
using application::CompletionRole;
using application::ContextCompactionError;
using application::ContextCompactionErrorCode;
using application::ContextCompactionResult;
using application::ContextCompactionService;
using application::ModelStoreError;
using application::PromptTemplateRepository;
using application::SoftCompactSplit;
using domain::AssistantToolEvent;
using domain::ChatMessage;
using domain::ChatToolCall;
using domain::ChatToolResult;
using domain::MessageRole;
using domain::ModelConfig;
using domain::ModelProtocol;

// ---------------------------------------------------------------------------
// Message fixtures
// ---------------------------------------------------------------------------

ChatMessage UserMessage(std::string content) {
  ChatMessage message;
  message.role = MessageRole::user;
  message.content = std::move(content);
  return message;
}

ChatMessage AssistantMessage(std::string content) {
  ChatMessage message;
  message.role = MessageRole::assistant;
  message.content = std::move(content);
  return message;
}

// A user message whose local estimate is exactly `tokens`
// (`message_overhead_tokens` = 8 plus `ceil(characters / 4)`).
ChatMessage MessageWithTokens(const int tokens, std::string prefix = {}) {
  EXPECT_EXPRESSION(tokens >= 9);
  ChatMessage message;
  message.role = MessageRole::user;
  message.content = std::move(prefix);
  message.content += std::string(
      static_cast<std::size_t>(tokens - 8) * domain::characters_per_token, 'x');
  return message;
}

std::vector<ChatMessage> RepeatMessages(const std::size_t count,
                                        const int tokens_each) {
  std::vector<ChatMessage> messages;
  messages.reserve(count);
  for (std::size_t index = 0; index < count; ++index) {
    messages.push_back(
        MessageWithTokens(tokens_each, "m" + std::to_string(index) + ":"));
  }
  return messages;
}

ChatMessage AssistantWithTool(std::string content, std::string call_id,
                              std::string name, std::string arguments_json,
                              std::optional<std::string> result_content,
                              std::string reasoning = {}) {
  ChatMessage message;
  message.role = MessageRole::assistant;
  message.content = std::move(content);
  message.reasoning_content = std::move(reasoning);
  ChatToolCall call;
  call.id = std::move(call_id);
  call.name = std::move(name);
  call.arguments_json = std::move(arguments_json);
  AssistantToolEvent event;
  event.call = std::move(call);
  if (result_content.has_value()) {
    ChatToolResult result;
    result.call_id = event.call.id;
    result.name = event.call.name;
    result.content = std::move(*result_content);
    event.result = std::move(result);
  }
  message.timeline.push_back(std::move(event));
  return message;
}

ModelConfig ProtocolModel(const ModelProtocol protocol,
                          const bool compression_enabled) {
  ModelConfig model;
  model.id = "model-record";
  model.name = "Fixture";
  model.protocol = protocol;
  model.provider_label = "Fixture";
  model.base_url = "https://example.invalid/v1";
  model.api_key = "fixture-key";
  model.model_id = "selected-model";
  model.compression_model_enabled = compression_enabled;
  return model;
}

// ---------------------------------------------------------------------------
// Pure threshold, split, selection and transcript tests
// ---------------------------------------------------------------------------

void HardTriggerThresholdsMatchLegacy() {
  // 799 estimated tokens against a 1000-token window is 79.9% and stays below
  // the 80% hard trigger; 800 tokens is exactly at it.
  const std::vector<ChatMessage> below{MessageWithTokens(799)};
  const std::vector<ChatMessage> at{MessageWithTokens(800)};
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact(below, 1000, true));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(at, 1000, true));

  // Observed server input tokens win over the local estimate.
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact(below, 1000, true, 799));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(below, 1000, true, 800));
  // Zero observed tokens mean "not observed" and fall back to the estimate.
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact(below, 1000, true, 0));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(at, 1000, true, 0));

  // Degenerate inputs: no messages never triggers, and a zero window floors to
  // one token instead of dividing by zero.
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact({}, 1000, true));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact({UserMessage("x")}, 0, true));

  // Reasoning is only counted when the caller asks for it.
  ChatMessage reasoning_only;
  reasoning_only.role = MessageRole::assistant;
  reasoning_only.reasoning_content = std::string(400U, 'r');
  const std::vector<ChatMessage> reasoning{reasoning_only};
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(reasoning, 100, true));
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact(reasoning, 100, false));

  // The model overload resolves the window through the ported parser.
  auto model = ProtocolModel(ModelProtocol::openai_compatible, false);
  model.context_size = 1000;
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact(model, below, true));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(model, at, true));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(model, below, true, 800));
  ModelConfig suffixed;
  suffixed.model_id = "fixture[1k]";
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(suffixed, at, true));
}

void SoftTriggerThresholdsMatchLegacy() {
  // Eight messages of 71 tokens: 568 of 1000 is above the 50% soft trigger,
  // below the 80% hard trigger, with exactly eight compactable messages.
  const auto eight = RepeatMessages(8U, 71);
  EXPECT_EXPRESSION(ContextCompactionService::ShouldSoftCompact(eight, 1000, true));
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldCompact(eight, 1000, true));

  // Seven compactable messages are not enough, even at the same usage.
  const auto seven = RepeatMessages(7U, 108);
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(seven, 1000, true));

  // Excluded and hidden messages never count towards the eight.
  auto filtered = eight;
  filtered.front().exclude_from_context = true;
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(filtered, 1000, true));
  auto hidden = eight;
  hidden.front().hidden = true;
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(hidden, 1000, true));

  // The hard trigger owns everything at or above 80%.
  const auto hard = RepeatMessages(8U, 100);
  EXPECT_EXPRESSION(ContextCompactionService::ShouldCompact(hard, 1000, true));
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(hard, 1000, true));

  // Eight messages of 33 tokens (264 of 1000) stay below the soft trigger,
  // 568 reaches it.
  const auto below = RepeatMessages(8U, 33);
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(below, 1000, true));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldSoftCompact(eight, 1000, true));

  // Observed server input tokens win over the local estimate.
  EXPECT_EXPRESSION(ContextCompactionService::ShouldSoftCompact(below, 1000, true, 500));
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(eight, 1000, true, 499));
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(eight, 1000, true, 800));
  EXPECT_EXPRESSION(ContextCompactionService::ShouldSoftCompact(eight, 1000, true, 0));

  // The model overload resolves the window through the ported parser.
  auto model = ProtocolModel(ModelProtocol::openai_compatible, false);
  model.context_size = 1000;
  EXPECT_EXPRESSION(ContextCompactionService::ShouldSoftCompact(model, eight, true));
  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact(model, seven, true));

  EXPECT_EXPRESSION(!ContextCompactionService::ShouldSoftCompact({}, 1000, true));
}

void SplitForSoftCompactKeepsTailRatio() {
  // tail = max(1, round(compactable * 0.3)).
  const auto ten = RepeatMessages(10U, 20);
  const auto ten_split = ContextCompactionService::SplitForSoftCompact(ten);
  EXPECT_EXPRESSION(ten_split.head.size() == 7U);
  EXPECT_EXPRESSION(ten_split.tail.size() == 3U);
  for (std::size_t index = 0; index < ten_split.head.size(); ++index)
    EXPECT_EXPRESSION(ten_split.head[index].content == ten[index].content);
  for (std::size_t index = 0; index < ten_split.tail.size(); ++index)
    EXPECT_EXPRESSION(ten_split.tail[index].content == ten[7U + index].content);

  const auto seven =
      ContextCompactionService::SplitForSoftCompact(RepeatMessages(7U, 20));
  EXPECT_EXPRESSION(seven.head.size() == 5U);
  EXPECT_EXPRESSION(seven.tail.size() == 2U);

  // round(1.5) rounds away from zero, like `Math.round`.
  const auto five =
      ContextCompactionService::SplitForSoftCompact(RepeatMessages(5U, 20));
  EXPECT_EXPRESSION(five.head.size() == 3U);
  EXPECT_EXPRESSION(five.tail.size() == 2U);

  const auto four =
      ContextCompactionService::SplitForSoftCompact(RepeatMessages(4U, 20));
  EXPECT_EXPRESSION(four.head.size() == 3U);
  EXPECT_EXPRESSION(four.tail.size() == 1U);

  const auto three =
      ContextCompactionService::SplitForSoftCompact(RepeatMessages(3U, 20));
  EXPECT_EXPRESSION(three.head.size() == 2U);
  EXPECT_EXPRESSION(three.tail.size() == 1U);

  // A single compactable message is always kept: the tail floors at one.
  const auto one =
      ContextCompactionService::SplitForSoftCompact(RepeatMessages(1U, 20));
  EXPECT_EXPRESSION(one.head.empty());
  EXPECT_EXPRESSION(one.tail.size() == 1U);

  // No compactable message at all yields two empty halves.
  const auto empty = ContextCompactionService::SplitForSoftCompact({});
  EXPECT_EXPRESSION(empty.head.empty());
  EXPECT_EXPRESSION(empty.tail.empty());

  // Non-compactable messages are removed before the ratio is applied.
  auto mixed = RepeatMessages(10U, 20);
  mixed.front().exclude_from_context = true;
  mixed.at(3U).hidden = true;
  mixed.at(5U).content.clear();
  const auto mixed_split = ContextCompactionService::SplitForSoftCompact(mixed);
  EXPECT_EXPRESSION(mixed_split.head.size() + mixed_split.tail.size() == 7U);
  EXPECT_EXPRESSION(mixed_split.tail.size() == 2U);
}

void CompactableMessageSelectionMatchesLegacy() {
  std::vector<ChatMessage> messages{
      UserMessage("hello"),
      AssistantMessage("   "),
      AssistantWithTool({}, "call-1", "file_read", R"({"path":"a.txt"})",
                        std::nullopt),
  };
  ChatMessage excluded = UserMessage("excluded");
  excluded.exclude_from_context = true;
  messages.push_back(excluded);
  ChatMessage hidden = UserMessage("compacted summary");
  hidden.hidden = true;
  messages.push_back(hidden);

  const auto compactable =
      ContextCompactionService::CompactableMessages(messages);
  EXPECT_EXPRESSION(compactable.size() == 2U);
  EXPECT_EXPRESSION(compactable.front().content == "hello");
  EXPECT_EXPRESSION(compactable.back().content.empty());
  EXPECT_EXPRESSION(ContextCompactionService::CompactableMessageCount(messages) == 2U);
  EXPECT_EXPRESSION(ContextCompactionService::CompactableMessageCount({}) == 0U);
}

void SelectRecentUserMessagesHonoursBudget() {
  const auto tokens_of = [](const int content_characters) {
    return 8 + content_characters / domain::characters_per_token;
  };
  std::vector<ChatMessage> messages{
      UserMessage(std::string(400U, 'a')),
      AssistantMessage("answer"),
      UserMessage(std::string(400U, 'b')),
      UserMessage(std::string(400U, 'c')),
  };
  const auto selected = ContextCompactionService::SelectRecentUserMessages(
      messages, tokens_of(400) * 2);
  // The sweep walks newest first, the result stays chronological.
  EXPECT_EXPRESSION(selected.size() == 2U);
  EXPECT_EXPRESSION(selected.front().content == std::string(400U, 'b'));
  EXPECT_EXPRESSION(selected.back().content == std::string(400U, 'c'));

  // The sweep stops at the first message that does not fit; it does not skip
  // it and continue to older messages. The oldest message would fit the nine
  // remaining tokens, but the oversized one in between stops the sweep.
  std::vector<ChatMessage> stop{
      UserMessage("x"),
      UserMessage(std::string(800U, 'y')),
      UserMessage(std::string(400U, 'z')),
  };
  const auto stopped = ContextCompactionService::SelectRecentUserMessages(
      stop, tokens_of(400) + tokens_of(1));
  EXPECT_EXPRESSION(stopped.size() == 1U);
  EXPECT_EXPRESSION(stopped.front().content == std::string(400U, 'z'));

  // Only real, visible, non-empty user messages qualify.
  ChatMessage excluded = UserMessage(std::string(400U, 'e'));
  excluded.exclude_from_context = true;
  ChatMessage hidden = UserMessage(std::string(400U, 'h'));
  hidden.hidden = true;
  std::vector<ChatMessage> filtered{
      excluded,
      hidden,
      UserMessage("   "),
      AssistantMessage(std::string(400U, 'q')),
  };
  EXPECT_EXPRESSION(ContextCompactionService::SelectRecentUserMessages(filtered, 10'000)
             .empty());

  EXPECT_EXPRESSION(ContextCompactionService::SelectRecentUserMessages(messages, 0).empty());
  EXPECT_EXPRESSION(ContextCompactionService::SelectRecentUserMessages({}, 100).empty());

  // The legacy default budget keeps the recent tail that fits.
  EXPECT_EXPRESSION(ContextCompactionService::COMPACT_USER_MESSAGE_MAX_TOKENS == 20'000);
  const auto default_budget = ContextCompactionService::SelectRecentUserMessages(
      messages, ContextCompactionService::COMPACT_USER_MESSAGE_MAX_TOKENS);
  EXPECT_EXPRESSION(default_budget.size() == 3U);
}

void BuildTranscriptMatchesLegacyFormat() {
  std::vector<ChatMessage> messages{
      UserMessage("hello"),
      AssistantWithTool("working", "call-1", "file_read",
                        R"({"path":"a.txt"})", std::string{"contents"},
                        "thinking"),
  };
  const std::string expected =
      std::string{"## 1. user\n\nhello\n\n---\n\n## 2. assistant\n\nworking"} +
      "\n\nTool calls:\n- file_read: " + R"({"path":"a.txt"})" + "\n" +
      "\n\nTool result for: call-1\n\ncontents" + "\n\nReasoning:\nthinking";
  const auto transcript = ContextCompactionService::BuildTranscript(messages);
  EXPECT_EXPRESSION(transcript == expected);

  // A message carrying only a tool call has no content section.
  const auto call_only = ContextCompactionService::BuildTranscript(
      {AssistantWithTool({}, "call-2", "shell_execute", R"({"command":"ls"})",
                         std::nullopt)});
  EXPECT_EXPRESSION(call_only == "## 1. assistant\n\nTool calls:\n- shell_execute: " +
                           std::string{R"({"command":"ls"})"} + "\n");

  EXPECT_EXPRESSION(ContextCompactionService::BuildTranscript({}).empty());
}

void BuildTranscriptKeepsEverySegment() {
  // Eight messages of 48 KiB cross the 256 KiB segmentation boundary. The
  // legacy builder appends the `---` separator only while the active segment
  // still holds text, and empties that segment at the end of every message, so
  // the message that closes a segment starts the next one without a separator.
  // The expectation mirrors that rule; no message body may be lost or
  // duplicated by the join.
  const std::string padding(48U * 1024U, 'x');
  std::vector<ChatMessage> messages;
  std::string expected;
  std::size_t active_segment_chars = 0;
  for (int index = 0; index < 8; ++index) {
    const auto content = "m" + std::to_string(index) + ":" + padding;
    messages.push_back(UserMessage(content));
    if (active_segment_chars > 0)
      expected += "\n\n---\n\n";
    const auto piece =
        "## " + std::to_string(index + 1) + ". user\n\n" + content;
    expected += piece;
    active_segment_chars += piece.size();
    if (active_segment_chars >=
        ContextCompactionService::TRANSCRIPT_SEGMENT_MAX_CHARS) {
      active_segment_chars = 0U;
    }
  }
  const auto transcript = ContextCompactionService::BuildTranscript(messages);
  EXPECT_EXPRESSION(expected.size() >
         ContextCompactionService::TRANSCRIPT_SEGMENT_MAX_CHARS);
  EXPECT_EXPRESSION(transcript.size() == expected.size());
  EXPECT_EXPRESSION(transcript == expected);
  // Every message body survived the segmentation exactly once.
  for (int index = 0; index < 8; ++index) {
    const auto marker = "m" + std::to_string(index) + ":";
    EXPECT_EXPRESSION(transcript.find(marker) != std::string::npos);
    EXPECT_EXPRESSION(transcript.find(marker, transcript.find(marker) + 1U) ==
           std::string::npos);
  }

  // Per-message content above `ToolResult.MAX_TOOL_RESULT_CHARS` is still
  // middle-truncated, exactly like the legacy transcript.
  const std::string huge(60U * 1024U, 'y');
  const auto truncated =
      ContextCompactionService::BuildTranscript({UserMessage(huge)});
  EXPECT_EXPRESSION(truncated.find("chars truncated") != std::string::npos);
  EXPECT_EXPRESSION(truncated.size() < huge.size());
}

void StrategyTableSelectsProtocolRow() {
  const auto strategies = ContextCompactionService::Strategies();
  EXPECT_EXPRESSION(strategies.size() == 3U);
  for (const auto &strategy : strategies) {
    EXPECT_EXPRESSION(!strategy.id.empty());
    EXPECT_EXPRESSION(strategy.applies != nullptr);
    EXPECT_EXPRESSION(strategy.execute != nullptr);
  }

  // Dedicated compaction only exists for the two protocols that support it.
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             ProtocolModel(ModelProtocol::openai_compatible, true))
             .id == "openai_responses_summary");
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             ProtocolModel(ModelProtocol::codex_responses, true))
             .id == "responses_compaction");
  // Without a dedicated compression model both protocols fall back.
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             ProtocolModel(ModelProtocol::openai_compatible, false))
             .id == "generic_summary");
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             ProtocolModel(ModelProtocol::codex_responses, false))
             .id == "generic_summary");
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             ProtocolModel(ModelProtocol::anthropic_messages, true))
             .id == "generic_summary");
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             ProtocolModel(ModelProtocol::local_gguf, true))
             .id == "generic_summary");
}

// ---------------------------------------------------------------------------
// In-memory fakes
// ---------------------------------------------------------------------------

class MemorySettings final : public application::AsyncSettingsStore {
public:
  huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override {
    const auto found = values.find(key);
    co_return found == values.end() ? std::move(fallback) : found->second;
  }
  huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string, bool fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string, std::int64_t fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<void>>
  SetString(std::string key, std::string value) override {
    values.insert_or_assign(std::move(key), std::move(value));
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetBoolean(std::string, bool) override {
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetInteger(std::string, std::int64_t) override {
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  Remove(std::string key) override {
    values.erase(key);
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  ClearLineCodeSettings() override {
    values.clear();
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return values;
  }

  std::map<std::string, std::string, std::less<>> values;
};

class FakeModelStore final : public application::ModelStore {
public:
  huxerui::Task<std::expected<std::vector<ModelConfig>, ModelStoreError>>
  List() override {
    co_return models;
  }
  huxerui::Task<std::expected<std::optional<ModelConfig>, ModelStoreError>>
  Find(std::string id) override {
    for (const auto &model : models) {
      if (model.id == id)
        co_return std::optional<ModelConfig>{model};
    }
    co_return std::optional<ModelConfig>{};
  }
  huxerui::Task<std::expected<ModelConfig, ModelStoreError>>
  Save(ModelConfig model) override {
    co_return model;
  }
  huxerui::Task<std::expected<void, ModelStoreError>>
  Delete(std::vector<std::string>) override {
    co_return std::expected<void, ModelStoreError>{};
  }
  huxerui::Task<std::expected<void, ModelStoreError>>
  Select(std::string id) override {
    selected_id = std::move(id);
    co_return std::expected<void, ModelStoreError>{};
  }
  huxerui::Task<std::expected<std::string, ModelStoreError>>
  SelectedId() override {
    if (selection_fails)
      co_return std::unexpected(ModelStoreError{"selection unavailable"});
    co_return selected_id;
  }

  std::vector<ModelConfig> models;
  std::string selected_id;
  bool selection_fails{};
};

struct ScriptedReply final {
  bool success{true};
  std::string text;
  std::string error_message;
};

ScriptedReply Reply(std::string text) {
  ScriptedReply reply;
  reply.text = std::move(text);
  return reply;
}

ScriptedReply Failure(std::string message) {
  ScriptedReply reply;
  reply.success = false;
  reply.error_message = std::move(message);
  return reply;
}

class FakeCompletionGateway final : public application::CompletionGateway {
public:
  huxerui::Task<std::expected<CompletionResponse, CompletionError>>
  Complete(CompletionRequest request, CompletionObserver) override {
    requests.push_back(std::move(request));
    const auto index = calls++;
    if (cancel_on_first_call && index == 0U && cancellation != nullptr)
      cancellation->request_stop();
    const auto &reply = index < script.size() ? script[index] : fallback;
    if (!reply.success) {
      co_return std::unexpected(CompletionError{
          .code = CompletionErrorCode::transport,
          .message = reply.error_message});
    }
    co_return CompletionResponse{.text = reply.text,
                                 .reasoning_content = {},
                                 .tool_calls = {},
                                 .input_tokens = 0,
                                 .output_tokens = 0};
  }

  std::vector<ScriptedReply> script;
  ScriptedReply fallback{};
  std::vector<CompletionRequest> requests;
  std::size_t calls{};
  bool cancel_on_first_call{};
  std::stop_source *cancellation{};
};

// ---------------------------------------------------------------------------
// Scenario harness. Every coroutine scenario of the whole binary runs inside
// one HuxerUI test application: `prepare` configures it, the single run
// executes them in order, and `verify` asserts on the recorded results.
// ---------------------------------------------------------------------------

enum class ProbeMode : std::uint8_t {
  compact,
  summary_content,
  responses_fallback,
};

struct Scenario final {
  std::shared_ptr<FakeCompletionGateway> gateway{
      std::make_shared<FakeCompletionGateway>()};
  std::shared_ptr<MemorySettings> settings{std::make_shared<MemorySettings>()};
  std::shared_ptr<FakeModelStore> models{std::make_shared<FakeModelStore>()};
  std::shared_ptr<ContextCompactionService> service;
  std::vector<ChatMessage> messages;
  ModelConfig model;
  std::stop_source cancellation;
  ProbeMode mode{ProbeMode::compact};
  std::string template_source;
  bool cancel_before_start{};
  std::optional<ContextCompactionResult> result;
  std::optional<ContextCompactionError> error;
  std::optional<std::string> helper_content;
  bool done{};
};

std::vector<std::shared_ptr<Scenario>> scenarios;
bool scenarios_finished{};

std::shared_ptr<Scenario> AddScenario() {
  scenarios.push_back(std::make_shared<Scenario>());
  return scenarios.back();
}

huxerui::View CompactionProbe() {
  const auto all =
      std::make_shared<std::vector<std::shared_ptr<Scenario>>>(scenarios);
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([all, tasks] {
    const auto handle = tasks.Launch([all]() -> huxerui::Task<void> {
      for (const auto &scenario : *all) {
        switch (scenario->mode) {
        case ProbeMode::compact: {
          if (scenario->cancel_before_start)
            scenario->cancellation.request_stop();
          auto result = co_await scenario->service->Compact(
              scenario->model, scenario->messages,
              scenario->cancellation.get_token());
          if (result)
            scenario->result = std::move(*result);
          else
            scenario->error = std::move(result.error());
          break;
        }
        case ProbeMode::summary_content:
          scenario->helper_content =
              co_await scenario->service->CreateCompactSummaryContent(
                  scenario->template_source);
          break;
        case ProbeMode::responses_fallback:
          scenario->helper_content = co_await scenario->service
                                         ->CreateResponsesCompactFallbackContent();
          break;
        }
        scenario->done = true;
      }
      scenarios_finished = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("context-compaction-probe");
}

void RunScenarios() {
  for (const auto &scenario : scenarios) {
    scenario->service = std::make_shared<ContextCompactionService>(
        scenario->gateway,
        std::make_shared<PromptTemplateRepository>(scenario->settings),
        scenario->models);
    scenario->gateway->cancellation = &scenario->cancellation;
  }
  const huxerui::Application application(CompactionProbe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  for (std::size_t frame = 0; frame < 80'000U && !scenarios_finished; ++frame)
    ui.Pump(std::chrono::milliseconds{1});
  EXPECT_EXPRESSION(scenarios_finished);
  for (const auto &scenario : scenarios)
    EXPECT_EXPRESSION(scenario->done);
}

// ---------------------------------------------------------------------------
// Gateway-observed strategy behaviour
// ---------------------------------------------------------------------------

std::shared_ptr<Scenario> generic_summary_scenario;
std::shared_ptr<Scenario> openai_responses_scenario;
std::shared_ptr<Scenario> responses_compaction_scenario;
std::shared_ptr<Scenario> dedicated_compaction_scenario;
std::shared_ptr<Scenario> missing_compaction_item_scenario;
std::shared_ptr<Scenario> retry_failure_scenario;
std::shared_ptr<Scenario> retry_success_scenario;
std::shared_ptr<Scenario> cancelled_before_start_scenario;
std::shared_ptr<Scenario> cancelled_during_failure_scenario;
std::shared_ptr<Scenario> cancelled_after_success_scenario;
std::shared_ptr<Scenario> empty_input_scenario;
std::shared_ptr<Scenario> excluded_input_scenario;
std::shared_ptr<Scenario> resolved_model_scenario;
std::shared_ptr<Scenario> missing_model_scenario;
std::shared_ptr<Scenario> failing_selection_scenario;
std::shared_ptr<Scenario> format_analysis_scenario;
std::shared_ptr<Scenario> format_upper_scenario;
std::shared_ptr<Scenario> format_multiple_scenario;
std::shared_ptr<Scenario> format_none_scenario;
std::shared_ptr<Scenario> format_unclosed_scenario;
std::shared_ptr<Scenario> format_blank_lines_scenario;
std::shared_ptr<Scenario> responses_fallback_scenario;

void PrepareGenericSummaryStrategy() {
  generic_summary_scenario = AddScenario();
  generic_summary_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  generic_summary_scenario->messages = {UserMessage("hello"),
                                        AssistantMessage("hi")};
  // Customised templates prove the injected repository is really consulted.
  generic_summary_scenario
      ->settings->values["@linecode_prompt_template_contextCompaction"] =
      "COMPACT DIRECTIVE";
  generic_summary_scenario->settings
      ->values["@linecode_prompt_template_contextCompactionSummaryPrefix"] =
      "PREFIX\n\n{{SUMMARY}}\n\nEND";
  generic_summary_scenario->gateway->script = {
      Reply("plain <analysis>hidden</analysis><summary>the answer</summary> "
            "tail")};
}

void VerifyGenericSummaryStrategy() {
  EXPECT_EXPRESSION(generic_summary_scenario->gateway->calls == 1U);
  EXPECT_EXPRESSION(generic_summary_scenario->gateway->requests.size() == 1U);
  const auto &request = generic_summary_scenario->gateway->requests.front();
  EXPECT_EXPRESSION(request.model.model_id == "selected-model");
  EXPECT_EXPRESSION(request.stream);
  EXPECT_EXPRESSION(request.reasoning_effort == domain::ReasoningEffort::off);
  EXPECT_EXPRESSION(!request.preserve_reasoning);
  EXPECT_EXPRESSION(request.tools.empty());
  EXPECT_EXPRESSION(request.messages.size() == 1U);
  EXPECT_EXPRESSION(request.messages.front().role == CompletionRole::user);
  EXPECT_EXPRESSION(request.messages.front().content.starts_with("COMPACT DIRECTIVE"));
  EXPECT_EXPRESSION(request.messages.front().content.find(
             "\n\nConversation transcript:\n## 1. user\n\nhello") !=
         std::string::npos);

  EXPECT_EXPRESSION(generic_summary_scenario->result.has_value());
  EXPECT_EXPRESSION(!generic_summary_scenario->error.has_value());
  EXPECT_EXPRESSION(generic_summary_scenario->result->response_input_item_json.empty());
  EXPECT_EXPRESSION(generic_summary_scenario->result->summary_content ==
         "PREFIX\n\nSummary:\nthe answer\n\nEND");
  EXPECT_EXPRESSION(!generic_summary_scenario->result->Empty());
}

void PrepareOpenAiResponsesSummary() {
  openai_responses_scenario = AddScenario();
  openai_responses_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, true);
  openai_responses_scenario->model.compression_model_auto = false;
  openai_responses_scenario->model.compression_model_id = "compression-model";
  openai_responses_scenario->messages = {UserMessage("hello")};
  openai_responses_scenario->gateway->fallback = Reply("done");
}

void VerifyOpenAiResponsesSummary() {
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             openai_responses_scenario->model)
             .id == "openai_responses_summary");
  EXPECT_EXPRESSION(openai_responses_scenario->gateway->calls == 1U);
  const auto &request = openai_responses_scenario->gateway->requests.front();
  // The dedicated compression model id replaces the selected one, and the
  // transcript prompt shape matches the generic summary strategy.
  EXPECT_EXPRESSION(request.model.model_id == "compression-model");
  EXPECT_EXPRESSION(request.stream);
  EXPECT_EXPRESSION(request.messages.size() == 1U);
  EXPECT_EXPRESSION(request.messages.front().content.find(
             "\n\nConversation transcript:\n") != std::string::npos);
  EXPECT_EXPRESSION(request.messages.front().content.find("## 1. user") !=
         std::string::npos);
  EXPECT_EXPRESSION(openai_responses_scenario->result.has_value());
  EXPECT_EXPRESSION(openai_responses_scenario->result->response_input_item_json.empty());
  EXPECT_EXPRESSION(openai_responses_scenario->result->summary_content.find("done") !=
         std::string::npos);
}

void PrepareResponsesCompaction() {
  responses_compaction_scenario = AddScenario();
  responses_compaction_scenario->model =
      ProtocolModel(ModelProtocol::codex_responses, true);
  responses_compaction_scenario->messages = {
      UserMessage("hello"),
      AssistantWithTool("working", "call-1", "file_read",
                        R"({"path":"a.txt"})", std::string{"contents"}),
  };
  // Surrounding whitespace is trimmed, exactly like the legacy value object.
  responses_compaction_scenario->gateway->fallback =
      Reply("  {\"type\":\"compaction\"}  ");

  // The same protocol with an explicit compression model uses that model id.
  dedicated_compaction_scenario = AddScenario();
  dedicated_compaction_scenario->model =
      ProtocolModel(ModelProtocol::codex_responses, true);
  dedicated_compaction_scenario->model.compression_model_auto = false;
  dedicated_compaction_scenario->model.compression_model_id =
      "compression-model";
  dedicated_compaction_scenario->messages = {UserMessage("hello")};
  dedicated_compaction_scenario->gateway->fallback =
      Reply(R"({"type":"compaction"})");

  // A reply without a compaction item is rejected with the legacy message.
  missing_compaction_item_scenario = AddScenario();
  missing_compaction_item_scenario->model =
      ProtocolModel(ModelProtocol::codex_responses, true);
  missing_compaction_item_scenario->messages = {UserMessage("hello")};
  missing_compaction_item_scenario->gateway->fallback =
      Reply("   ");
}

void VerifyResponsesCompaction() {
  EXPECT_EXPRESSION(ContextCompactionService::SelectStrategy(
             responses_compaction_scenario->model)
             .id == "responses_compaction");
  EXPECT_EXPRESSION(responses_compaction_scenario->gateway->calls == 1U);
  const auto &request = responses_compaction_scenario->gateway->requests.front();
  EXPECT_EXPRESSION(request.model.model_id == "selected-model");
  // The compaction input is the conversation itself, not a transcript prompt,
  // and it is not streamed.
  EXPECT_EXPRESSION(!request.stream);
  EXPECT_EXPRESSION(request.reasoning_effort == domain::ReasoningEffort::off);
  EXPECT_EXPRESSION(request.messages.size() == 3U);
  EXPECT_EXPRESSION(request.messages[0].role == CompletionRole::user);
  EXPECT_EXPRESSION(request.messages[0].content == "hello");
  EXPECT_EXPRESSION(request.messages[1].role == CompletionRole::assistant);
  EXPECT_EXPRESSION(request.messages[1].content == "working");
  EXPECT_EXPRESSION(request.messages[1].tool_calls.size() == 1U);
  EXPECT_EXPRESSION(request.messages[1].tool_calls.front().name == "file_read");
  EXPECT_EXPRESSION(request.messages[2].role == CompletionRole::tool);
  EXPECT_EXPRESSION(request.messages[2].tool_result.has_value());
  EXPECT_EXPRESSION(request.messages[2].tool_result->content == "contents");
  for (const auto &message : request.messages)
    EXPECT_EXPRESSION(message.content.find("Conversation transcript:") ==
           std::string::npos);

  EXPECT_EXPRESSION(responses_compaction_scenario->result.has_value());
  EXPECT_EXPRESSION(responses_compaction_scenario->result->response_input_item_json ==
         R"({"type":"compaction"})");
  EXPECT_EXPRESSION(responses_compaction_scenario->result->summary_content.starts_with(
      "This session is being continued from a previous conversation"));

  EXPECT_EXPRESSION(dedicated_compaction_scenario->gateway->requests.front()
             .model.model_id == "compression-model");

  EXPECT_EXPRESSION(missing_compaction_item_scenario->error.has_value());
  EXPECT_EXPRESSION(missing_compaction_item_scenario->error->code ==
         ContextCompactionErrorCode::missing_compaction_item);
  EXPECT_EXPRESSION(!missing_compaction_item_scenario->result.has_value());
}

void PrepareRetries() {
  retry_failure_scenario = AddScenario();
  retry_failure_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  retry_failure_scenario->messages = {UserMessage("hello")};
  retry_failure_scenario->gateway->fallback =
      Failure("transport boom");

  retry_success_scenario = AddScenario();
  retry_success_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  retry_success_scenario->messages = {UserMessage("hello")};
  retry_success_scenario->gateway->script = {
      Failure("first failure"),
      Failure("second failure"),
      Reply("recovered summary"),
  };
}

void VerifyRetries() {
  // Initial call plus MAX_COMPACT_RETRIES attempts.
  EXPECT_EXPRESSION(ContextCompactionService::MAX_COMPACT_RETRIES == 2);
  EXPECT_EXPRESSION(retry_failure_scenario->gateway->calls == 3U);
  EXPECT_EXPRESSION(!retry_failure_scenario->result.has_value());
  EXPECT_EXPRESSION(retry_failure_scenario->error.has_value());
  EXPECT_EXPRESSION(retry_failure_scenario->error->code ==
         ContextCompactionErrorCode::completion_failed);
  EXPECT_EXPRESSION(retry_failure_scenario->error->message == "transport boom");

  EXPECT_EXPRESSION(retry_success_scenario->gateway->calls == 3U);
  EXPECT_EXPRESSION(!retry_success_scenario->error.has_value());
  EXPECT_EXPRESSION(retry_success_scenario->result.has_value());
  EXPECT_EXPRESSION(retry_success_scenario->result->summary_content.find(
             "recovered summary") != std::string::npos);
}

void PrepareCancellation() {
  // Already cancelled: nothing is requested at all.
  cancelled_before_start_scenario = AddScenario();
  cancelled_before_start_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  cancelled_before_start_scenario->messages = {UserMessage("hello")};
  cancelled_before_start_scenario->cancel_before_start = true;

  // Cancelled while the first attempt is in flight and that attempt fails:
  // the failure is neither retried nor reported.
  cancelled_during_failure_scenario = AddScenario();
  cancelled_during_failure_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  cancelled_during_failure_scenario->messages = {UserMessage("hello")};
  cancelled_during_failure_scenario->gateway->cancel_on_first_call = true;
  cancelled_during_failure_scenario->gateway->fallback =
      Failure("cancelled request");

  // Cancelled after a successful request: the legacy service returned the
  // empty result instead of the produced summary.
  cancelled_after_success_scenario = AddScenario();
  cancelled_after_success_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  cancelled_after_success_scenario->messages = {UserMessage("hello")};
  cancelled_after_success_scenario->gateway->cancel_on_first_call = true;
  cancelled_after_success_scenario->gateway->fallback =
      Reply("late summary");
}

void VerifyCancellation() {
  EXPECT_EXPRESSION(cancelled_before_start_scenario->gateway->calls == 0U);
  EXPECT_EXPRESSION(cancelled_before_start_scenario->result.has_value());
  EXPECT_EXPRESSION(cancelled_before_start_scenario->result->Empty());
  EXPECT_EXPRESSION(!cancelled_before_start_scenario->error.has_value());

  EXPECT_EXPRESSION(cancelled_during_failure_scenario->gateway->calls == 1U);
  EXPECT_EXPRESSION(cancelled_during_failure_scenario->result.has_value());
  EXPECT_EXPRESSION(cancelled_during_failure_scenario->result->Empty());
  EXPECT_EXPRESSION(!cancelled_during_failure_scenario->error.has_value());

  EXPECT_EXPRESSION(cancelled_after_success_scenario->gateway->calls == 1U);
  EXPECT_EXPRESSION(cancelled_after_success_scenario->result.has_value());
  EXPECT_EXPRESSION(cancelled_after_success_scenario->result->Empty());
  EXPECT_EXPRESSION(!cancelled_after_success_scenario->error.has_value());
}

void PrepareEmptyInput() {
  empty_input_scenario = AddScenario();
  empty_input_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);

  excluded_input_scenario = AddScenario();
  excluded_input_scenario->model =
      ProtocolModel(ModelProtocol::openai_compatible, false);
  ChatMessage message = UserMessage("hidden history");
  message.exclude_from_context = true;
  excluded_input_scenario->messages = {message};
}

void VerifyEmptyInput() {
  EXPECT_EXPRESSION(empty_input_scenario->error.has_value());
  EXPECT_EXPRESSION(empty_input_scenario->error->code ==
         ContextCompactionErrorCode::nothing_to_compact);
  EXPECT_EXPRESSION(empty_input_scenario->gateway->calls == 0U);

  EXPECT_EXPRESSION(excluded_input_scenario->error.has_value());
  EXPECT_EXPRESSION(excluded_input_scenario->error->code ==
         ContextCompactionErrorCode::nothing_to_compact);
  EXPECT_EXPRESSION(excluded_input_scenario->gateway->calls == 0U);
}

void PrepareModelResolution() {
  resolved_model_scenario = AddScenario();
  resolved_model_scenario->model = ModelConfig{};
  resolved_model_scenario->messages = {UserMessage("hello")};
  resolved_model_scenario->models->selected_id = "model-record";
  resolved_model_scenario->models->models = {
      ProtocolModel(ModelProtocol::openai_compatible, false)};
  resolved_model_scenario->gateway->fallback =
      Reply("ok");

  missing_model_scenario = AddScenario();
  missing_model_scenario->model = ModelConfig{};
  missing_model_scenario->messages = {UserMessage("hello")};

  failing_selection_scenario = AddScenario();
  failing_selection_scenario->model = ModelConfig{};
  failing_selection_scenario->messages = {UserMessage("hello")};
  failing_selection_scenario->models->selection_fails = true;
}

void VerifyModelResolution() {
  EXPECT_EXPRESSION(resolved_model_scenario->gateway->calls == 1U);
  EXPECT_EXPRESSION(resolved_model_scenario->gateway->requests.front().model.model_id ==
         "selected-model");
  EXPECT_EXPRESSION(resolved_model_scenario->result.has_value());

  EXPECT_EXPRESSION(missing_model_scenario->gateway->calls == 0U);
  EXPECT_EXPRESSION(missing_model_scenario->error.has_value());
  EXPECT_EXPRESSION(missing_model_scenario->error->code ==
         ContextCompactionErrorCode::no_model);
  EXPECT_EXPRESSION(missing_model_scenario->error->message ==
         "No model available, cannot compact context");

  EXPECT_EXPRESSION(failing_selection_scenario->gateway->calls == 0U);
  EXPECT_EXPRESSION(failing_selection_scenario->error.has_value());
  EXPECT_EXPRESSION(failing_selection_scenario->error->code ==
         ContextCompactionErrorCode::no_model);
  EXPECT_EXPRESSION(failing_selection_scenario->error->message ==
         "selection unavailable");
}

std::shared_ptr<Scenario> FormattingScenario(std::string source) {
  const auto scenario = AddScenario();
  scenario->mode = ProbeMode::summary_content;
  scenario->template_source = std::move(source);
  // Rendering through a `{{SUMMARY}}`-only prefix exposes the formatted text.
  scenario
      ->settings->values["@linecode_prompt_template_contextCompactionSummaryPrefix"] =
      "{{SUMMARY}}";
  return scenario;
}

void PrepareSummaryFormatting() {
  format_analysis_scenario =
      FormattingScenario("keep <analysis>secret</analysis> tail");
  format_upper_scenario = FormattingScenario(
      "<ANALYSIS>secret</ANALYSIS><SUMMARY> Upper case </SUMMARY>");
  format_multiple_scenario = FormattingScenario(
      "<analysis>one</analysis>a<analysis>two</analysis>b"
      "<summary>first</summary><summary>second</summary>");
  format_none_scenario = FormattingScenario("no tags at all");
  format_unclosed_scenario =
      FormattingScenario("<analysis>unclosed and <summary>dangling");
  format_blank_lines_scenario = FormattingScenario("a\n\n\n\nb");

  responses_fallback_scenario = AddScenario();
  responses_fallback_scenario->mode = ProbeMode::responses_fallback;
}

void VerifySummaryFormatting() {
  EXPECT_EXPRESSION(format_analysis_scenario->helper_content ==
         std::optional<std::string>{"keep  tail"});
  EXPECT_EXPRESSION(format_upper_scenario->helper_content ==
         std::optional<std::string>{"Summary:\nUpper case"});
  // Every analysis block is dropped and the first summary wins.
  EXPECT_EXPRESSION(format_multiple_scenario->helper_content ==
         std::optional<std::string>{"Summary:\nfirst"});
  EXPECT_EXPRESSION(format_none_scenario->helper_content ==
         std::optional<std::string>{"no tags at all"});
  // An unclosed tag is literal text, exactly like the legacy regex.
  EXPECT_EXPRESSION(format_unclosed_scenario->helper_content ==
         std::optional<std::string>{"<analysis>unclosed and <summary>dangling"});
  // Legacy `replaceAll("\\n\\n+", "\n\n")` collapses the run.
  EXPECT_EXPRESSION(format_blank_lines_scenario->helper_content ==
         std::optional<std::string>{"a\n\nb"});
  EXPECT_EXPRESSION(responses_fallback_scenario->helper_content ==
         std::optional<std::string>{
             "This session is being continued from a previous conversation "
             "that ran out of context. The earlier portion of the "
             "conversation has been compacted by the OpenAI Responses compact "
             "API. Continue the conversation from where it left off without "
             "asking the user any further questions. Resume directly."});
}

void ConstructorRejectsMissingDependencies() {
  const auto templates = std::make_shared<PromptTemplateRepository>(
      std::make_shared<MemorySettings>());
  const auto models = std::make_shared<FakeModelStore>();

  const auto rejects = [](std::shared_ptr<application::CompletionGateway>
                              completion,
                          std::shared_ptr<PromptTemplateRepository> prompts,
                          std::shared_ptr<application::ModelStore> store) {
    try {
      [[maybe_unused]] const ContextCompactionService service(
          std::move(completion), std::move(prompts), std::move(store));
      return false;
    } catch (const std::invalid_argument &) {
      return true;
    }
  };

  EXPECT_EXPRESSION(rejects(nullptr, templates, models));
  EXPECT_EXPRESSION(rejects(std::make_shared<FakeCompletionGateway>(), nullptr, models));
  EXPECT_EXPRESSION(rejects(std::make_shared<FakeCompletionGateway>(), templates, nullptr));
  EXPECT_EXPRESSION(!rejects(std::make_shared<FakeCompletionGateway>(), templates, models));
}

struct ScenarioTest final {
  void (*prepare)();
  void (*verify)();
};

constexpr ScenarioTest scenario_tests[] = {
    {&PrepareGenericSummaryStrategy, &VerifyGenericSummaryStrategy},
    {&PrepareOpenAiResponsesSummary, &VerifyOpenAiResponsesSummary},
    {&PrepareResponsesCompaction, &VerifyResponsesCompaction},
    {&PrepareRetries, &VerifyRetries},
    {&PrepareCancellation, &VerifyCancellation},
    {&PrepareEmptyInput, &VerifyEmptyInput},
    {&PrepareModelResolution, &VerifyModelResolution},
    {&PrepareSummaryFormatting, &VerifySummaryFormatting},
};

} // namespace

TEST(context_compaction_tests, LegacySuite) {
  // Pure contracts: no HuxerUI runtime required.
  HardTriggerThresholdsMatchLegacy();
  SoftTriggerThresholdsMatchLegacy();
  SplitForSoftCompactKeepsTailRatio();
  CompactableMessageSelectionMatchesLegacy();
  SelectRecentUserMessagesHonoursBudget();
  BuildTranscriptMatchesLegacyFormat();
  BuildTranscriptKeepsEverySegment();
  StrategyTableSelectsProtocolRow();
  ConstructorRejectsMissingDependencies();

  // Gateway-driven contracts: every scenario runs inside one test application.
  for (const auto &test : scenario_tests)
    test.prepare();
  RunScenarios();
  for (const auto &test : scenario_tests)
    test.verify();

  scenarios.clear();
  std::cout << "context_compaction_tests passed\n";
  return;
}
