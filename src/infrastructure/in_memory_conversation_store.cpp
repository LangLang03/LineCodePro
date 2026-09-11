#include "infrastructure/in_memory_conversation_store.h"

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
