#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <span>
#include <string>

#include <huxerui/file.h>
#include <huxerui/sqlite.h>
#include <huxerui/task.h>

#include "application/ports/conversation_store.h"

namespace linecode::infrastructure {

// UI-facing cache plus an asynchronous, serialized SQLite outbox. The cache
// preserves the existing synchronous application port while all storage work
// remains off the UI thread in HuxerUI SQLite tasks.
class SqliteConversationStore final : public application::ConversationStore {
public:
  explicit SqliteConversationStore(huxerui::TaskScope tasks,
                                   std::function<void()> on_changed = {});
  ~SqliteConversationStore() override;

  SqliteConversationStore(const SqliteConversationStore &) = delete;
  SqliteConversationStore &operator=(const SqliteConversationStore &) = delete;

  [[nodiscard]] huxerui::Task<huxerui::sqlite::Result<void>>
  InitializeAsync(huxerui::File database_file);

  [[nodiscard]] std::span<const domain::ChatMessage>
  Messages() const noexcept override;
  [[nodiscard]] std::uint64_t AllocateMessageId() noexcept override;
  void Append(domain::ChatMessage message) override;
  void Clear() override;
  [[nodiscard]] std::span<const application::ConversationSummary>
  Conversations() const noexcept override;
  [[nodiscard]] std::string_view
  CurrentConversationId() const noexcept override;
  void StartNewConversation() override;
  void SelectConversation(std::string_view id) override;
  void DeleteConversation(std::string_view id) override;

  [[nodiscard]] std::string LastPersistenceError() const;

private:
  struct State;
  static void ScheduleFlush(const std::shared_ptr<State> &state);
  [[nodiscard]] static huxerui::Task<void>
  FlushAsync(std::shared_ptr<State> state);
  [[nodiscard]] static huxerui::Task<huxerui::sqlite::Result<void>>
  ProcessNextAsync(std::shared_ptr<State> state);

  std::shared_ptr<State> state_;
};

} // namespace linecode::infrastructure
