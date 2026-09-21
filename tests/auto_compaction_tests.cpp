// Contract tests for the auto context-compaction orchestration
// (`cn.lineai.mvp.ContextCompactionController`): the three trigger decisions,
// `getAutoCompactPreservedTail`, the hard/soft merge write-back and the failure
// and cancellation paths. Everything under test is a pure function over the
// conversation, so no model, store or coroutine is involved.

#include "gtest_support.h"
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "application/auto_compaction_service.h"
#include "application/chat_session.h"
#include "application/context_compaction.h"
#include "domain/app_state.h"
#include "domain/behavior_settings.h"
#include "domain/compaction_progress.h"
#include "domain/context_usage.h"
#include "domain/model_config.h"
#include "infrastructure/in_memory_conversation_store.h"
#include "presentation/compaction_progress_presentation.h"

namespace {

using namespace linecode;

using application::CompactFailureMessage;
using application::CompactFailureNoSummary;
using application::CompactFailureOutOfMemory;
using application::CompactionResumeMode;
using application::CompactionWriteback;
using application::CompletedCompactProgress;
using application::HasCompactableBaseMessages;
using application::MergeCompaction;
using application::MergeSoftCompaction;
using application::MessageIdSet;
using application::PreservedTail;
using application::RetainedUserMessageIds;
using application::ShouldAutoCompactBeforeRequest;
using application::ShouldAutoCompactMidLoop;
using application::ShouldAutoSoftCompactBeforeRequest;
using application::ShouldResumeAfterCompaction;
using domain::AssistantToolEvent;
using domain::ChatMessage;
using domain::ChatToolCall;
using domain::ChatToolResult;
using domain::MessageRole;
using domain::ModelConfig;
using presentation::CompactStatusIcon;

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

constexpr int kContextTokens = 1'000;

ModelConfig Model() {
  ModelConfig model;
  model.id = "model-1";
  model.model_id = "model-1";
  model.context_size = kContextTokens;
  return model;
}

ChatMessage User(const std::uint64_t id, std::string content) {
  ChatMessage message;
  message.id = id;
  message.role = MessageRole::user;
  message.content = std::move(content);
  return message;
}

ChatMessage Assistant(const std::uint64_t id, std::string content) {
  ChatMessage message;
  message.id = id;
  message.role = MessageRole::assistant;
  message.content = std::move(content);
  return message;
}

ChatMessage AssistantWithTool(const std::uint64_t id, std::string content) {
  auto message = Assistant(id, std::move(content));
  domain::ChatToolCall call;
  call.id = "call-" + std::to_string(id);
  call.name = "read_file";
  call.arguments_json = "{}";
  domain::ChatToolResult result;
  result.call_id = call.id;
  result.name = call.name;
  result.content = "ok";
  domain::AssistantToolEvent event;
  event.turn_index = 0;
  event.call = std::move(call);
  event.result = std::move(result);
  message.timeline.push_back(std::move(event));
  return message;
}

ChatMessage Tool(const std::uint64_t id, std::string content) {
  ChatMessage message;
  message.id = id;
  message.role = MessageRole::tool;
  message.content = std::move(content);
  return message;
}

ChatMessage Excluded(ChatMessage message) {
  message.exclude_from_context = true;
  return message;
}

// A hidden summary written back by an earlier compaction: `hidden` is the C++
// stand-in for the legacy "isHidden() && responseInputItemJson non-empty".
ChatMessage HiddenSummary(const std::uint64_t id) {
  auto message = User(id, "summary of earlier conversation");
  message.hidden = true;
  return message;
}

ChatMessage Progress(const std::uint64_t id,
                     const std::string_view status) {
  return domain::CompactProgressMessage(id, status);
}

std::vector<std::uint64_t> Ids(const std::vector<ChatMessage> &messages) {
  return MessageIdSet(messages);
}

std::size_t CountExcludedFromContext(const std::vector<ChatMessage> &messages) {
  std::size_t total = 0;
  for (const auto &message : messages) {
    if (message.exclude_from_context)
      ++total;
  }
  return total;
}

const ChatMessage *Find(const std::vector<ChatMessage> &messages,
                        const std::uint64_t id) {
  for (const auto &message : messages) {
    if (message.id == id)
      return &message;
  }
  return nullptr;
}

// ---------------------------------------------------------------------------
// ChatMessage-level compact block (port of ChatMessage.compactProgress)
// ---------------------------------------------------------------------------

void CompactProgressMessageMatchesLegacy() {
  const auto running = Progress(7U, domain::compact_status_running);
  EXPECT_EXPRESSION(running.role == MessageRole::assistant);
  EXPECT_EXPRESSION(running.content.empty());
  EXPECT_EXPRESSION(running.compact_status == domain::compact_status_running);
  // `compactProgress` passes streaming=(status == running), hidden=false,
  // excludeFromContext=true.
  EXPECT_EXPRESSION(running.streaming);
  EXPECT_EXPRESSION(!running.hidden);
  EXPECT_EXPRESSION(running.exclude_from_context);
  EXPECT_EXPRESSION(domain::IsCompactBlock(running));

  const auto done = Progress(8U, domain::compact_status_done);
  EXPECT_EXPRESSION(!done.streaming);
  EXPECT_EXPRESSION(done.compact_status == domain::compact_status_done);

  // `normalizeCompactStatus` collapses unknown values to "no block".
  const auto unknown = Progress(9U, "paused");
  EXPECT_EXPRESSION(unknown.compact_status.empty());
  EXPECT_EXPRESSION(!domain::IsCompactBlock(unknown));
  EXPECT_EXPRESSION(domain::NormalizeCompactStatus("done") == "done");
  EXPECT_EXPRESSION(domain::NormalizeCompactStatus("").empty());

  // `withCompactStatus` changes status and streaming only.
  const auto completed = domain::WithCompactStatus(
      running, domain::compact_status_done, false);
  EXPECT_EXPRESSION(completed.id == running.id);
  EXPECT_EXPRESSION(completed.exclude_from_context);
  EXPECT_EXPRESSION(completed.compact_status == domain::compact_status_done);
  EXPECT_EXPRESSION(!completed.streaming);

  // A progress block never costs context tokens (empty content, assistant
  // role, exclude_from_context=true), matching the legacy `isCompactBlock`
  // skip in ContextManager.
  const std::vector<ChatMessage> blocks{running};
  EXPECT_EXPRESSION(domain::EstimateContextTokens(blocks) == 0);
}

// ---------------------------------------------------------------------------
// Hard trigger (legacy lines 106-131)
// ---------------------------------------------------------------------------

void HardTriggerFollowsObservedTokens() {
  const std::vector<ChatMessage> messages{User(1U, "hello")};
  // 799 observed tokens is below the 80% ratio of a 1'000 token window.
  EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), messages, 799, {}));
  // 800 is the exact ratio: `usage >= maximum * COMPACT_TRIGGER_RATIO`.
  EXPECT_EXPRESSION(ShouldAutoCompactBeforeRequest(Model(), messages, 800, {}));
  EXPECT_EXPRESSION(ShouldAutoCompactBeforeRequest(Model(), messages, 900, {}));
}

