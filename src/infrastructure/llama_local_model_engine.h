#pragma once

#include "application/local_model_support.h"

#if LINCODE_LOCAL_MODEL_ENABLED

#include <memory>

#include "application/ports/local_model_engine.h"

namespace linecode::infrastructure {

// Runs GGUF inference through llama.cpp on the device the model asks for.
//
// The adapter owns a process-wide model cache, because a chat turn issues many
// completion requests and reloading several gigabytes of weights for each one
// would dominate the response time. Contexts and samplers stay per-request:
// they carry the conversation state, which must not leak between turns.
class LlamaLocalModelEngine final : public application::LocalModelEngine {
public:
  LlamaLocalModelEngine();
  ~LlamaLocalModelEngine() override;

  LlamaLocalModelEngine(const LlamaLocalModelEngine &) = delete;
  LlamaLocalModelEngine &operator=(const LlamaLocalModelEngine &) = delete;

  [[nodiscard]] bool Available() const noexcept override;

  [[nodiscard]] application::LocalModelRuntimeInfo RuntimeInfo() const override;

  // Decodes in bounded worker slices and invokes `on_piece` on the task's
  // runtime thread between slices, so the decode loop never blocks the UI
  // thread and the callback never runs on a worker thread.
  [[nodiscard]] huxerui::Task<
      std::expected<application::LocalGenerationResult,
                    application::CompletionError>>
  Generate(application::LocalGenerationRequest request,
           std::function<void(std::string_view)> on_piece) override;

private:
  struct Impl;
  std::shared_ptr<Impl> impl_;
};

} // namespace linecode::infrastructure

#endif // LINCODE_LOCAL_MODEL_ENABLED
