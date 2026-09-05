#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class ChatSession;
class ProjectWorkspaceStore;
} // namespace linecode::application

namespace linecode::presentation {

huxerui::View
MainScreen(std::shared_ptr<application::ChatSession> initial_session,
           std::shared_ptr<application::ProjectWorkspaceStore> project_store);

} // namespace linecode::presentation
