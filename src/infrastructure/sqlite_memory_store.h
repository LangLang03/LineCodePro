#pragma once

#include <memory>

#include <huxerui/file.h>

#include "application/ports/memory_store.h"

namespace linecode::infrastructure {

class SqliteMemoryStoreState;

class SqliteMemoryStore final : public application::MemoryStore {
public:
  explicit SqliteMemoryStore(huxerui::File database_file);

  [[nodiscard]] huxerui::Task<
      application::MemoryStoreResult<domain::MemoryOverview>>
  LoadOverview(std::string project_id) override;

  [[nodiscard]] huxerui::Task<
      application::MemoryStoreResult<domain::MemoryRecord>>
  SaveManual(domain::MemoryRecord memory) override;

  [[nodiscard]] huxerui::Task<application::MemoryStoreResult<void>>
  Delete(std::vector<std::string> ids) override;

  [[nodiscard]] huxerui::Task<
      application::MemoryStoreResult<application::MemoryRetrievalCorpus>>
  LoadRetrievalCorpus(std::string project_id,
                      std::string exclude_conversation_id) override;

  [[nodiscard]] huxerui::Task<application::MemoryStoreResult<
      std::vector<domain::MemoryRecord>>>
  LoadManualMemories(std::string project_id) override;

  [[nodiscard]] huxerui::Task<application::MemoryStoreResult<void>>
  MarkUsed(std::vector<std::string> ids) override;

  [[nodiscard]] huxerui::Task<
      application::MemoryStoreResult<domain::MemoryRecord>>
  SaveExtracted(domain::MemoryRecord memory) override;

  [[nodiscard]] huxerui::Task<application::MemoryStoreResult<void>>
  IndexConversationTurn(domain::MemoryConversationTurn turn) override;

private:
  std::shared_ptr<SqliteMemoryStoreState> state_;
};

} // namespace linecode::infrastructure
