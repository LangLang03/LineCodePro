#pragma once

#include <memory>

#include "application/ports/completion_gateway.h"
#include "application/ports/local_model_engine.h"

namespace linecode::infrastructure {

// Routes a completion to the in-process runtime or to the HTTP gateway.
//
// The whole application already talks to a single `CompletionGateway`: the chat
// loop, auto-compaction, sub-agents and the extension draft all share it. Making
// the local backend a decorator at that one seam means a local model reuses
// every one of those paths unchanged, instead of each caller growing its own
// branch for `protocol == local_gguf`.
class LocalModelCompletionGateway final
    : public application::CompletionGateway {
public:
  LocalModelCompletionGateway(
      std::shared_ptr<application::CompletionGateway> remote,
      std::shared_ptr<application::LocalModelEngine> engine);

  [[nodiscard]] huxerui::Task<std::expected<application::CompletionResponse,
                                            application::CompletionError>>
  Complete(application::CompletionRequest request,
           application::CompletionObserver observer) override;

private:
  [[nodiscard]] huxerui::Task<std::expected<application::CompletionResponse,
                                            application::CompletionError>>
  CompleteLocal(application::CompletionRequest request,
                application::CompletionObserver observer);

  std::shared_ptr<application::CompletionGateway> remote_;
  std::shared_ptr<application::LocalModelEngine> engine_;
};

} // namespace linecode::infrastructure
