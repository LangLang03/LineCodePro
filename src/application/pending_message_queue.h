#pragma once

#include <expected>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "application/send_message.h"
#include "domain/input_attachment.h"

namespace linecode::application {

struct PendingMessage final {
  std::string text;
  std::vector<domain::InputAttachment> attachments;
  std::optional<domain::ChatImage> image;

  bool operator==(const PendingMessage &) const = default;
};

// Owns only the legacy composer queue policy. Persistence and generation
// remain the responsibility of ChatSession and GenerationController.
class PendingMessageQueue final {
public:
  [[nodiscard]] std::expected<void, SendMessageError>
  Enqueue(std::string text,
          std::vector<domain::InputAttachment> attachments = {},
          std::optional<domain::ChatImage> image = std::nullopt);
  [[nodiscard]] std::optional<PendingMessage> TakeNext();
  [[nodiscard]] bool Remove(std::size_t index);
  void Clear() noexcept;

  [[nodiscard]] std::span<const PendingMessage> Items() const noexcept;
  [[nodiscard]] bool Empty() const noexcept;

private:
  std::vector<PendingMessage> items_;
};

} // namespace linecode::application
