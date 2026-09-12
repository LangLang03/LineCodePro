#pragma once

#include <cstdint>
#include <string>

#include "application/ports/completion_gateway.h"

namespace linecode::application {

// Port of `cn.lineai.context.TokenUsageTracker`.
//
// Records the usage the server reported for the most recent response. The
// input token count already covers the system prompt, the tool definitions and
// the whole history, so it tracks the live context far better than the local
// characters-over-four estimate; when a protocol reports no usage the value
// stays 0 and the trigger falls back to that estimate.
//
// Legacy kept this per conversation and reset it whenever the conversation
// changed, because the count belongs to the transcript it was measured on.
class TokenUsageTracker final {
public:
  // Only positive values overwrite: a protocol that omits usage must not wipe
  // the last real measurement.
  void Record(const CompletionResponse &response) noexcept {
    if (response.input_tokens > 0)
      last_input_tokens_ = static_cast<int>(response.input_tokens);
    if (response.output_tokens > 0)
      last_output_tokens_ = static_cast<int>(response.output_tokens);
  }

  [[nodiscard]] int LastInputTokens() const noexcept {
    return last_input_tokens_;
  }
  [[nodiscard]] int LastOutputTokens() const noexcept {
    return last_output_tokens_;
  }

  void Reset() noexcept {
    last_input_tokens_ = 0;
    last_output_tokens_ = 0;
    measured_conversation_.clear();
  }

  // Legacy `ContextCompactionController.onConversationChanged()`: the count
  // describes the transcript it was measured on, so switching conversation --
  // or emptying one -- zeroes it. Called at the start of every turn, which
  // also covers a tracker that was only just created.
  void BeginConversation(std::string conversation_id,
                         bool transcript_empty) {
    if (transcript_empty || conversation_id != measured_conversation_) {
      last_input_tokens_ = 0;
      last_output_tokens_ = 0;
      measured_conversation_ = std::move(conversation_id);
    }
  }

private:
  int last_input_tokens_{};
  int last_output_tokens_{};
  std::string measured_conversation_;
};

} // namespace linecode::application
