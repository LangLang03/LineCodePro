#include "application/send_message.h"

#include <algorithm>
#include <cctype>
#include <ranges>
#include <string_view>
#include <utility>

namespace linecode::application {
namespace {

bool IsBlank(std::string_view text) {
  return std::ranges::all_of(text, [](unsigned char character) { return std::isspace(character) != 0; });
}

} // namespace

std::expected<domain::ChatMessage, SendMessageError> SendMessage::Execute(std::string text) {
  if (text.empty() || IsBlank(text)) {
    return std::unexpected(SendMessageError::empty);
  }

  domain::ChatMessage message{
      .id = next_id_++,
      .role = domain::MessageRole::user,
      .content = std::move(text),
  };
  store_.Append(message);
  return message;
}

} // namespace linecode::application
