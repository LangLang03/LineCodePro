#include "application/chat_session.h"

#include <stdexcept>
#include <utility>

namespace linecode::application {

ChatSession::ChatSession(std::unique_ptr<ConversationStore> store)
    : store_(std::move(store)), send_message_(RequireStore(store_)) {}

std::span<const domain::ChatMessage> ChatSession::Messages() const noexcept {
  return store_->Messages();
}

std::expected<domain::ChatMessage, SendMessageError>
ChatSession::Send(std::string text) {
  return send_message_.Execute(std::move(text));
}

void ChatSession::Clear() { store_->Clear(); }

ConversationStore &
ChatSession::RequireStore(const std::unique_ptr<ConversationStore> &store) {
  if (!store) {
    throw std::invalid_argument("ChatSession requires a conversation store");
  }
  return *store;
}

} // namespace linecode::application
