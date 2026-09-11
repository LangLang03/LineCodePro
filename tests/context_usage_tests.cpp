// Contract tests for the ported context-usage estimation and the model
// context-window resolution that the legacy `ModelContextParser` and
// `ContextManager` provided.

#include <cassert>
#include <cstdint>
#include <iostream>
#include <string>
#include <vector>

#include "domain/context_usage.h"

namespace {

using linecode::domain::ChatMessage;
using linecode::domain::ChatToolCall;
using linecode::domain::ChatToolResult;
using linecode::domain::AssistantToolEvent;
using linecode::domain::AssistantTimelineEvent;
using linecode::domain::ContextSnapshot;
using linecode::domain::InputAttachment;
using linecode::domain::MessageRole;
using linecode::domain::ModelConfig;
using linecode::domain::ModelContextInfo;

ChatMessage UserMessage(std::string content) {
  ChatMessage message;
  message.role = MessageRole::user;
  message.content = std::move(content);
  return message;
}

ChatMessage ToolMessage(std::string call_id, std::string name,
                        std::string content) {
  ChatMessage message;
  message.role = MessageRole::assistant;
  AssistantToolEvent event;
  event.call.id = std::move(call_id);
  event.call.name = std::move(name);
  event.call.arguments_json = R"({"path":"a.txt"})";
  event.result = ChatToolResult{.call_id = event.call.id,
                                .name = event.call.name,
                                .content = std::move(content),
                                .error = false,
                                .diff_id = {},
                                .review_state = {},
                                .review_message = {}};
  message.timeline.push_back(std::move(event));
  return message;
}

void GroupedTokensMatchNumberFormat() {
  using linecode::domain::FormatGroupedTokens;
  assert(FormatGroupedTokens(0) == "0");
  assert(FormatGroupedTokens(7) == "7");
  assert(FormatGroupedTokens(999) == "999");
  assert(FormatGroupedTokens(1'000) == "1,000");
  assert(FormatGroupedTokens(4'096) == "4,096");
  assert(FormatGroupedTokens(25'000) == "25,000");
  assert(FormatGroupedTokens(250'000) == "250,000");
  assert(FormatGroupedTokens(1'000'000) == "1,000,000");
  assert(FormatGroupedTokens(-12'345) == "-12,345");
}

void FormatLabelMatchesLegacy() {
  assert(linecode::domain::FormatContextLabel(250'000) == "250K");
  assert(linecode::domain::FormatContextLabel(1'000'000) == "1M");
  assert(linecode::domain::FormatContextLabel(4'096) == "4096");
  assert(linecode::domain::FormatContextLabel(0).empty());
}

void ModelIdSuffixIsParsedLikeModelContextParser() {
  // No suffix: the default window applies and the id is untouched.
  auto plain = linecode::domain::ResolveModelContext(std::string_view{"gpt-4o"});
  assert(plain.api_model_id == "gpt-4o");
  assert(plain.context_tokens == linecode::domain::default_context_tokens);
  assert(plain.context_label == "250K");

  // Lower-case and upper-case units both scale.
  auto kilo = linecode::domain::ResolveModelContext(std::string_view{"m[128k]"});
  assert(kilo.api_model_id == "m");
  assert(kilo.context_tokens == 128'000);
  assert(kilo.context_label == "128K");

  auto mega = linecode::domain::ResolveModelContext(std::string_view{"m[1M]"});
  assert(mega.context_tokens == 1'000'000);

  // A fractional value rounds like Math.round.
  auto fractional =
      linecode::domain::ResolveModelContext(std::string_view{"m[1.5k]"});
  assert(fractional.context_tokens == 1'500);

  // A suffix without a unit is a literal token count.
  auto literal = linecode::domain::ResolveModelContext(std::string_view{"m[4096]"});
  assert(literal.context_tokens == 4'096);

  // A malformed suffix is not a suffix: the model keeps the whole id.
  auto malformed =
      linecode::domain::ResolveModelContext(std::string_view{"m[abc]"});
  assert(malformed.api_model_id == "m[abc]");
  assert(malformed.context_tokens == linecode::domain::default_context_tokens);

  // A suffix that would leave no id keeps the trimmed original.
  auto bare = linecode::domain::ResolveModelContext(std::string_view{"[8k]"});
  assert(bare.api_model_id == "[8k]");
  assert(bare.context_tokens == 8'000);
}

void ExplicitContextSizeWinsOverSuffix() {
  ModelConfig model;
  model.model_id = "m[8k]";
  model.context_size = 32'000;
  const auto resolved = linecode::domain::ResolveModelContext(model);
  // The field wins and the suffix stays in the id, matching the legacy
  // documented precedence.
  assert(resolved.api_model_id == "m[8k]");
  assert(resolved.context_tokens == 32'000);
  assert(resolved.context_label == "32K");

  // Unset falls back to the suffix.
  model.context_size = ModelConfig::context_size_unset;
  const auto fallen = linecode::domain::ResolveModelContext(model);
  assert(fallen.api_model_id == "m");
  assert(fallen.context_tokens == 8'000);
}

void EstimationMirrorsContextManager() {
  // Empty content is not a context message.
  ChatMessage blank;
  blank.role = MessageRole::user;
  assert(linecode::domain::EstimateMessageTokens(blank, true) == 0);

  // Overhead plus ceil(characters / 4).
  auto short_message = UserMessage("abcd");
  assert(linecode::domain::EstimateMessageTokens(short_message, true) == 8 + 1);

  auto long_message = UserMessage(std::string(400, 'x'));
  assert(linecode::domain::EstimateMessageTokens(long_message, true) == 8 + 100);

  // Excluded messages cost nothing even with content.
  auto excluded = UserMessage("ignored");
  excluded.exclude_from_context = true;
  assert(linecode::domain::EstimateMessageTokens(excluded, true) == 0);

  // Reasoning is counted only when requested.
  ChatMessage reasoned = UserMessage("abcd");
  reasoned.reasoning_content = std::string(40, 'r');
  assert(linecode::domain::EstimateMessageTokens(reasoned, true) == 8 + 1 + 10);
  assert(linecode::domain::EstimateMessageTokens(reasoned, false) == 8 + 1);

  // Tool messages are context messages even with empty content.
  ChatMessage tool;
  tool.role = MessageRole::tool;
  assert(linecode::domain::EstimateMessageTokens(tool, true) == 8);

  // Attachments add name, source and path.
  ChatMessage attached = UserMessage("abcd");
  attached.attachments.push_back(
      InputAttachment{"a.txt", "src/a.txt", "local"});
  const int attachment_cost = attached.attachments.size() == 0
                                  ? 0
                                  : linecode::domain::EstimateMessageTokens(
                                        attached, true);
  assert(attachment_cost > 8 + 1);
}

void ToolCallArgumentsAndResultsAreCounted() {
  auto message = ToolMessage("c1", "file_read", "contents here");
  const int with_result = linecode::domain::EstimateMessageTokens(message, true);
  assert(with_result > linecode::domain::message_overhead_tokens);

  // Dropping the result lowers the estimate, which proves the result text is
  // part of the cost.
  message.timeline.clear();
  message.content = "x";
  assert(linecode::domain::EstimateMessageTokens(message, true) < with_result);
}

void SnapshotClampsPercent() {
  std::vector<ChatMessage> messages;
  messages.push_back(UserMessage(std::string(4'000, 'a'))); // ~1008 tokens

  const auto small =
      linecode::domain::SnapshotContext(messages, 10'000, true);
  assert(small.max_tokens == 10'000);
  assert(small.used_tokens > 0);
  assert(small.percent > 0 && small.percent < 100);

  // A tiny window saturates instead of overflowing past 100.
  const auto saturated = linecode::domain::SnapshotContext(messages, 1, true);
  assert(saturated.percent == 100);

  // A zero or negative window is floored at one token rather than dividing by
  // zero.
  const auto guarded = linecode::domain::SnapshotContext(messages, 0, true);
  assert(guarded.max_tokens == 1);

  // No messages at all reports zero percent.
  const auto empty =
      linecode::domain::SnapshotContext({}, 10'000, true);
  assert(empty.used_tokens == 0);
  assert(empty.percent == 0);
}

} // namespace

int main() {
  FormatLabelMatchesLegacy();
  GroupedTokensMatchNumberFormat();
  ModelIdSuffixIsParsedLikeModelContextParser();
  ExplicitContextSizeWinsOverSuffix();
  EstimationMirrorsContextManager();
  ToolCallArgumentsAndResultsAreCounted();
  SnapshotClampsPercent();
  std::cout << "context_usage_tests passed\n";
  return 0;
}
