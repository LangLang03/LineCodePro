#pragma once

#include <span>
#include <string>

#include "domain/memory_rag.h"

namespace linecode::application {

class MemoryPromptRenderer {
public:
  virtual ~MemoryPromptRenderer() = default;

  [[nodiscard]] virtual std::string
  RenderLearning(std::span<const domain::MemoryCandidate> working,
                 std::span<const domain::MemoryCandidate> memories,
                 std::span<const domain::MemoryCandidate> history,
                 std::span<const domain::MemoryCandidate> skills) const = 0;
  [[nodiscard]] virtual std::string
  RenderManual(std::span<const domain::MemoryCandidate> memories) const = 0;
};

// Preserves the legacy LearningContext prompt contract while leaving the
// orchestration service open to a template-backed renderer later.
class LegacyMemoryPromptRenderer final : public MemoryPromptRenderer {
public:
  [[nodiscard]] std::string RenderLearning(
      std::span<const domain::MemoryCandidate> working,
      std::span<const domain::MemoryCandidate> memories,
      std::span<const domain::MemoryCandidate> history,
      std::span<const domain::MemoryCandidate> skills) const override;
  [[nodiscard]] std::string RenderManual(
      std::span<const domain::MemoryCandidate> memories) const override;
};

} // namespace linecode::application
