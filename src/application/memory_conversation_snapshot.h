#pragma once

#include <cstdint>
#include <span>
#include <string>

#include "application/ports/conversation_store.h"
#include "domain/memory.h"

namespace linecode::application {

[[nodiscard]] domain::MemoryConversationTurn BuildMemoryConversationTurn(
    std::span<const ConversationSummary> conversations,
    std::span<const domain::ChatMessage> messages, std::string project_id,
    std::string conversation_id, std::int64_t timestamp_millis);

} // namespace linecode::application
