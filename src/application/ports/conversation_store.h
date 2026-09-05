#pragma once

#include <span>

#include "domain/app_state.h"

namespace linecode::application {

class ConversationStore {
public:
  virtual ~ConversationStore() = default;

  [[nodiscard]] virtual std::span<const domain::ChatMessage> Messages() const noexcept = 0;
  virtual void Append(domain::ChatMessage message) = 0;
  virtual void Clear() noexcept = 0;
};

} // namespace linecode::application
