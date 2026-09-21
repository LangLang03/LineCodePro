#pragma once

#include <expected>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "domain/memory.h"

namespace linecode::application {

struct MemoryStoreError final {
  std::string message;

  bool operator==(const MemoryStoreError &) const = default;
};

template <typename T>
using MemoryStoreResult = std::expected<T, MemoryStoreError>;

struct MemoryRetrievalCorpus final {
  std::vector<domain::WorkingMemoryRecord> working;
  std::vector<domain::MemoryRecord> memories;
  std::vector<domain::ConversationIndexRecord> history;
  std::vector<domain::MemorySkillRecord> skills;

  bool operator==(const MemoryRetrievalCorpus &) const = default;
};

class MemoryStore {
public:
  virtual ~MemoryStore() = default;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<domain::MemoryOverview>>
  LoadOverview(std::string project_id) = 0;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<domain::MemoryRecord>>
  SaveManual(domain::MemoryRecord memory) = 0;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<void>>
  Delete(std::vector<std::string> ids) = 0;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<MemoryRetrievalCorpus>>
  LoadRetrievalCorpus(std::string project_id,
                      std::string exclude_conversation_id) = 0;

  [[nodiscard]] virtual huxerui::Task<
      MemoryStoreResult<std::vector<domain::MemoryRecord>>>
  LoadManualMemories(std::string project_id) = 0;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<void>>
  MarkUsed(std::vector<std::string> ids) = 0;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<domain::MemoryRecord>>
  SaveExtracted(domain::MemoryRecord memory) = 0;

  [[nodiscard]] virtual huxerui::Task<MemoryStoreResult<void>>
  IndexConversationTurn(domain::MemoryConversationTurn turn) = 0;
};

} // namespace linecode::application