void HardTriggerFallsBackToLocalEstimate() {
  // `8 + ceil(chars / 4)`: 3'160 chars == 798 tokens, 3'168 chars == 800.
  const std::vector<ChatMessage> below{
      User(1U, std::string(3'160U, 'a'))};
  const std::vector<ChatMessage> at_ratio{
      User(1U, std::string(3'168U, 'a'))};
  EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), below, 0, {}));
  EXPECT_EXPRESSION(ShouldAutoCompactBeforeRequest(Model(), at_ratio, 0, {}));
}

void HardTriggerSkipsExcludedPreservedAndCompacted() {
  // The only compactable message is the preserved tail.
  {
    const std::vector<ChatMessage> messages{User(1U, "current question")};
    const auto preserved = Ids(PreservedTail(messages, 1U));
    EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), messages, 900, preserved));
  }
  // The only compactable message left the context already.
  {
    const std::vector<ChatMessage> messages{
        Excluded(Assistant(1U, "old answer")), User(2U, "current")};
    const auto preserved = Ids(PreservedTail(messages, 2U));
    EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), messages, 900, preserved));
  }
  // A hidden summary produced by an earlier compaction is not compactable
  // content, so it never keeps the trigger alive on its own.
  {
    const std::vector<ChatMessage> messages{HiddenSummary(1U)};
    EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), messages, 900, {}));
  }
  // Neither is a finished progress block.
  {
    const std::vector<ChatMessage> messages{
        Progress(1U, domain::compact_status_done)};
    EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), messages, 900, {}));
  }
  // Reasoning or a tool call alone is compactable content (legacy line 126).
  {
    auto reasoning = Assistant(1U, "");
    reasoning.reasoning_content = "thinking";
    const std::vector<ChatMessage> messages{std::move(reasoning)};
    EXPECT_EXPRESSION(ShouldAutoCompactBeforeRequest(Model(), messages, 900, {}));
  }
  {
    const std::vector<ChatMessage> messages{AssistantWithTool(1U, "")};
    EXPECT_EXPRESSION(ShouldAutoCompactBeforeRequest(Model(), messages, 900, {}));
  }
  // Whitespace-only content is not (legacy `trim().length() > 0`).
  {
    const std::vector<ChatMessage> messages{Assistant(1U, "   \n\t ")};
    EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(Model(), messages, 900, {}));
  }
}

void HardTriggerRequiresModel() {
  const std::vector<ChatMessage> messages{User(1U, "hello")};
  EXPECT_EXPRESSION(!ShouldAutoCompactBeforeRequest(std::nullopt, messages, 900, {}));
}

