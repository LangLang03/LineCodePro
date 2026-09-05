#pragma once

#include <memory>

namespace linecode::application {
class ChatSession;
}

namespace linecode::app {

std::shared_ptr<application::ChatSession> CreateChatSession();

} // namespace linecode::app
