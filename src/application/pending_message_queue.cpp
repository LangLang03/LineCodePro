#include "application/pending_message_queue.h"

#include <cctype>
#include <cstddef>
#include <ranges>
#include <utility>

#include "application/legacy_attachment_policy.h"

namespace linecode::application {
namespace {

std::string Trim(std::string text) {
  const auto visible = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(text, visible);
  if (begin == text.end())
    return {};
  const auto end = std::ranges::find_if(text | std::views::reverse, visible);
  return std::string{begin, end.base()};
}

} // namespace

std::expected<void, SendMessageError> PendingMessageQueue::Enqueue(
    std::string text, std::vector<domain::InputAttachment> attachments,
    std::optional<domain::ChatImage> image) {
  text = Trim(std::move(text));
  attachments = DefaultAttachmentPolicy().Sanitize(attachments);
  if (image.has_value() && image->Empty())
    image.reset();
  if (text.empty() && attachments.empty() && !image.has_value())
    return std::unexpected(SendMessageError::empty);
  items_.push_back(
      PendingMessage{.text = std::move(text),
                     .attachments = std::move(attachments),
                     .image = std::move(image)});
  return {};
}

std::optional<PendingMessage> PendingMessageQueue::TakeNext() {
  if (items_.empty())
    return std::nullopt;
  PendingMessage next = std::move(items_.front());
  items_.erase(items_.begin());
  return next;
}

bool PendingMessageQueue::Remove(const std::size_t index) {
  if (index >= items_.size())
    return false;
  items_.erase(items_.begin() + static_cast<std::ptrdiff_t>(index));
  return true;
}

void PendingMessageQueue::Clear() noexcept { items_.clear(); }

std::span<const PendingMessage> PendingMessageQueue::Items() const noexcept {
  return items_;
}

bool PendingMessageQueue::Empty() const noexcept { return items_.empty(); }

} // namespace linecode::application