void MidLoopTriggerUsesEmptyActiveUserMessageId() {
  // The last context message is the user message, so the mid-loop tail (empty
  // active id) preserves it and the trigger stays off.
  const std::vector<ChatMessage> only_user{User(1U, "question")};
  EXPECT_EXPRESSION(!ShouldAutoCompactMidLoop(Model(), only_user, 900));

  // With an assistant turn in between, the user message is no longer the
  // preserved tail and the trigger fires.
  const std::vector<ChatMessage> with_answer{User(1U, "question"),
                                             Assistant(2U, "answer")};
  EXPECT_EXPRESSION(ShouldAutoCompactMidLoop(Model(), with_answer, 900));
  EXPECT_EXPRESSION(!ShouldAutoCompactMidLoop(std::nullopt, with_answer, 900));
  EXPECT_EXPRESSION(!ShouldAutoCompactMidLoop(Model(), with_answer, 799));
}

// ---------------------------------------------------------------------------
// Soft trigger (legacy lines 139-164)
// ---------------------------------------------------------------------------

// `shouldSoftCompact` only fires with at least eight compactable messages
// (`SOFT_COMPACT_MIN_COMPACTABLE_MESSAGES`).
std::vector<ChatMessage> SoftTriggerConversation() {
  std::vector<ChatMessage> messages;
  messages.push_back(User(1U, "start"));
  for (std::uint64_t index = 0; index < 7U; ++index) {
    messages.push_back(Assistant(2U + index * 2U, "answer"));
    messages.push_back(User(3U + index * 2U, "follow up"));
  }
  // Nine compactable messages, no preserved tail (the last message is an
  // assistant reply without tool calls).
  messages.push_back(Assistant(99U, "final answer"));
  return messages;
}

void SoftTriggerHonoursRatioAndSwitch() {
  const auto messages = SoftTriggerConversation();
  const domain::AiBehaviorSettings behavior{};
  // 60% is inside the soft band [50%, 80%).
  EXPECT_EXPRESSION(ShouldAutoSoftCompactBeforeRequest(Model(), messages, 600, true, {},
                                            behavior.preserve_reasoning));
  // The switch from AiBehaviorSettingsRepository gates the whole trigger.
  EXPECT_EXPRESSION(!ShouldAutoSoftCompactBeforeRequest(Model(), messages, 600, false, {},
                                             false));
  // Below 50% nothing happens, at/above 80% the hard trigger owns it.
  EXPECT_EXPRESSION(!ShouldAutoSoftCompactBeforeRequest(Model(), messages, 499, true, {},
                                             false));
  EXPECT_EXPRESSION(!ShouldAutoSoftCompactBeforeRequest(Model(), messages, 800, true, {},
                                             false));
  // The legacy service also requires at least eight compactable messages.
  const std::vector<ChatMessage> too_short{User(1U, "hello"),
                                           User(2U, "again")};
  EXPECT_EXPRESSION(!ShouldAutoSoftCompactBeforeRequest(Model(), too_short, 600, true, {},
                                             false));
  // No model, no trigger.
  EXPECT_EXPRESSION(!ShouldAutoSoftCompactBeforeRequest(std::nullopt, messages, 600, true,
                                             {}, false));
}

void SoftTriggerSkipsWhenHeadIsEmpty() {
  // Every compactable message belongs to the preserved tail (the last message
  // is a tool result, so the tail starts at the assistant that called it), so
  // the split head has nothing left to compress.
  std::vector<ChatMessage> messages{Progress(1U, domain::compact_status_done),
                                    HiddenSummary(2U)};
  messages.push_back(AssistantWithTool(3U, ""));
  for (std::uint64_t index = 0; index < 7U; ++index)
    messages.push_back(Tool(4U + index, "tool output"));
  const auto preserved = Ids(PreservedTail(messages, std::nullopt));
  EXPECT_EXPRESSION(preserved.size() == 8U);
  // The threshold check still passes (eight compactable messages overall)...
  EXPECT_EXPRESSION(application::ContextCompactionService::ShouldSoftCompact(
      messages, kContextTokens, true, 600));
  // ...but the head is empty, so the trigger must not fire.
  EXPECT_EXPRESSION(!ShouldAutoSoftCompactBeforeRequest(Model(), messages, 600, true,
                                             preserved, true));
}

// ---------------------------------------------------------------------------
// Preserved tail (legacy lines 672-698)
// ---------------------------------------------------------------------------

