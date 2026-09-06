#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <utility>

#include "domain/app_state.h"

namespace linecode::application {

struct ConversationSummary final {
  std::string id;
  std::string title;
  std::int64_t updated_at_millis{};

  bool operator==(const ConversationSummary &) const = default;
};

// Orders an asynchronous selection against synchronous UI commands. A token
// remains current until a later selection, new-conversation command, or delete
// of the intended conversation invalidates it.
class ConversationSelectionBarrier final {
public:
  [[nodiscard]] std::uint64_t Begin(std::string conversation_id) {
    const auto generation = ++generation_;
    active_ = Intent{.conversation_id = std::move(conversation_id),
                     .generation = generation};
    return generation;
  }

  void Invalidate() noexcept {
    ++generation_;
    active_.reset();
  }

  [[nodiscard]] bool Matches(std::uint64_t generation,
                             std::string_view conversation_id) const noexcept {
    return active_.has_value() && active_->generation == generation &&
           active_->conversation_id == conversation_id;
  }

  [[nodiscard]] bool
  DefersAppendTo(std::string_view conversation_id) const noexcept {
    return active_.has_value() && active_->conversation_id == conversation_id;
  }

  [[nodiscard]] std::uint64_t Generation() const noexcept {
    return generation_;
  }

  void Settle(std::uint64_t generation) noexcept {
    if (active_.has_value() && active_->generation == generation) {
      active_.reset();
    }
  }

private:
  struct Intent final {
    std::string conversation_id;
    std::uint64_t generation{};
  };

  std::uint64_t generation_{};
  std::optional<Intent> active_;
};

class ConversationStore {
public:
  virtual ~ConversationStore() = default;

  [[nodiscard]] virtual std::span<const domain::ChatMessage>
  Messages() const noexcept = 0;
  [[nodiscard]] virtual std::uint64_t AllocateMessageId() noexcept = 0;
  virtual void Append(domain::ChatMessage message) = 0;
  virtual void Clear() = 0;

  // Implementations expose a UI-thread cache here. Persistent adapters may
  // fulfill the commands asynchronously, then notify their owner to render the
  // updated cache; callers never wait for storage on the UI thread.
  [[nodiscard]] virtual std::span<const ConversationSummary>
  Conversations() const noexcept {
    return {};
  }
  [[nodiscard]] virtual std::string_view
  CurrentConversationId() const noexcept {
    return {};
  }
  virtual void StartNewConversation() { Clear(); }
  virtual void SelectConversation(std::string_view) {}
  virtual void DeleteConversation(std::string_view id) {
    if (id == CurrentConversationId()) {
      Clear();
    }
  }
};

} // namespace linecode::application
