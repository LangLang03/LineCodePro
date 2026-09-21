#pragma once

#include <cstdint>
#include <expected>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "application/chat_session.h"
#include "application/ports/completion_gateway.h"
#include "application/tool_result_display_policy.h"

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
  std::string streamed_text;
  std::string streamed_reasoning;
  std::string promoted_content;
  std::vector<domain::AssistantTimelineEvent> timeline;
  std::size_t active_turn_index{};
  std::int64_t started_at_millis{};

  bool operator==(const GenerationState &) const = default;
};

struct GenerationWork final {
  std::uint64_t generation_id{};
  std::vector<CompletionMessage> messages;
};

class GenerationController final {
public:
  explicit GenerationController(
      ChatSession &session,
      std::shared_ptr<const ToolResultDisplayProjector> result_display = {});

  [[nodiscard]] std::expected<GenerationWork, SendMessageError>
  Begin(std::string text);
  [[nodiscard]] std::expected<GenerationWork, SendMessageError>
  Begin(std::string text, std::vector<domain::InputAttachment> attachments);
  [[nodiscard]] std::expected<GenerationWork, SendMessageError>
  Begin(std::string text, std::vector<domain::InputAttachment> attachments,
        std::optional<domain::ChatImage> image);
  [[nodiscard]] bool Complete(std::uint64_t generation_id,
                              CompletionResponse response);
  [[nodiscard]] bool Observe(std::uint64_t generation_id,
                             const CompletionEvent &event);
  [[nodiscard]] bool Fail(std::uint64_t generation_id,
                          CompletionError error);
  // Drops whatever the current attempt streamed so a retry starts clean,
  // without failing the generation. The legacy controller removed the partial
  // assistant message before appending its retry notice; this port never
  // persists a partial until `Fail`, so clearing the streamed state is enough
  // and no conversation-store removal primitive is needed.
  [[nodiscard]] bool ResetAttempt(std::uint64_t generation_id);
  void Cancel() noexcept;
  void Reset() noexcept;

  [[nodiscard]] bool IsCurrent(std::uint64_t generation_id) const noexcept;
  [[nodiscard]] const GenerationState &State() const noexcept;

  // Rebuilds `work.messages` from the current conversation. The legacy
  // controller restarted the model request after a context compaction
  // (`Host.startInitialModelRequest`), so a caller that compacts between
  // `Begin()` and the completion request refreshes the snapshot here instead of
  // sending the pre-compaction transcript.
  void RefreshMessages(GenerationWork &work) const;

private:
  void PersistPartial(bool error, std::string error_message);
  [[nodiscard]] std::vector<CompletionMessage> BuildMessages() const;

  ChatSession &session_;
  std::shared_ptr<const ToolResultDisplayProjector> result_display_;
  std::uint64_t next_generation_id_{};
  GenerationState state_;
};

} // namespace linecode::application