void PreservedTailKeepsTheActiveUserMessage() {
  const std::vector<ChatMessage> messages{Assistant(1U, "answer"),
                                          User(2U, "current question")};
  const auto tail = PreservedTail(messages, 2U);
  EXPECT_EXPRESSION(tail.size() == 1U);
  EXPECT_EXPRESSION(tail.front().id == 2U);
  // An absent active id behaves like the legacy empty string: the last user
  // message is still preserved.
  const auto without_active = PreservedTail(messages, std::nullopt);
  EXPECT_EXPRESSION(without_active.size() == 1U);
  EXPECT_EXPRESSION(without_active.front().id == 2U);
  // A different active id means the last message is not the active request.
  EXPECT_EXPRESSION(PreservedTail(messages, 1U).empty());
}

void PreservedTailKeepsTheToolSequence() {
  const std::vector<ChatMessage> messages{
      User(1U, "question"), AssistantWithTool(2U, ""), Tool(3U, "result")};
  const auto tail = PreservedTail(messages, std::nullopt);
  EXPECT_EXPRESSION(tail.size() == 2U);
  EXPECT_EXPRESSION(tail[0].id == 2U);
  EXPECT_EXPRESSION(tail[1].id == 3U);
  // The search walks back past non-assistant messages.
  const std::vector<ChatMessage> extended{
      User(1U, "question"), AssistantWithTool(2U, ""), Tool(3U, "result"),
      Tool(4U, "result 2")};
  const auto longer = PreservedTail(extended, std::nullopt);
  EXPECT_EXPRESSION(longer.size() == 3U);
  EXPECT_EXPRESSION(longer.front().id == 2U);
  // A tool result without a preceding assistant tool call preserves nothing.
  const std::vector<ChatMessage> orphan{User(1U, "question"),
                                        Tool(2U, "orphan result")};
  EXPECT_EXPRESSION(PreservedTail(orphan, std::nullopt).empty());
}

void PreservedTailIgnoresExcludedMessagesAndOtherRoles() {
  // Only the last message that is still in context counts: the trailing
  // excluded user message is ignored, the active one is preserved.
  const std::vector<ChatMessage> messages{
      User(1U, "question"), Assistant(2U, "answer"), User(3U, "current"),
      Excluded(User(4U, "already compacted"))};
  const auto tail = PreservedTail(messages, 3U);
  EXPECT_EXPRESSION(tail.size() == 1U);
  EXPECT_EXPRESSION(tail.front().id == 3U);
  // The active request is not the last context message: nothing is preserved.
  EXPECT_EXPRESSION(PreservedTail(messages, 1U).empty());

  // A closing assistant reply is not a preserved tail.
  const std::vector<ChatMessage> answer_only{User(1U, "question"),
                                             Assistant(2U, "answer")};
  EXPECT_EXPRESSION(PreservedTail(answer_only, 1U).empty());

  // Everything excluded: no tail at all.
  const std::vector<ChatMessage> hidden{Excluded(User(1U, "question"))};
  EXPECT_EXPRESSION(PreservedTail(hidden, 1U).empty());
  EXPECT_EXPRESSION(PreservedTail({}, 1U).empty());
}

// ---------------------------------------------------------------------------
// Hard merge (legacy lines 450-539)
// ---------------------------------------------------------------------------

void MergeCompactionKeepsSummaryTailAndRetainedUsers() {
  const std::vector<ChatMessage> messages{
      User(1U, "old question"),
      Assistant(2U, "old answer"),
      User(3U, "recent question"),
      Progress(4U, domain::compact_status_running),
      User(5U, "current question"),
  };
  const auto preserved = Ids(PreservedTail(messages, 5U));
  EXPECT_EXPRESSION(preserved.size() == 1U);
  // baseSnapshot is the conversation without the preserved tail.
  std::vector<ChatMessage> base;
  for (const auto &message : messages) {
    if (message.id != 5U)
      base.push_back(message);
  }
  const auto base_ids = Ids(base);
  const auto retained = RetainedUserMessageIds(base);
  EXPECT_EXPRESSION(retained.size() == 2U); // recent question + old question, newest first

  const CompactionWriteback writeback{.progress_id = 4U,
                                      .summary_id = 90U,
                                      .summary_content = "compact summary"};
  const auto merged = MergeCompaction(messages, base_ids, preserved, retained,
                                      writeback);

  // Ordering follows the legacy loop: retained messages stay where they were,
  // every other base message is excluded in place, then the summary, the
  // preserved tail and the completed progress block.
  EXPECT_EXPRESSION(merged.size() == 6U);
  EXPECT_EXPRESSION(merged[0].id == 1U);
  EXPECT_EXPRESSION(!merged[0].exclude_from_context); // retained user message verbatim
  EXPECT_EXPRESSION(merged[1].id == 2U);
  EXPECT_EXPRESSION(merged[1].exclude_from_context); // summarized away
  EXPECT_EXPRESSION(merged[2].id == 3U);
  EXPECT_EXPRESSION(!merged[2].exclude_from_context); // retained user message verbatim
  const auto *summary = Find(merged, 90U);
  EXPECT_EXPRESSION(summary != nullptr);
  EXPECT_EXPRESSION(summary->role == MessageRole::user);
  EXPECT_EXPRESSION(summary->content == "compact summary");
  // The summary must join the context, otherwise the model sees the history
  // as wiped (legacy line 495).
  EXPECT_EXPRESSION(!summary->exclude_from_context);
  EXPECT_EXPRESSION(summary->hidden);
  const auto *tail = Find(merged, 5U);
  EXPECT_EXPRESSION(tail != nullptr);
  EXPECT_EXPRESSION(!tail->exclude_from_context);
  EXPECT_EXPRESSION(!tail->hidden);
  // The running block was removed from its old position and re-appended done.
  EXPECT_EXPRESSION(merged.back().id == 4U);
  EXPECT_EXPRESSION(merged.back().compact_status == domain::compact_status_done);
  EXPECT_EXPRESSION(!merged.back().streaming);
  EXPECT_EXPRESSION(merged.back().exclude_from_context);
  // Only the summarized assistant turn and the progress block are excluded.
  EXPECT_EXPRESSION(CountExcludedFromContext(merged) == 2U);
}

