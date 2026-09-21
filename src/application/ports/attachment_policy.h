#pragma once

#include <span>
#include <vector>

#include "domain/input_attachment.h"

namespace linecode::application {

class AttachmentPolicy {
public:
  virtual ~AttachmentPolicy() = default;

  [[nodiscard]] virtual std::vector<domain::InputAttachment>
  Sanitize(std::span<const domain::InputAttachment> attachments) const = 0;
};

} // namespace linecode::application
