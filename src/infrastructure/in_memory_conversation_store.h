#pragma once

#include <vector>

#include "application/ports/conversation_store.h"

namespace linecode::infrastructure {

class InMemoryConversationStore final : public application::ConversationStore {
public:
  [[nodiscard]] std::span<const domain::ChatMessage>
  Messages() const noexcept override;
  [[nodiscard]] std::uint64_t AllocateMessageId() noexcept override;
  void Append(domain::ChatMessage message) override;
  void Clear() override;

private:
  std::vector<domain::ChatMessage> messages_;
  std::uint64_t next_message_id_{1};
};

} // namespace linecode::infrastructure