void MergeCompactionWithoutRetainedUsersExcludesEverything() {
  // With an empty retained set every base message leaves the context; the
  // summary is still the only thing the model sees.
  const std::vector<ChatMessage> messages{User(1U, "old"), Assistant(2U, "a")};
  const auto base_ids = Ids(messages);
  const CompactionWriteback writeback{.progress_id = 7U,
                                      .summary_id = 91U,
                                      .summary_content = "summary"};
  const auto merged =
      MergeCompaction(messages, base_ids, {}, /*retained_ids=*/{}, writeback);
  EXPECT_EXPRESSION(merged.size() == 4U);
  EXPECT_EXPRESSION(merged[0].exclude_from_context);
  EXPECT_EXPRESSION(merged[1].exclude_from_context);
  EXPECT_EXPRESSION(!merged[2].exclude_from_context); // the summary replaces them
  EXPECT_EXPRESSION(merged[2].id == 91U);
  // The progress block did not exist in the transcript: the legacy helper
  // synthesises a fresh completed block instead of dropping it.
  EXPECT_EXPRESSION(merged.back().id == 7U);
  EXPECT_EXPRESSION(merged.back().compact_status == domain::compact_status_done);
  EXPECT_EXPRESSION(CountExcludedFromContext(merged) == 3U);
}

void CompletedCompactProgressReusesTheRunningBlock() {
  const std::vector<ChatMessage> messages{Progress(1U,
                                                   domain::compact_status_running)};
  const auto completed = CompletedCompactProgress(messages, 1U);
  EXPECT_EXPRESSION(completed.id == 1U);
  EXPECT_EXPRESSION(completed.compact_status == domain::compact_status_done);
  EXPECT_EXPRESSION(!completed.streaming);
  // Missing block: a fresh done block is created (legacy lines 664-670).
  const auto fresh = CompletedCompactProgress(messages, 42U);
  EXPECT_EXPRESSION(fresh.id == 42U);
  EXPECT_EXPRESSION(fresh.compact_status == domain::compact_status_done);
}

// ---------------------------------------------------------------------------
// Soft merge (legacy lines 546-628)
// ---------------------------------------------------------------------------

void MergeSoftCompactionKeepsTailVerbatim() {
  const std::vector<ChatMessage> messages{
      User(1U, "old question"),
      Assistant(2U, "old answer"),
      User(3U, "recent question"),
      Assistant(4U, "recent answer"),
      Progress(5U, domain::compact_status_running),
      User(6U, "current question"),
  };
  const std::vector<ChatMessage> head_snapshot{messages[0], messages[1]};
  const auto head_ids = Ids(head_snapshot);
  const auto preserved = Ids(PreservedTail(messages, 6U));
  const CompactionWriteback writeback{.progress_id = 5U,
                                      .summary_id = 92U,
                                      .summary_content = "head summary"};
  const auto merged =
      MergeSoftCompaction(messages, head_ids, preserved, writeback);

  // [head(excluded)] + [summary] + [tail] + [preserved] + [progress done]
  EXPECT_EXPRESSION(merged.size() == 7U);
  EXPECT_EXPRESSION(merged[0].id == 1U);
  EXPECT_EXPRESSION(merged[0].exclude_from_context);
  EXPECT_EXPRESSION(merged[1].id == 2U);
  EXPECT_EXPRESSION(merged[1].exclude_from_context);
  EXPECT_EXPRESSION(merged[2].id == 92U);
  EXPECT_EXPRESSION(merged[2].content == "head summary");
  EXPECT_EXPRESSION(!merged[2].exclude_from_context);
  EXPECT_EXPRESSION(merged[2].hidden);
  EXPECT_EXPRESSION(merged[3].id == 3U);
  EXPECT_EXPRESSION(merged[4].id == 4U);
  EXPECT_EXPRESSION(!merged[3].exclude_from_context);
  EXPECT_EXPRESSION(!merged[4].exclude_from_context);
  EXPECT_EXPRESSION(merged[5].id == 6U);
  EXPECT_EXPRESSION(!merged[5].exclude_from_context);
  const auto *progress = Find(merged, 5U);
  EXPECT_EXPRESSION(progress != nullptr);
  EXPECT_EXPRESSION(progress->compact_status == domain::compact_status_done);
  // The progress block sits last, not at its original position.
  EXPECT_EXPRESSION(merged.back().id == 5U);
}

