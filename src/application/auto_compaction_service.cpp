#include "application/auto_compaction_service.h"

#include <algorithm>
#include <ranges>
#include <utility>

#include "application/context_compaction.h"
#include "domain/compaction_progress.h"
#include "domain/context_usage.h"

namespace linecode::application {
namespace {

// Legacy `message.getContent().trim().length() > 0` and the reasoning
// equivalent. `domain::ChatMessage` content is byte-identical to the legacy
// string, so a whitespace-only test is enough.
[[nodiscard]] bool IsBlank(const std::string_view value) {
  return std::ranges::all_of(value, [](const unsigned char character) {
    return character == ' ' || character == '\t' || character == '\n' ||
           character == '\r' || character == '\f' || character == '\v';
  });
}

// Legacy `message.hasToolCalls() || !message.getToolResults().isEmpty()`: the
// C++ conversation keeps both on the assistant timeline.
[[nodiscard]] bool HasToolActivity(const domain::ChatMessage &message) {
  return std::ranges::any_of(message.timeline, [](const auto &event) {
    return std::holds_alternative<domain::AssistantToolEvent>(event);
  });
}

// Legacy "already compacted": `message.isHidden() &&
// message.getResponseInputItemJson().length() > 0`. The C++ conversation model
// has no `responseInputItemJson` column, so `hidden` is the marker (the same
// rule `ContextCompactionService::CompactableMessages` already applies).
[[nodiscard]] bool IsCompactedArtifact(const domain::ChatMessage &message) {
  return message.hidden;
}

[[nodiscard]] bool ContainsId(const std::span<const std::uint64_t> ids,
                              const std::uint64_t id) {
  return std::ranges::find(ids, id) != ids.end();
}

// Last message that would enter the model context, matching the legacy
// `getAutoCompactPreservedTail` loop over `!isExcludeFromContext()`. Progress
// blocks are excluded from the context (`compactProgress` sets
// excludeFromContext=true) and are never a preserved tail.
[[nodiscard]] std::vector<const domain::ChatMessage *>
ContextMessages(const std::vector<domain::ChatMessage> &messages) {
  std::vector<const domain::ChatMessage *> visible;
  visible.reserve(messages.size());
  for (const auto &message : messages) {
    if (!message.exclude_from_context)
      visible.push_back(&message);
  }
  return visible;
}

[[nodiscard]] domain::ChatMessage SummaryMessage(
    const std::uint64_t id, std::string_view content) {
  domain::ChatMessage summary{};
  summary.id = id;
  // Legacy `finishContextCompaction` builds the summary with Role.USER
  // (ContextCompactionController.java:498-515).
  summary.role = domain::MessageRole::user;
  summary.content = std::string{content};
  // hidden=true, excludeFromContext=false: the summary replaces the compacted
  // history in the model context while staying out of the transcript.
  summary.hidden = true;
  summary.exclude_from_context = false;
  return summary;
}

} // namespace

bool HasCompactableContent(const domain::ChatMessage &message) noexcept {
  return !IsBlank(message.content) || !IsBlank(message.reasoning_content) ||
         HasToolActivity(message);
}

bool HasCompactableBaseMessages(
    const std::span<const domain::ChatMessage> messages) {
  for (const auto &message : messages) {
    if (message.exclude_from_context || IsCompactedArtifact(message) ||
        domain::IsCompactBlock(message)) {
      continue;
    }
    if (HasCompactableContent(message))
      return true;
  }
  return false;
}

std::vector<domain::ChatMessage>
PreservedTail(const std::vector<domain::ChatMessage> &messages,
              const std::optional<std::uint64_t> active_user_message_id) {
  const auto context = ContextMessages(messages);
  if (context.empty())
    return {};
  const auto *last = context.back();
  if (last->role == domain::MessageRole::user &&
      (!active_user_message_id.has_value() ||
       *active_user_message_id == last->id)) {
    return {*last};
  }
  if (last->role != domain::MessageRole::tool)
    return {};
  // Legacy lines 689-696: walk back to the assistant message that issued the
  // tool call and keep everything from there to the end.
  for (auto index = context.size(); index > 0; --index) {
    const auto *message = context[index - 1U];
    if (message->role == domain::MessageRole::assistant &&
        HasToolActivity(*message)) {
      std::vector<domain::ChatMessage> tail;
      tail.reserve(context.size() - (index - 1U));
      for (std::size_t offset = index - 1U; offset < context.size(); ++offset)
        tail.push_back(*context[offset]);
      return tail;
    }
  }
  return {};
}

std::vector<std::uint64_t>
MessageIdSet(const std::span<const domain::ChatMessage> messages) {
  std::vector<std::uint64_t> ids;
  ids.reserve(messages.size());
  for (const auto &message : messages)
    ids.push_back(message.id);
  return ids;
}

std::vector<std::uint64_t> RetainedUserMessageIds(
    const std::vector<domain::ChatMessage> &base_snapshot) {
  const auto retained = ContextCompactionService::SelectRecentUserMessages(
      base_snapshot, ContextCompactionService::COMPACT_USER_MESSAGE_MAX_TOKENS);
  return MessageIdSet(retained);
}

bool ShouldAutoCompactBeforeRequest(
    const std::optional<domain::ModelConfig> &model,
    const std::vector<domain::ChatMessage> &messages,
    const int observed_input_tokens,
    const std::span<const std::uint64_t> preserved_tail_ids,
    const bool include_reasoning) {
  if (!model.has_value())
    return false;
  if (!ContextCompactionService::ShouldCompact(*model, messages,
                                               include_reasoning,
                                               observed_input_tokens)) {
    return false;
  }
  for (const auto &message : messages) {
    if (ContainsId(preserved_tail_ids, message.id) ||
        message.exclude_from_context) {
      continue;
    }
    // A hidden summary produced by an earlier compaction is not compactable
    // content; compacting it again would summarize the summary.
    if (IsCompactedArtifact(message))
      continue;
    if (HasCompactableContent(message))
      return true;
  }
  return false;
}

bool ShouldAutoSoftCompactBeforeRequest(
    const std::optional<domain::ModelConfig> &model,
    const std::vector<domain::ChatMessage> &messages,
    const int observed_input_tokens, const bool soft_compaction_enabled,
    const std::span<const std::uint64_t> preserved_tail_ids,
    const bool include_reasoning) {
  if (!model.has_value())
    return false;
  if (!soft_compaction_enabled)
    return false;
  if (!ContextCompactionService::ShouldSoftCompact(*model, messages,
                                                   include_reasoning,
                                                   observed_input_tokens)) {
    return false;
  }
  // Legacy lines 154-163: the base is the conversation without the preserved
  // tail; `splitForSoftCompact` filters the compactable messages itself.
  std::vector<domain::ChatMessage> base;
  base.reserve(messages.size());
  for (const auto &message : messages) {
    if (ContainsId(preserved_tail_ids, message.id))
      continue;
    base.push_back(message);
  }
  const auto split = ContextCompactionService::SplitForSoftCompact(base);
  return HasCompactableBaseMessages(split.head);
}

bool ShouldAutoCompactMidLoop(
    const std::optional<domain::ModelConfig> &model,
    const std::vector<domain::ChatMessage> &messages,
    const int observed_input_tokens, const bool include_reasoning) {
  if (!model.has_value())
    return false;
  if (!ContextCompactionService::ShouldCompact(*model, messages,
                                               include_reasoning,
                                               observed_input_tokens)) {
    return false;
  }
  // Legacy line 244: the mid-loop trigger preserves nothing explicitly
  // (`getAutoCompactPreservedTail("")`).
  const auto preserved = PreservedTail(messages, std::nullopt);
  const auto preserved_ids = MessageIdSet(preserved);
  return ShouldAutoCompactBeforeRequest(
      model, messages, observed_input_tokens, preserved_ids,
      include_reasoning);
}

domain::ChatMessage
CompletedCompactProgress(const std::span<const domain::ChatMessage> messages,
                         const std::uint64_t progress_id) {
  const auto found = std::ranges::find(messages, progress_id,
                                       &domain::ChatMessage::id);
  const auto running =
      found != messages.end()
          ? *found
          : domain::CompactProgressMessage(progress_id,
                                           domain::compact_status_running);
  return domain::WithCompactStatus(running, domain::compact_status_done,
                                   false);
}

std::vector<domain::ChatMessage> MergeCompaction(
    const std::vector<domain::ChatMessage> &messages,
    const std::span<const std::uint64_t> base_snapshot_ids,
    const std::span<const std::uint64_t> preserved_ids,
    const std::span<const std::uint64_t> retained_ids,
    const CompactionWriteback &writeback) {
  std::vector<domain::ChatMessage> compacted;
  compacted.reserve(messages.size() + 2U);
  for (const auto &message : messages) {
    if (message.id == writeback.progress_id ||
        ContainsId(preserved_ids, message.id)) {
      continue;
    }
    if (ContainsId(retained_ids, message.id)) {
      compacted.push_back(message);
    } else if (ContainsId(base_snapshot_ids, message.id)) {
      auto excluded = message;
      excluded.exclude_from_context = true;
      compacted.push_back(std::move(excluded));
    } else {
      compacted.push_back(message);
    }
  }
  // The summary must enter the context (exclude_from_context=false), otherwise
  // the model sees the history as wiped. Legacy lines 495-516.
  compacted.push_back(SummaryMessage(writeback.summary_id,
                                     writeback.summary_content));
  for (const auto &message : messages) {
    if (ContainsId(preserved_ids, message.id))
      compacted.push_back(message);
  }
  compacted.push_back(CompletedCompactProgress(messages, writeback.progress_id));
  return compacted;
}

std::vector<domain::ChatMessage> MergeSoftCompaction(
    const std::vector<domain::ChatMessage> &messages,
    const std::span<const std::uint64_t> head_snapshot_ids,
    const std::span<const std::uint64_t> preserved_ids,
    const CompactionWriteback &writeback) {
  // Expected order: [head(excluded)] + [summary] + [tail verbatim] +
  // [preservedTail] + [progress done]. Legacy lines 572-615.
  std::vector<domain::ChatMessage> head;
  std::vector<domain::ChatMessage> tail;
  head.reserve(messages.size());
  tail.reserve(messages.size());
  for (const auto &message : messages) {
    if (message.id == writeback.progress_id ||
        ContainsId(preserved_ids, message.id)) {
      continue;
    }
    if (ContainsId(head_snapshot_ids, message.id)) {
      auto excluded = message;
      excluded.exclude_from_context = true;
      head.push_back(std::move(excluded));
    } else {
      tail.push_back(message);
    }
  }
  std::vector<domain::ChatMessage> compacted;
  compacted.reserve(head.size() + tail.size() + preserved_ids.size() + 2U);
  compacted.insert(compacted.end(), head.begin(), head.end());
  compacted.push_back(SummaryMessage(writeback.summary_id,
                                     writeback.summary_content));
  compacted.insert(compacted.end(), tail.begin(), tail.end());
  for (const auto &message : messages) {
    if (ContainsId(preserved_ids, message.id))
      compacted.push_back(message);
  }
  compacted.push_back(CompletedCompactProgress(messages, writeback.progress_id));
  return compacted;
}

bool ShouldResumeAfterCompaction(const CompactionResumeMode mode,
                                 const bool cancelled) noexcept {
  if (mode == CompactionResumeMode::none)
    return false;
  return !cancelled;
}

std::vector<domain::ChatMessage> MarkCompactionError(
    const std::vector<domain::ChatMessage> &messages,
    const std::uint64_t progress_id, const std::uint64_t notice_id,
    std::string message) {
  std::vector<domain::ChatMessage> updated;
  updated.reserve(messages.size() + 1U);
  for (const auto &entry : messages) {
    if (entry.id == progress_id) {
      updated.push_back(domain::WithCompactStatus(
          entry, domain::compact_status_error, false));
      continue;
    }
    updated.push_back(entry);
  }
  if (!message.empty()) {
    // Legacy lines 644-647: an assistant notice excluded from the context.
    domain::ChatMessage notice{};
    notice.id = notice_id;
    notice.role = domain::MessageRole::assistant;
    notice.content = std::move(message);
    notice.exclude_from_context = true;
    updated.push_back(std::move(notice));
  }
  return updated;
}

std::vector<domain::ChatMessage> MarkCompactionCancelled(
    const std::vector<domain::ChatMessage> &messages,
    const std::uint64_t progress_id) {
  return MarkCompactionError(messages, progress_id, 0U, std::string{});
}

std::string CompactFailureNoSummary() {
  return std::string{domain::compact_failure_no_summary};
}

std::string CompactFailureMessage(const std::string_view detail) {
  return domain::CompactFailureMessage(detail);
}

std::string CompactFailureOutOfMemory() {
  return std::string{domain::compact_failure_out_of_memory};
}

} // namespace linecode::application
