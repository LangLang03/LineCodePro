#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "domain/app_state.h"
#include "domain/behavior_settings.h"
#include "domain/model_config.h"

namespace linecode::application {

// Pure port of the trigger and merge half of
// `cn.lineai.mvp.ContextCompactionController` (757 lines). Everything here is a
// deterministic function over the conversation, so the orchestration is
// testable without a model, a store, or a coroutine.
//
// Reference lines are the legacy file
// `app/src/main/java/cn/lineai/mvp/ContextCompactionController.java`.

// Port of `ContextCompactionController.getAutoCompactPreservedTail`
// (legacy lines 672-698): the tail that survives a hard compaction verbatim.
//   * every message with `exclude_from_context` is ignored;
//   * when the last remaining message is a USER message and the active user id
//     is absent or equals it, that single message is the tail;
//   * when the last remaining message is a TOOL message, the tail starts at the
//     closest preceding ASSISTANT message carrying a tool call;
//   * otherwise the tail is empty.
[[nodiscard]] std::vector<domain::ChatMessage>
PreservedTail(const std::vector<domain::ChatMessage> &messages,
              std::optional<std::uint64_t> active_user_message_id);

// Port of `messageIdSet` (legacy lines 700-711).
[[nodiscard]] std::vector<std::uint64_t>
MessageIdSet(std::span<const domain::ChatMessage> messages);

// Legacy lines 476-481: `selectRecentUserMessages(baseSnapshot,
// COMPACT_USER_MESSAGE_MAX_TOKENS, contextManager)` followed by `messageIdSet`.
// The budget constant lives in `ContextCompactionService`.
[[nodiscard]] std::vector<std::uint64_t>
RetainedUserMessageIds(const std::vector<domain::ChatMessage> &base_snapshot);

// Port of `shouldAutoCompactBeforeRequest` (legacy lines 106-131), the hard 80%
// trigger that runs before a user request.
//
// `preserved_tail_ids` is `MessageIdSet(PreservedTail(messages,
// active_user_message_id))`; `include_reasoning` is
// `AiBehaviorSettings.preserve_reasoning` and `observed_input_tokens` is the
// server-reported input token count (`TokenUsageTracker.lastInputTokens()`,
// zero when nothing was observed).
[[nodiscard]] bool ShouldAutoCompactBeforeRequest(
    const std::optional<domain::ModelConfig> &model,
    const std::vector<domain::ChatMessage> &messages, int observed_input_tokens,
    std::span<const std::uint64_t> preserved_tail_ids,
    bool include_reasoning = true);

// Port of `shouldAutoSoftCompactBeforeRequest` (legacy lines 139-164). The
// legacy gate `AiBehaviorSettingsRepository.get().isSoftCompactionEnabled()` is
// passed in as `soft_compaction_enabled`. Only fires between the 50% soft ratio
// and the 80% hard ratio, and only when the split head still holds compactable
// content.
[[nodiscard]] bool ShouldAutoSoftCompactBeforeRequest(
    const std::optional<domain::ModelConfig> &model,
    const std::vector<domain::ChatMessage> &messages, int observed_input_tokens,
    bool soft_compaction_enabled,
    std::span<const std::uint64_t> preserved_tail_ids,
    bool include_reasoning = true);

// Port of `shouldAutoCompactMidLoop` (legacy lines 234-259). The preserved tail
// is computed with an empty active user message id, exactly like the legacy
// call `getAutoCompactPreservedTail("")`.
[[nodiscard]] bool ShouldAutoCompactMidLoop(
    const std::optional<domain::ModelConfig> &model,
    const std::vector<domain::ChatMessage> &messages, int observed_input_tokens,
    bool include_reasoning = true);

// Port of `hasCompactableBaseMessages` (legacy lines 713-736). A hidden summary
// produced by an earlier compaction (the C++ stand-in for "isHidden() &&
// responseInputItemJson.length() > 0") and a progress block are never
// compactable content.
[[nodiscard]] bool
HasCompactableBaseMessages(std::span<const domain::ChatMessage> messages);

// `message.getContent().trim().length() > 0 ||
//  message.getReasoningContent().trim().length() > 0 ||
//  message.hasToolCalls()` (legacy lines 126, 254, 728-731).
[[nodiscard]] bool
HasCompactableContent(const domain::ChatMessage &message) noexcept;

// The summary written back by a compaction: `host.nextId()` for the new
// message plus `result.getSummaryContent()`. `progress_id` is the id of the
// running progress block that must flip to `done`.
struct CompactionWriteback final {
  std::uint64_t progress_id{};
  std::uint64_t summary_id{};
  std::string summary_content;
};

// Port of `finishContextCompaction` (legacy lines 450-539): the hard-compaction
// merge.
//   * messages in `base_snapshot_ids` that were not retained become
//     `exclude_from_context = true`;
//   * `retained_ids` (the recent user messages picked by
//     `SelectRecentUserMessages`) stay verbatim;
//   * the summary is appended as a NEW message with
//     `exclude_from_context = false` (legacy line 495-516);
//   * `preserved_ids` are appended last, verbatim;
//   * the progress block is re-appended with status `done`.
[[nodiscard]] std::vector<domain::ChatMessage> MergeCompaction(
    const std::vector<domain::ChatMessage> &messages,
    std::span<const std::uint64_t> base_snapshot_ids,
    std::span<const std::uint64_t> preserved_ids,
    std::span<const std::uint64_t> retained_ids,
    const CompactionWriteback &writeback);

// Port of `finishSoftContextCompaction` (legacy lines 546-628): the
// soft-compaction merge. Only the head is excluded, the summary is inserted
// between head and tail, the tail stays verbatim, then the preserved tail and
// the completed progress block.
[[nodiscard]] std::vector<domain::ChatMessage> MergeSoftCompaction(
    const std::vector<domain::ChatMessage> &messages,
    std::span<const std::uint64_t> head_snapshot_ids,
    std::span<const std::uint64_t> preserved_ids,
    const CompactionWriteback &writeback);

// Port of the completed progress block (legacy lines 664-670): the existing
// running block when it is still in the transcript, otherwise a fresh one.
[[nodiscard]] domain::ChatMessage
CompletedCompactProgress(std::span<const domain::ChatMessage> messages,
                         std::uint64_t progress_id);

// Legacy `ResumeMode` (lines 203-207).
enum class CompactionResumeMode : std::uint8_t {
  none,
  initial_request,
  tool_loop,
};

// Legacy lines 650-657 / 527-534: the original request continues only when the
// compaction was not cancelled.
[[nodiscard]] bool
ShouldResumeAfterCompaction(CompactionResumeMode mode,
                            bool cancelled) noexcept;

// Port of `failContextCompaction` (legacy lines 630-662): the progress block
// becomes `error`, then the failure text is appended as an assistant message
// that is excluded from the context.
[[nodiscard]] std::vector<domain::ChatMessage>
MarkCompactionError(const std::vector<domain::ChatMessage> &messages,
                    std::uint64_t progress_id, std::uint64_t notice_id,
                    std::string message);

// Port of the cancellation branch (legacy lines 462-467 / 557-562): the
// progress block becomes `error` and nothing else is written.
[[nodiscard]] std::vector<domain::ChatMessage>
MarkCompactionCancelled(const std::vector<domain::ChatMessage> &messages,
                        std::uint64_t progress_id);

// The failure text for "the model returned no summary" (legacy line 473).
[[nodiscard]] std::string CompactFailureNoSummary();

// The failure text for a compaction exception (legacy line 342).
[[nodiscard]] std::string CompactFailureMessage(std::string_view detail);

// The failure text for the out-of-memory fallback (legacy line 359).
[[nodiscard]] std::string CompactFailureOutOfMemory();

} // namespace linecode::application