// ---------------------------------------------------------------------------
// Failure and cancellation (legacy lines 630-662, 462-467)
// ---------------------------------------------------------------------------

void FailureMarksProgressErrorAndAppendsTheNotice() {
  const std::vector<ChatMessage> messages{User(1U, "question"),
                                          Progress(2U, domain::compact_status_running),
                                          Assistant(3U, "")};
  const auto failed = application::MarkCompactionError(
      messages, 2U, 40U, CompactFailureNoSummary());
  EXPECT_EXPRESSION(failed.size() == 4U);
  const auto *progress = Find(failed, 2U);
  EXPECT_EXPRESSION(progress != nullptr);
  EXPECT_EXPRESSION(progress->compact_status == domain::compact_status_error);
  EXPECT_EXPRESSION(!progress->streaming);
  const auto *notice = Find(failed, 40U);
  EXPECT_EXPRESSION(notice != nullptr);
  EXPECT_EXPRESSION(notice->role == MessageRole::assistant);
  EXPECT_EXPRESSION(notice->content == "上下文压缩失败：模型没有返回摘要。");
  // The notice is excluded from the context but stays visible (legacy line
  // 645-646).
  EXPECT_EXPRESSION(notice->exclude_from_context);
  EXPECT_EXPRESSION(!notice->hidden);
  // The original turns are untouched.
  EXPECT_EXPRESSION(Find(failed, 1U) != nullptr && Find(failed, 3U) != nullptr);
}

void CancellationMarksProgressErrorWithoutNotice() {
  const std::vector<ChatMessage> messages{
      User(1U, "question"),
      Progress(2U, domain::compact_status_running)};
  const auto cancelled = application::MarkCompactionCancelled(messages, 2U);
  EXPECT_EXPRESSION(cancelled.size() == 2U);
  EXPECT_EXPRESSION(cancelled[1].compact_status == domain::compact_status_error);
  EXPECT_EXPRESSION(!cancelled[1].streaming);
}

void FailureCopyMatchesLegacy() {
  EXPECT_EXPRESSION(CompactFailureNoSummary() == "上下文压缩失败：模型没有返回摘要。");
  EXPECT_EXPRESSION(CompactFailureMessage("boom") == "上下文压缩失败：boom");
  EXPECT_EXPRESSION(CompactFailureOutOfMemory() ==
         "上下文过大，压缩时内存不足，请手动清理早期对话后重试");
  // Cancelled compactions never resume; a failed one resumes unless the run
  // was cancelled (legacy lines 650-657).
  EXPECT_EXPRESSION(!ShouldResumeAfterCompaction(CompactionResumeMode::none, false));
  EXPECT_EXPRESSION(ShouldResumeAfterCompaction(CompactionResumeMode::initial_request,
                                     false));
  EXPECT_EXPRESSION(!ShouldResumeAfterCompaction(CompactionResumeMode::initial_request,
                                      true));
  EXPECT_EXPRESSION(ShouldResumeAfterCompaction(CompactionResumeMode::tool_loop, false));
  EXPECT_EXPRESSION(!ShouldResumeAfterCompaction(CompactionResumeMode::tool_loop, true));
}

// ---------------------------------------------------------------------------
// Progress block projection (ContextCompactBlockView)
// ---------------------------------------------------------------------------

