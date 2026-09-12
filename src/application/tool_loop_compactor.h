#pragma once

#include <memory>
#include <vector>

#include "application/auto_compaction_service.h"
#include "application/context_compaction.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/mid_loop_compactor.h"
#include "application/ports/model_store.h"

namespace linecode::application {

// Mid-loop compactor for the tool loop.
//
// Port of `GenerationFlowController.continueModelAfterTools()`: after every
// tool batch, and before the next model turn, a context that has reached the
// hard 80% trigger is summarized. The in-flight assistant + tool group stays
// verbatim so the model still sees the result it just produced.
//
// The loop owns `CompletionMessage`s while the compaction service works on
// `domain::ChatMessage`, so this adapter converts for the estimate and then
// rebuilds the request from the original tail plus one summary message.
class ToolLoopCompactor final : public MidLoopCompactor {
public:
  ToolLoopCompactor(std::shared_ptr<ContextCompactionService> compaction,
                    std::shared_ptr<ModelStore> models, bool include_reasoning);

  [[nodiscard]] huxerui::Task<CompletionRequest>
  CompactIfNeeded(CompletionRequest request) override;

private:
  std::shared_ptr<ContextCompactionService> compaction_;
  std::shared_ptr<ModelStore> models_;
  bool include_reasoning_{true};
};

} // namespace linecode::application
