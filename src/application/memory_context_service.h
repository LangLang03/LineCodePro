#pragma once

#include <expected>
#include <memory>
#include <string>

#include <huxerui/task.h>

#include "application/ports/memory_store.h"
#include "application/memory_prompt_renderer.h"
#include "domain/memory_rag.h"

namespace linecode::application {

struct PreparedMemoryContext final {
  std::string prompt;
  bool learning_enabled{};

  bool operator==(const PreparedMemoryContext &) const = default;
};

class MemoryContextService final {
public:
  MemoryContextService(
      std::shared_ptr<MemoryStore> store,
      std::shared_ptr<const domain::MemoryExtractionPolicy> extraction_policy,
      std::shared_ptr<const MemoryPromptRenderer> prompt_renderer);

  [[nodiscard]] huxerui::Task<MemoryStoreResult<PreparedMemoryContext>>
  Prepare(std::string project_id, std::string query,
          std::string exclude_conversation_id, bool learning_enabled);

  [[nodiscard]] huxerui::Task<MemoryStoreResult<void>>
  CommitTurn(bool learning_enabled, domain::MemoryConversationTurn turn,
             std::string user_text);

private:
  std::shared_ptr<MemoryStore> store_;
  std::shared_ptr<const domain::MemoryExtractionPolicy> extraction_policy_;
  std::shared_ptr<const MemoryPromptRenderer> prompt_renderer_;
};

} // namespace linecode::application
