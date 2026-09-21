#include "app/bootstrap.h"

#include <memory>
#include <utility>

#include "application/chat_session.h"
#include "infrastructure/hux_project_workspace_store.h"
#include "infrastructure/sqlite_conversation_store.h"

namespace linecode::app {
ChatSessionBootstrap::ChatSessionBootstrap(huxerui::TaskScope tasks,
                                           std::function<void()> on_changed) {
  auto store = std::make_unique<infrastructure::SqliteConversationStore>(
      std::move(tasks), std::move(on_changed));
  store_ = store.get();
  session_ = std::make_shared<application::ChatSession>(std::move(store));
}

ChatSessionBootstrap::~ChatSessionBootstrap() = default;

const std::shared_ptr<application::ChatSession> &
ChatSessionBootstrap::Session() const noexcept {
  return session_;
}

huxerui::Task<std::expected<void, std::string>>
ChatSessionBootstrap::InitializeAsync(huxerui::File database_file) {
  auto initialized = co_await store_->InitializeAsync(std::move(database_file));
  if (!initialized)
    co_return std::unexpected(initialized.Error().Message());
  co_return std::expected<void, std::string>{};
}

huxerui::Task<std::expected<void, std::string>>
ChatSessionBootstrap::PersistAsync() {
  auto persisted = co_await store_->FlushPendingAsync();
  if (!persisted) {
    co_return std::unexpected(persisted.Error().Message());
  }
  co_return std::expected<void, std::string>{};
}

huxerui::Task<std::expected<void, std::string>>
ChatSessionBootstrap::ReloadAsync() {
  auto reloaded = co_await store_->ReloadAsync();
  if (!reloaded) {
    co_return std::unexpected(reloaded.Error().Message());
  }
  co_return std::expected<void, std::string>{};
}

std::shared_ptr<application::ProjectWorkspaceStore>
CreateProjectWorkspaceStore(huxerui::File root) {
  return std::make_shared<infrastructure::HuxProjectWorkspaceStore>(
      std::move(root));
}

} // namespace linecode::app
