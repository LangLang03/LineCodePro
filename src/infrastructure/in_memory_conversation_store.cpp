#include "infrastructure/in_memory_conversation_store.h"

#include <limits>
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

} // namespace linecode::infrastructure
