#pragma once

#include "application/ports/local_model_engine.h"

namespace linecode::infrastructure {

// The engine a build without llama.cpp reports.
//
// It exists so that every caller can hold a `LocalModelEngine` without an
// `#ifdef` of its own, and so that a build which lost the backend still answers
// questions about itself honestly: `Available()` is false and generation fails
// with a reason a user can act on, rather than the application silently
// pretending a local model works.
class UnavailableLocalModelEngine final
    : public application::LocalModelEngine {
public:
  [[nodiscard]] bool Available() const noexcept override { return false; }

  [[nodiscard]] application::LocalModelRuntimeInfo RuntimeInfo() const override;

  [[nodiscard]] huxerui::Task<
      std::expected<application::LocalGenerationResult,
                    application::CompletionError>>
  Generate(application::LocalGenerationRequest request,
           std::function<void(std::string_view)> on_piece) override;
};

} // namespace linecode::infrastructure
