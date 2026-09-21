#include "gtest_support.h"

#include <array>
#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "application/memory_conversation_snapshot.h"

namespace {

using namespace linecode;

domain::ChatMessage Message(std::uint64_t id, domain::MessageRole role,
                            std::string content) {
  domain::ChatMessage message;
  message.id = id;
  message.role = role;
  message.content = std::move(content);
  return message;
}

TEST(MemoryConversationSnapshotTest, UsesPersistedConversationTitle) {
  const std::array conversations{
      application::ConversationSummary{
          .id = "conversation-1",
          .title = "Persisted title",
          .updated_at_millis = 7,
      },
  };
  const std::array messages{
      Message(10, domain::MessageRole::user, "A user message"),
      Message(11, domain::MessageRole::assistant, "An assistant message"),
  };

  const auto turn = application::BuildMemoryConversationTurn(
      conversations, messages, "project-1", "conversation-1", 1234);

  EXPECT_EQ(turn.project_id, "project-1");
  EXPECT_EQ(turn.conversation_id, "conversation-1");
  EXPECT_EQ(turn.title, "Persisted title");
  ASSERT_EQ(turn.messages.size(), 2U);
  EXPECT_EQ(turn.messages[0].id, "10");
  EXPECT_EQ(turn.messages[0].role, "user");
  EXPECT_EQ(turn.messages[0].timestamp, 1234);
  EXPECT_EQ(turn.messages[1].role, "assistant");
  EXPECT_EQ(turn.updated_at, 1234);
}

TEST(MemoryConversationSnapshotTest, DerivesTitleAndSkipsUnindexableMessages) {
  const std::vector<application::ConversationSummary> conversations;
  const std::array messages{
      Message(20, domain::MessageRole::tool, "Tool output"),
      Message(21, domain::MessageRole::assistant, ""),
      Message(22, domain::MessageRole::user,
              "First meaningful user message"),
  };

  const auto turn = application::BuildMemoryConversationTurn(
      conversations, messages, "project-2", "conversation-2", 5678);

  EXPECT_EQ(turn.title, "First meaningful user mes...");
  ASSERT_EQ(turn.messages.size(), 1U);
  EXPECT_EQ(turn.messages.front().id, "22");
  EXPECT_EQ(turn.messages.front().content, "First meaningful user message");
}

} // namespace
