#include "domain/compaction_progress.h"

#include <utility>

namespace linecode::domain {

std::string NormalizeCompactStatus(const std::string_view value) {
  if (value == compact_status_running || value == compact_status_done ||
      value == compact_status_error) {
    return std::string{value};
  }
  return {};
}

bool IsCompactBlock(const ChatMessage &message) noexcept {
  return !message.compact_status.empty();
}

ChatMessage CompactProgressMessage(const std::uint64_t id,
                                   const std::string_view status) {
  ChatMessage message{};
  message.id = id;
  message.role = MessageRole::assistant;
  message.streaming = status == compact_status_running;
  // `compactProgress` passes hidden=false / excludeFromContext=true
  // (`ChatMessage.java:398-402`): the block is visible in the transcript but
  // never reaches the model.
  message.hidden = false;
  message.exclude_from_context = true;
  message.compact_status = NormalizeCompactStatus(status);
  return message;
}

ChatMessage WithCompactStatus(const ChatMessage &message,
                              const std::string_view status,
                              const bool streaming) {
  auto next = message;
  next.compact_status = NormalizeCompactStatus(status);
  next.streaming = streaming;
  return next;
}

std::string CompactFailureMessage(const std::string_view detail) {
  std::string message{compact_failure_prefix};
  message += detail;
  return message;
}

} // namespace linecode::domain
