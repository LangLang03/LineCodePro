#pragma once

#include <span>
#include <string>

#include "domain/app_state.h"

namespace linecode::application {

class AttachmentPromptRenderer {
public:
  virtual ~AttachmentPromptRenderer() = default;

  [[nodiscard]] virtual std::string
  Render(std::span<const domain::ChatMessage> history) const = 0;
};

} // namespace linecode::application
