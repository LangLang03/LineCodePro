#pragma once

#include <expected>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "application/send_message.h"

namespace linecode::application {

class ChatSession final {
public:
  explicit ChatSession(std::unique_ptr<ConversationStore> store);

  [[nodiscard]] std::span<const domain::ChatMessage> Messages() const noexcept;
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Send(std::string text);
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Send(std::string text, std::vector<domain::InputAttachment> attachments);
  [[nodiscard]] domain::ChatMessage AppendAssistant(std::string text);
  [[nodiscard]] std::optional<domain::ChatMessage>
  RecallUserMessage(std::uint64_t message_id);
  void Clear();
  [[nodiscard]] std::span<const ConversationSummary>
  Conversations() const noexcept;
  [[nodiscard]] std::string_view CurrentConversationId() const noexcept;
  void StartNewConversation();
  void SelectConversation(std::string_view id);
  void DeleteConversation(std::string_view id);

private:
  static ConversationStore &
  RequireStore(const std::unique_ptr<ConversationStore> &store);

  std::unique_ptr<ConversationStore> store_;
  SendMessage send_message_;
};

} // namespace linecode::application
