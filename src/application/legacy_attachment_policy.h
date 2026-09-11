#pragma once

#include "application/ports/attachment_policy.h"

namespace linecode::application {

class LegacyAttachmentPolicy final : public AttachmentPolicy {
public:
  [[nodiscard]] std::vector<domain::InputAttachment>
  Sanitize(std::span<const domain::InputAttachment> attachments) const override;
};

[[nodiscard]] const AttachmentPolicy &DefaultAttachmentPolicy() noexcept;

} // namespace linecode::application
