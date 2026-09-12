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

std::expected<domain::ChatMessage, SendMessageError>
ChatSession::Send(std::string text,
                  std::vector<domain::InputAttachment> attachments) {
  return send_message_.Execute(std::move(text), std::move(attachments));
}

std::expected<domain::ChatMessage, SendMessageError>
ChatSession::Send(std::string text,
                  std::vector<domain::InputAttachment> attachments,
                  std::optional<domain::ChatImage> image) {
  return send_message_.Execute(std::move(text), std::move(attachments),
                               std::move(image));
}

domain::ChatMessage ChatSession::AppendAssistant(std::string text) {
  domain::ChatMessage message{};
  message.content = std::move(text);
  return AppendAssistant(std::move(message));
}

domain::ChatMessage
ChatSession::AppendAssistant(domain::ChatMessage message) {
  message.id = store_->AllocateMessageId();
  message.role = domain::MessageRole::assistant;
  store_->Append(message);
  return message;
}

std::optional<domain::ChatMessage>
ChatSession::RecallUserMessage(std::uint64_t message_id) {
  return store_->RecallUserMessage(message_id);
}

void ChatSession::Clear() { store_->Clear(); }

void ChatSession::ApplyCompaction(std::vector<std::uint64_t> excluded_ids,
                                  std::string summary_content) {
  ApplyCompaction(std::move(excluded_ids), std::move(summary_content), {});
}

void ChatSession::ApplyCompaction(std::vector<std::uint64_t> excluded_ids,
                                  std::string summary_content,
                                  std::vector<domain::ChatMessage> trailing) {
  ApplyCompaction(std::move(excluded_ids), std::move(summary_content),
                  std::move(trailing), 0);
}

void ChatSession::ApplyCompaction(std::vector<std::uint64_t> excluded_ids,
                                  std::string summary_content,
                                  std::vector<domain::ChatMessage> trailing,
                                  const std::uint64_t insert_after_id) {
  if (summary_content.empty())
    return;
  auto &store = RequireStore(store_);
  domain::ChatMessage summary;
  summary.id = store.AllocateMessageId();
  // Legacy `ContextCompactionController.java:500` builds the summary as a
  // `Role.USER` message, not an assistant one; the model must read it as
  // handed-over context rather than its own prior turn.
  summary.role = domain::MessageRole::user;
  summary.content = std::move(summary_content);
  // Legacy compact blocks are hidden from the transcript but stay in context,
  // which is exactly what makes them replace the summarized history.
  summary.hidden = true;
  store.ApplyCompaction(excluded_ids, std::move(summary), insert_after_id);
  for (auto &message : trailing) {
    // The originals were handed to `ApplyCompaction` (and left the context), so
    // the tail travels as hidden copies that still render nowhere but take part
    // in the model request, right after the summary.
    message.id = store.AllocateMessageId();
    message.hidden = true;
    message.exclude_from_context = false;
    message.streaming = false;
    message.compact_status.clear();
    store.Append(std::move(message));
  }
}

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
