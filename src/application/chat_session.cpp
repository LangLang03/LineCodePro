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

domain::ChatMessage ChatSession::AppendAssistant(std::string text) {
  domain::ChatMessage message{
      .id = store_->AllocateMessageId(),
      .role = domain::MessageRole::assistant,
      .content = std::move(text),
  };
  store_->Append(message);
  return message;
}

void ChatSession::Clear() { store_->Clear(); }

std::span<const ConversationSummary>
ChatSession::Conversations() const noexcept {
  return store_->Conversations();
}

std::string_view ChatSession::CurrentConversationId() const noexcept {
  return store_->CurrentConversationId();
}

void ChatSession::StartNewConversation() { store_->StartNewConversation(); }

void ChatSession::SelectConversation(std::string_view id) {
  if (!id.empty()) {
    store_->SelectConversation(id);
  }
}

void ChatSession::DeleteConversation(std::string_view id) {
  if (!id.empty()) {
    store_->DeleteConversation(id);
  }
}

ConversationStore &
ChatSession::RequireStore(const std::unique_ptr<ConversationStore> &store) {
  if (!store) {
    throw std::invalid_argument("ChatSession requires a conversation store");
  }
  return *store;
}

} // namespace linecode::application
