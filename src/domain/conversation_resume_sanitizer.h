#pragma once

// Port of `cn.lineai.mvp.ConversationResumeSanitizer`
// (`app/src/main/java/cn/lineai/mvp/ConversationResumeSanitizer.java`).
//
// The legacy sanitizer runs before a conversation is handed to the session
// store, so a process killed in the middle of a generation cannot leave
// "half-finished" persisted state behind: messages stuck in `streaming`,
// compaction blocks stuck in `running`, tool results parked in a
// running/pending review state, assistant tool calls without a matching tool
// result and `linecode_agent_progress` / `linecode_agent_pipeline_progress`
// payloads that still claim to be running all get repaired, using the
// "上次生成已中断。" notice to fill the gaps.
//
// The records below mirror the legacy persistence records the sanitizer
// operated on (`cn.lineai.data.repository.ConversationRecord` /
// `MessageRecord`): string ids, `hidden` / `exclude_from_context` flags and a
// `raw_json` payload that owns `compact_status`, `review_state` and
// `tool_calls`. The C++ conversation store loads rows in exactly this shape
// (see `StoredMessage` in `infrastructure/sqlite_conversation_store.cpp`), so
// the records are the direct port target; `domain::ChatMessage` cannot carry
// `tool_call_id`, `tool_name` or a per-message `review_state`.

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "domain/app_state.h"

namespace linecode::domain {

// Port of `ConversationResumeSanitizer.FALLBACK_TERMINATED_MESSAGE`
// (`ConversationResumeSanitizer.java:13`). The Android resource
// `R.string.message_generation_interrupted` (`values-zh/strings.xml:1098`)
// carries the same text.
inline constexpr std::string_view fallback_terminated_message =
    "上次生成已中断。";

// Port of `ConversationResumeSanitizer.SanitizedPayload`
// (`ConversationResumeSanitizer.java:422-444`).
struct SanitizedPayload final {
  std::string content;
  bool changed{};
  bool error{};

  bool operator==(const SanitizedPayload &) const = default;
};

// Port of the legacy `MessageRecord` fields the sanitizer reads and writes
// (`ConversationResumeSanitizer.java:62-111`, `268-289`). Declaration order
// matches the legacy constructor argument order so designated initializers
// read like the Java call sites.
struct ResumeMessageRecord final {
  std::string id;
  MessageRole role{MessageRole::user};
  std::string content;
  std::string reasoning_content;
  std::int64_t timestamp{};
  bool streaming{};
  bool hidden{};
  bool exclude_from_context{};
  std::string tool_call_id;
  std::string tool_name;
  bool error{};
  // Legacy `MessageRecord.rawJson`: carries `compact_status`, `review_state`
  // and `tool_calls`.
  std::string raw_json;

  bool operator==(const ResumeMessageRecord &) const = default;
};

// Port of the legacy `ConversationRecord` (`ConversationResumeSanitizer.java:
// 18-60`). `updated_at` timestamps every recovered tool result
// (`ConversationResumeSanitizer.java:238`).
struct ResumeConversationRecord final {
  std::string id;
  std::string title;
  std::string project_id;
  std::int64_t created_at{};
  std::int64_t updated_at{};
  bool current{};
  std::string raw_json;
  std::vector<ResumeMessageRecord> messages;

  bool operator==(const ResumeConversationRecord &) const = default;
};

// Port of `ConversationResumeSanitizer.Result`
// (`ConversationResumeSanitizer.java:404-420`). When nothing needed repair the
// input conversation is returned unchanged and `changed` stays false.
struct ResumeSanitizeResult final {
  ResumeConversationRecord conversation;
  bool changed{};
};

// Port of the `terminatedMessage == null || trim().isEmpty()` fallback
// (`ConversationResumeSanitizer.java:22-24`).
[[nodiscard]] std::string
ResolveTerminatedMessage(std::string_view terminated_message);

// Port of `isUnfinishedReviewState` (`ConversationResumeSanitizer.java:348-352`):
// a review still in flight, or one accepted with nothing to show for it.
[[nodiscard]] bool IsUnfinishedReviewState(std::string_view state,
                                           std::string_view content);

// The same repairs applied to the model the conversation store actually hands
// to the session. `domain::ChatMessage` keeps a tool call and its result
// together in one `AssistantToolEvent`, so the legacy "assistant tool call
// with no matching tool result" case is an event whose result is empty.
//
// Returns whether anything needed repairing. Recovered results carry the
// terminated notice and the error flag, exactly like the legacy synthesized
// `MessageRecord`s.
[[nodiscard]] bool SanitizeResumeMessages(
    std::vector<ChatMessage> &messages, std::string_view terminated_message);

// Port of `ConversationResumeSanitizer.sanitizeId`
// (`ConversationResumeSanitizer.java:399-402`): every character outside
// `[A-Za-z0-9_-]` becomes `_`, an empty result becomes "unknown".
[[nodiscard]] std::string SanitizeResumeId(std::string_view value);

// Port of `ConversationResumeSanitizer.sanitizeToolContent`
// (`ConversationResumeSanitizer.java:113-131`). `terminated_message` is
// expected to be already resolved by the caller, exactly like the legacy
// `GenerationFlowController.markRunningAgentProgressStopped` call site
// (`GenerationFlowController.java:586`). Untouched payloads are returned
// byte-for-byte.
[[nodiscard]] SanitizedPayload
SanitizeToolContent(std::string_view content,
                    std::string_view terminated_message);

// Port of `ConversationResumeSanitizer.sanitize`
// (`ConversationResumeSanitizer.java:18-60`). `now_millis` stands in for
// `System.currentTimeMillis()` when `conversation.updated_at <= 0`, which
// keeps the recovered timestamps deterministic in tests.
[[nodiscard]] ResumeSanitizeResult
Sanitize(ResumeConversationRecord conversation,
         std::string_view terminated_message, std::int64_t now_millis);

[[nodiscard]] ResumeSanitizeResult
Sanitize(ResumeConversationRecord conversation,
         std::string_view terminated_message);

} // namespace linecode::domain
