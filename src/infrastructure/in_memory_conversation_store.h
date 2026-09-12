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
  // Port contract: the summarized messages leave the context and the summary
  // joins the conversation hidden from the transcript but present in it.
  void ApplyCompaction(std::span<const std::uint64_t> excluded_ids,
                       domain::ChatMessage summary,
                       std::uint64_t insert_after_id = 0) override;
  [[nodiscard]] std::optional<domain::ChatMessage>
  RecallUserMessage(std::uint64_t message_id) override;

private:
  std::vector<domain::ChatMessage> messages_;
  std::uint64_t next_message_id_{1};
};

} // namespace linecode::infrastructure
