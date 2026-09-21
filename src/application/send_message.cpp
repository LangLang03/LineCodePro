#include "application/send_message.h"

#include <optional>
#include <string_view>
#include <utility>

#include "application/legacy_attachment_policy.h"

namespace linecode::application {
namespace {

std::string Trim(std::string text) {
  std::string_view view{text};
  while (!view.empty() &&
         static_cast<unsigned char>(view.front()) <=
             static_cast<unsigned char>(' ')) {
    view.remove_prefix(1);
  }
  while (!view.empty() &&
         static_cast<unsigned char>(view.back()) <=
             static_cast<unsigned char>(' ')) {
    view.remove_suffix(1);
  }
  if (view.data() == text.data() && view.size() == text.size()) {
    return text;
  }
  return std::string{view};
}

} // namespace

SendMessage::SendMessage(ConversationStore &store) noexcept
    : SendMessage(store, DefaultAttachmentPolicy()) {}

SendMessage::SendMessage(ConversationStore &store,
                         const AttachmentPolicy &attachment_policy) noexcept
    : store_(store), attachment_policy_(attachment_policy) {}

std::expected<domain::ChatMessage, SendMessageError>
SendMessage::Execute(std::string text) {
  return Execute(std::move(text), {});
}

std::expected<domain::ChatMessage, SendMessageError>
SendMessage::Execute(std::string text,
                     std::vector<domain::InputAttachment> attachments) {
  return Execute(std::move(text), std::move(attachments), std::nullopt);
}

std::expected<domain::ChatMessage, SendMessageError>
SendMessage::Execute(std::string text,
                     std::vector<domain::InputAttachment> attachments,
                     std::optional<domain::ChatImage> image) {
  text = Trim(std::move(text));
  attachments = attachment_policy_.Sanitize(attachments);
  if (image.has_value() && image->Empty())
    image.reset();
  if (text.empty() && image.has_value()) {
    // `ChatInteractionController.java:160-162`.
    const auto name = Trim(image->name);
    text = name.empty() ? std::string{"已附加图片"}
                        : "已附加图片：" + name;
  }
  if (text.empty() && attachments.empty() && !image.has_value()) {
    return std::unexpected(SendMessageError::empty);
  }

  domain::ChatMessage message{
      .id = store_.AllocateMessageId(),
      .role = domain::MessageRole::user,
      .content = std::move(text),
      .attachments = std::move(attachments),
  };
  message.image = std::move(image);
  store_.Append(message);
  return message;
}

} // namespace linecode::application
