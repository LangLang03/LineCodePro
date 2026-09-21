#include "application/memory_conversation_snapshot.h"

#include <array>
#include <optional>
#include <ranges>
#include <string_view>
#include <utility>
#include <vector>

namespace linecode::application {
namespace {

struct IndexableRole final {
  domain::MessageRole role;
  std::string_view storage_name;
};

constexpr std::array kIndexableRoles{
    IndexableRole{domain::MessageRole::user, "user"},
    IndexableRole{domain::MessageRole::assistant, "assistant"},
};

std::optional<std::string_view>
MemoryRoleName(domain::MessageRole role) noexcept {
  const auto found =
      std::ranges::find(kIndexableRoles, role, &IndexableRole::role);
  if (found == kIndexableRoles.end())
    return std::nullopt;
  return found->storage_name;
}

} // namespace

domain::MemoryConversationTurn BuildMemoryConversationTurn(
    std::span<const ConversationSummary> conversations,
    std::span<const domain::ChatMessage> messages, std::string project_id,
    std::string conversation_id, std::int64_t timestamp_millis) {
  std::string title;
  const auto summary = std::ranges::find(conversations, conversation_id,
                                         &ConversationSummary::id);
  if (summary != conversations.end())
    title = summary->title;

  std::vector<domain::MemoryConversationMessage> indexed_messages;
  indexed_messages.reserve(messages.size());
  for (const auto &message : messages) {
    const auto role = MemoryRoleName(message.role);
    if (!role || message.content.empty())
      continue;
    indexed_messages.push_back(domain::MemoryConversationMessage{
        .id = std::to_string(message.id),
        .role = std::string{*role},
        .content = message.content,
        .timestamp = timestamp_millis,
    });
    if (title.empty() && message.role == domain::MessageRole::user)
      title = domain::PreviewMemoryText(message.content, 28);
  }
  return domain::MemoryConversationTurn{
      .project_id = std::move(project_id),
      .conversation_id = std::move(conversation_id),
      .title = std::move(title),
      .messages = std::move(indexed_messages),
      .updated_at = timestamp_millis,
  };
}

} // namespace linecode::application
