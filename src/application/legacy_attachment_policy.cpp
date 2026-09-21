#include "application/legacy_attachment_policy.h"

#include <algorithm>
#include <ranges>

namespace linecode::application {

std::vector<domain::InputAttachment> LegacyAttachmentPolicy::Sanitize(
    const std::span<const domain::InputAttachment> attachments) const {
  std::vector<domain::InputAttachment> sanitized;
  sanitized.reserve(attachments.size());
  for (const auto &attachment : attachments) {
    if (attachment.Path().empty()) {
      continue;
    }
    const bool duplicate = std::ranges::any_of(
        sanitized, [&attachment](const domain::InputAttachment &current) {
          return current.Matches(attachment.Path(), attachment.Source());
        });
    if (!duplicate) {
      sanitized.push_back(attachment);
    }
  }
  return sanitized;
}

const AttachmentPolicy &DefaultAttachmentPolicy() noexcept {
  static const LegacyAttachmentPolicy policy;
  return policy;
}

} // namespace linecode::application
