#pragma once

#include <cstdint>
#include <expected>
#include <string>

#include "application/ports/conversation_store.h"

namespace linecode::application {

enum class SendMessageError : std::uint8_t {
  empty,
};

class SendMessage final {
public:
  explicit SendMessage(ConversationStore& store) noexcept : store_(store) {}

  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError> Execute(std::string text);

private:
  ConversationStore& store_;
  std::uint64_t next_id_{1};
};

} // namespace linecode::application
