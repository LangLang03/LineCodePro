#pragma once

#include <expected>
#include <string>
#include <vector>

#include "application/ports/attachment_policy.h"
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

private:
  ConversationStore &store_;
  const AttachmentPolicy &attachment_policy_;
};

} // namespace linecode::application
