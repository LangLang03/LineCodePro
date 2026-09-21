#pragma once

#include <expected>
#include <optional>
#include <string>
#include <vector>

#include "application/ports/attachment_policy.h"
#include "domain/chat_image.h"
#include "application/ports/conversation_store.h"

namespace linecode::application {

enum class SendMessageError : std::uint8_t {
  empty,
  generation_in_progress,
};

class SendMessage final {
public:
  explicit SendMessage(ConversationStore &store) noexcept;
  SendMessage(ConversationStore &store,
              const AttachmentPolicy &attachment_policy) noexcept;

  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Execute(std::string text);
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Execute(std::string text,
          std::vector<domain::InputAttachment> attachments);
  // Legacy `ChatInteractionController.sendMessageWithImage`: an attached image
  // with no text gets a placeholder so the turn is never empty (and the Codex
  // protocol still receives a non-empty prompt).
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Execute(std::string text,
          std::vector<domain::InputAttachment> attachments,
          std::optional<domain::ChatImage> image);

private:
  ConversationStore &store_;
  const AttachmentPolicy &attachment_policy_;
};

} // namespace linecode::application
