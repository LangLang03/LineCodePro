#pragma once

#include <functional>
#include <memory>

#include <huxerui/file.h>
#include <huxerui/task.h>

namespace linecode::application {
class ChatSession;
class ProjectWorkspaceStore;
} // namespace linecode::application

namespace linecode::infrastructure {
class SqliteConversationStore;
}

namespace linecode::app {

class ChatSessionBootstrap final {
public:
  ChatSessionBootstrap(huxerui::TaskScope tasks,
                       std::function<void()> on_changed);
  ~ChatSessionBootstrap();

  ChatSessionBootstrap(const ChatSessionBootstrap &) = delete;
  ChatSessionBootstrap &operator=(const ChatSessionBootstrap &) = delete;

  [[nodiscard]] const std::shared_ptr<application::ChatSession> &
  Session() const noexcept;
  [[nodiscard]] huxerui::Task<void>
  InitializeAsync(huxerui::File database_file);

private:
  std::shared_ptr<application::ChatSession> session_;
  infrastructure::SqliteConversationStore *store_{};
};

std::shared_ptr<application::ProjectWorkspaceStore>
CreateProjectWorkspaceStore(huxerui::File root);

} // namespace linecode::app
