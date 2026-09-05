#include "infrastructure/in_memory_conversation_store.h"

#include <utility>

namespace linecode::infrastructure {

std::span<const domain::ChatMessage> InMemoryConversationStore::Messages() const noexcept {
  return messages_;
}

void InMemoryConversationStore::Append(domain::ChatMessage message) {
  messages_.push_back(std::move(message));
}

void InMemoryConversationStore::Clear() noexcept {
  messages_.clear();
}

} // namespace linecode::infrastructure
