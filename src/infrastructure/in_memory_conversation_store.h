#pragma once

#include <vector>

#include "application/ports/conversation_store.h"

namespace linecode::infrastructure {

class InMemoryConversationStore final : public application::ConversationStore {
public:
  [[nodiscard]] std::span<const domain::ChatMessage> Messages() const noexcept override;
  void Append(domain::ChatMessage message) override;
  void Clear() noexcept override;

private:
  std::vector<domain::ChatMessage> messages_;
};

} // namespace linecode::infrastructure
