#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class ChatSession;
}

namespace linecode::presentation {

huxerui::View MainScreen(std::shared_ptr<application::ChatSession> initial_session);

} // namespace linecode::presentation