void CompactBlockProjectionMatchesLegacy() {
  const auto running = presentation::PresentCompactProgress(
      domain::compact_status_running);
  EXPECT_EXPRESSION(running.running && !running.failed);
  EXPECT_EXPRESSION(running.show_progress_bar && !running.show_status_icon);
  EXPECT_EXPRESSION(!running.danger);
  EXPECT_EXPRESSION(running.status_icon == CompactStatusIcon::check);

  const auto done =
      presentation::PresentCompactProgress(domain::compact_status_done);
  EXPECT_EXPRESSION(!done.running && !done.failed);
  EXPECT_EXPRESSION(!done.show_progress_bar && done.show_status_icon);
  EXPECT_EXPRESSION(!done.danger);
  EXPECT_EXPRESSION(done.status_icon == CompactStatusIcon::check);

  const auto error =
      presentation::PresentCompactProgress(domain::compact_status_error);
  EXPECT_EXPRESSION(!error.running && error.failed);
  EXPECT_EXPRESSION(!error.show_progress_bar && error.show_status_icon);
  EXPECT_EXPRESSION(error.danger);
  EXPECT_EXPRESSION(error.status_icon == CompactStatusIcon::close);

  // An empty status defaults to running (`bind` line 53).
  const auto empty = presentation::PresentCompactProgress("");
  EXPECT_EXPRESSION(empty.running);
  EXPECT_EXPRESSION(empty.show_progress_bar);

  // Geometry of the legacy constructor.
  const auto metrics = presentation::CompactBlockMetricsDefault();
  EXPECT_EXPRESSION(metrics.min_height == 48.0F);
  EXPECT_EXPRESSION(metrics.vertical_padding == 6.0F);
  EXPECT_EXPRESSION(metrics.icon_slot == 18.0F);
  EXPECT_EXPRESSION(metrics.archive_icon_size == 14.0F);
  EXPECT_EXPRESSION(metrics.label_left_margin == 6.0F);
  EXPECT_EXPRESSION(metrics.label_size == 13.0F);
  EXPECT_EXPRESSION(metrics.progress_size == 18.0F);
  EXPECT_EXPRESSION(metrics.status_icon_size == 13.0F);

  // Compact blocks are their own timeline block and never render as an
  // assistant reply.
  const auto block = Progress(1U, domain::compact_status_running);
  EXPECT_EXPRESSION(presentation::IsCompactTimelineBlock(block));
  EXPECT_EXPRESSION(!presentation::IsCompactTimelineBlock(User(2U, "hi")));
}

void CompactableBaseMessageSelectionMatchesLegacy() {
  EXPECT_EXPRESSION(!HasCompactableBaseMessages({}));
  EXPECT_EXPRESSION(!HasCompactableBaseMessages(
      std::vector<ChatMessage>{Progress(1U, domain::compact_status_done)}));
  EXPECT_EXPRESSION(!HasCompactableBaseMessages(
      std::vector<ChatMessage>{HiddenSummary(1U)}));
  EXPECT_EXPRESSION(!HasCompactableBaseMessages(
      std::vector<ChatMessage>{Excluded(User(1U, "gone"))}));
  EXPECT_EXPRESSION(!HasCompactableBaseMessages(
      std::vector<ChatMessage>{Assistant(1U, "  ")}));
  EXPECT_EXPRESSION(HasCompactableBaseMessages(
      std::vector<ChatMessage>{Assistant(1U, "text")}));
  EXPECT_EXPRESSION(HasCompactableBaseMessages(
      std::vector<ChatMessage>{AssistantWithTool(1U, "")}));
}

// The port is append-only, so the legacy "summary, then the preserved tail"
// order is reproduced by excluding the originals and re-appending hidden,
// in-context copies. This guards the ordering the send path depends on.
void ApplyCompactionKeepsTheTailAfterTheSummary() {
  auto store = std::make_unique<infrastructure::InMemoryConversationStore>();
  auto *store_view = store.get();
  application::ChatSession session{std::move(store)};
  static_cast<void>(session.Send("old question"));
  static_cast<void>(session.AppendAssistant("old answer"));
  const auto current = session.Send("current question");
  EXPECT_EXPRESSION(current);
  const auto current_id = current->id;

  const auto messages = std::vector<ChatMessage>{session.Messages().begin(),
                                                 session.Messages().end()};
  const auto preserved = PreservedTail(messages, current_id);
  EXPECT_EXPRESSION(preserved.size() == 1U);

  std::vector<std::uint64_t> excluded;
  for (const auto &message : messages) {
    if (message.id != current_id)
      excluded.push_back(message.id);
  }
  excluded.push_back(current_id); // the original tail leaves the context
  session.ApplyCompaction(std::move(excluded), "the summary",
                          std::vector<ChatMessage>{preserved});

  const auto compacted = std::vector<ChatMessage>{session.Messages().begin(),
                                                  session.Messages().end()};
  // The transcript keeps the three original bubbles plus a hidden summary and a
  // hidden tail copy.
  EXPECT_EXPRESSION(compacted.size() == 5U);
  const auto *summary = Find(compacted, compacted[3].id);
  EXPECT_EXPRESSION(summary != nullptr);
  EXPECT_EXPRESSION(summary->content == "the summary");
  // Legacy builds the summary as `Role.USER`
  // (`ContextCompactionController.java:500`). The runtime write-back went out
  // as assistant for a while because only this field was left unchecked.
  EXPECT_EXPRESSION(summary->role == MessageRole::user);
  EXPECT_EXPRESSION(summary->hidden);
  // The summary enters the model context right before the preserved tail.
  EXPECT_EXPRESSION(!summary->exclude_from_context);
  const auto *tail = Find(compacted, compacted[4].id);
  EXPECT_EXPRESSION(tail != nullptr);
  EXPECT_EXPRESSION(tail->role == MessageRole::user);
  EXPECT_EXPRESSION(tail->content == "current question");
  EXPECT_EXPRESSION(tail->hidden);
  EXPECT_EXPRESSION(!tail->exclude_from_context);
  // The original current question stays visible but left the context.
  const auto *original = Find(compacted, current_id);
  EXPECT_EXPRESSION(original != nullptr);
  EXPECT_EXPRESSION(!original->hidden);
  EXPECT_EXPRESSION(original->exclude_from_context);
  static_cast<void>(store_view);
}

