#include "infrastructure/in_memory_conversation_store.h"

#include <algorithm>
#include <limits>
#include <ranges>
#include <utility>

namespace linecode::infrastructure {

std::span<const domain::ChatMessage>
InMemoryConversationStore::Messages() const noexcept {
  return messages_;
}

std::uint64_t InMemoryConversationStore::AllocateMessageId() noexcept {
  return next_message_id_++;
}

void InMemoryConversationStore::Append(domain::ChatMessage message) {
  if (message.id >= next_message_id_ &&
      message.id != std::numeric_limits<std::uint64_t>::max()) {
    next_message_id_ = message.id + 1;
  }
  messages_.push_back(std::move(message));
}

void InMemoryConversationStore::Clear() { messages_.clear(); }

void InMemoryConversationStore::ApplyCompaction(
    const std::span<const std::uint64_t> excluded_ids,
    domain::ChatMessage summary, const std::uint64_t insert_after_id) {
  for (auto &message : messages_) {
    if (std::ranges::contains(excluded_ids, message.id))
      message.exclude_from_context = true;
  }
  summary.hidden = true;
  if (insert_after_id != 0) {
    const auto anchor =
        std::ranges::find(messages_, insert_after_id, &domain::ChatMessage::id);
    if (anchor != messages_.end()) {
      messages_.insert(std::next(anchor), std::move(summary));
      return;
    }
  }
  Append(std::move(summary));
}

std::optional<domain::ChatMessage>
InMemoryConversationStore::RecallUserMessage(std::uint64_t message_id) {
  const auto found = std::ranges::find(messages_, message_id,
                                       &domain::ChatMessage::id);
  if (found == messages_.end() ||
      found->role != domain::MessageRole::user) {
    return std::nullopt;
  }
  auto recalled = *found;
  messages_.erase(found, messages_.end());
  return recalled;
}

} // namespace linecode::infrastructure
