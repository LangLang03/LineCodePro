#include "app/bootstrap.h"

#include <memory>

#include "application/chat_session.h"
#include "infrastructure/in_memory_conversation_store.h"

namespace linecode::app {

std::shared_ptr<application::ChatSession> CreateChatSession() {
  return std::make_shared<application::ChatSession>(
      std::make_unique<infrastructure::InMemoryConversationStore>()
  );
}

} // namespace linecode::app
