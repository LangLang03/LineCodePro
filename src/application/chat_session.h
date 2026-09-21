#pragma once

#include <expected>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "application/send_message.h"

namespace linecode::application {

class ChatSession final {
public:
  explicit ChatSession(std::unique_ptr<ConversationStore> store);

  [[nodiscard]] std::span<const domain::ChatMessage> Messages() const noexcept;
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Send(std::string text);
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Send(std::string text, std::vector<domain::InputAttachment> attachments);
  [[nodiscard]] std::expected<domain::ChatMessage, SendMessageError>
  Send(std::string text, std::vector<domain::InputAttachment> attachments,
       std::optional<domain::ChatImage> image);
  [[nodiscard]] domain::ChatMessage AppendAssistant(std::string text);
  [[nodiscard]] domain::ChatMessage
  AppendAssistant(domain::ChatMessage message);
  [[nodiscard]] std::optional<domain::ChatMessage>
  RecallUserMessage(std::uint64_t message_id);
  // Applies a compaction result: the summarized messages leave the context and
  // the summary joins the conversation as a hidden message.
  void ApplyCompaction(std::vector<std::uint64_t> excluded_ids,
                       std::string summary_content);
  // Same write-back, plus messages that must stay in the context *after* the
  // summary. The append-only conversation port cannot move the originals, so
  // the legacy `finishContextCompaction` order
  // (`ContextCompactionController.java:517-521`: summary, then the preserved
  // tail, then the completed progress block) is reproduced by excluding the
  // originals and re-appending hidden, in-context copies. The transcript is
  // unaffected: the originals keep rendering, the copies stay hidden.
  void ApplyCompaction(std::vector<std::uint64_t> excluded_ids,
                       std::string summary_content,
                       std::vector<domain::ChatMessage> trailing);
  // Same, but the summary is placed immediately after `insert_after_id`
  // instead of at the end. Soft compaction summarizes only the oldest slice
  // and legacy ordered the result "summary -> recent context -> current
  // question" (`ContextCompactionController.java:606-615`).
  void ApplyCompaction(std::vector<std::uint64_t> excluded_ids,
                       std::string summary_content,
                       std::vector<domain::ChatMessage> trailing,
                       std::uint64_t insert_after_id);
  void Clear();
  [[nodiscard]] std::span<const ConversationSummary>
  Conversations() const noexcept;
  [[nodiscard]] std::string_view CurrentConversationId() const noexcept;
  void StartNewConversation();
  void DeleteCurrentConversation();
  void SelectConversation(std::string_view id);
  void DeleteConversation(std::string_view id);

private:
  static ConversationStore &
  RequireStore(const std::unique_ptr<ConversationStore> &store);

  std::unique_ptr<ConversationStore> store_;
  SendMessage send_message_;
};

} // namespace linecode::application
