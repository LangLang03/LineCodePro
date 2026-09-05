#pragma once

#include <expected>
#include <string>

#include "application/ports/conversation_store.h"

namespace linecode::application {

enum class SendMessageError : std::uint8_t {
  empty,
};

class SendMessage final {
public:
  explicit SendMessage(ConversationStore &store) noexcept : store_(store) {}

  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Execute(std::string text);

private:
  ConversationStore &store_;
};

} // namespace linecode::application
