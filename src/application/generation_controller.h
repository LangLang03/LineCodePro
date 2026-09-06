#pragma once

#include <cstdint>
#include <expected>
#include <span>
#include <string>
#include <vector>

#include "application/chat_session.h"
#include "application/ports/completion_gateway.h"

namespace linecode::application {

enum class GenerationPhase : std::uint8_t {
  idle,
  running,
  completed,
  cancelled,
  failed,
};

struct GenerationState final {
  std::uint64_t generation_id{};
  GenerationPhase phase{GenerationPhase::idle};
  std::string error;

  bool operator==(const GenerationState &) const = default;
};

struct GenerationWork final {
  std::uint64_t generation_id{};
  std::vector<CompletionMessage> messages;
};

class GenerationController final {
public:
  explicit GenerationController(ChatSession &session) noexcept;

  [[nodiscard]] std::expected<GenerationWork, SendMessageError>
  Begin(std::string text);
  [[nodiscard]] bool Complete(std::uint64_t generation_id,
                              CompletionResponse response);
  [[nodiscard]] bool Fail(std::uint64_t generation_id,
                          CompletionError error);
  void Cancel() noexcept;
  void Reset() noexcept;

  [[nodiscard]] bool IsCurrent(std::uint64_t generation_id) const noexcept;
  [[nodiscard]] const GenerationState &State() const noexcept;

private:
  ChatSession &session_;
  std::uint64_t next_generation_id_{};
  GenerationState state_;
};

} // namespace linecode::application