// Soft compaction summarizes only the oldest slice, and legacy ordered the
// result "summary -> recent context -> current question"
// (`ContextCompactionController.java:606-615`). Appending the summary would
// put it after the very context it is supposed to precede.
void ApplyCompactionPlacesTheSummaryAfterItsSlice() {
  auto store = std::make_unique<infrastructure::InMemoryConversationStore>();
  application::ChatSession session{std::move(store)};
  static_cast<void>(session.Send("old question"));
  static_cast<void>(session.AppendAssistant("old answer"));
  static_cast<void>(session.Send("recent question"));
  static_cast<void>(session.AppendAssistant("recent answer"));
  const auto current = session.Send("current question");
  EXPECT_EXPRESSION(current.has_value());

  const auto before = std::vector<ChatMessage>{session.Messages().begin(),
                                               session.Messages().end()};
  EXPECT_EXPRESSION(before.size() == 5U);
  const auto head_end = before[1].id;   // "old answer" closes the head
  const auto anchor_index = 1U;

  std::vector<std::uint64_t> excluded{before[0].id, before[1].id};
  session.ApplyCompaction(std::move(excluded), "the summary",
                          std::vector<ChatMessage>{}, head_end);

  const auto after = std::vector<ChatMessage>{session.Messages().begin(),
                                              session.Messages().end()};
  EXPECT_EXPRESSION(after.size() == 6U);
  // The summary sits immediately after the anchor, not at the end.
  EXPECT_EXPRESSION(after[anchor_index + 1].content == "the summary");
  EXPECT_EXPRESSION(after[anchor_index + 1].hidden);
  EXPECT_EXPRESSION(!after[anchor_index + 1].exclude_from_context);
  EXPECT_EXPRESSION(after[anchor_index + 2].content == "recent question");
  // The tail keeps its order and stays in context.
  EXPECT_EXPRESSION(after.back().content == "current question");
  for (std::size_t index = anchor_index + 2; index < after.size(); ++index)
    EXPECT_EXPRESSION(!after[index].exclude_from_context);
}

// A zero anchor keeps the append behaviour the hard path relies on.
void ApplyCompactionWithoutAnAnchorStillAppends() {
  auto store = std::make_unique<infrastructure::InMemoryConversationStore>();
  application::ChatSession session{std::move(store)};
  static_cast<void>(session.Send("question"));
  static_cast<void>(session.AppendAssistant("answer"));

  const auto before = std::vector<ChatMessage>{session.Messages().begin(),
                                               session.Messages().end()};
  session.ApplyCompaction({before[0].id}, "the summary",
                          std::vector<ChatMessage>{});
  const auto after = std::vector<ChatMessage>{session.Messages().begin(),
                                              session.Messages().end()};
  EXPECT_EXPRESSION(after.size() == 3U);
  EXPECT_EXPRESSION(after.back().content == "the summary");
}

} // namespace

TEST(auto_compaction_tests, LegacySuite) {
  CompactProgressMessageMatchesLegacy();

  HardTriggerFollowsObservedTokens();
  HardTriggerFallsBackToLocalEstimate();
  HardTriggerSkipsExcludedPreservedAndCompacted();
  HardTriggerRequiresModel();
  MidLoopTriggerUsesEmptyActiveUserMessageId();

  SoftTriggerHonoursRatioAndSwitch();
  SoftTriggerSkipsWhenHeadIsEmpty();

  PreservedTailKeepsTheActiveUserMessage();
  PreservedTailKeepsTheToolSequence();
  PreservedTailIgnoresExcludedMessagesAndOtherRoles();

  MergeCompactionKeepsSummaryTailAndRetainedUsers();
  MergeCompactionWithoutRetainedUsersExcludesEverything();
  CompletedCompactProgressReusesTheRunningBlock();

  MergeSoftCompactionKeepsTailVerbatim();

  FailureMarksProgressErrorAndAppendsTheNotice();
  CancellationMarksProgressErrorWithoutNotice();
  FailureCopyMatchesLegacy();

  CompactBlockProjectionMatchesLegacy();
  CompactableBaseMessageSelectionMatchesLegacy();
  ApplyCompactionKeepsTheTailAfterTheSummary();
  ApplyCompactionPlacesTheSummaryAfterItsSlice();
  ApplyCompactionWithoutAnAnchorStillAppends();

  std::cout << "auto_compaction_tests passed\n";
  return;
}
