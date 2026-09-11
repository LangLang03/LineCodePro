#pragma once

#include <cstddef>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "domain/input_attachment.h"

namespace linecode::infrastructure {

inline constexpr std::size_t max_attachment_json_bytes = 8U * 1024U * 1024U;
inline constexpr std::size_t max_attachments_per_message = 1024U;
inline constexpr std::size_t max_attachment_name_bytes = 64U * 1024U;
inline constexpr std::size_t max_attachment_path_bytes = 1024U * 1024U;

// Matches the legacy MessageRecord raw_json shape:
// {"attachments":[{"name":"...","path":"...","source":"local"}]}
[[nodiscard]] std::string EncodeAttachmentJson(
    std::span<const domain::InputAttachment> attachments);

// Invalid, oversized, or partially unknown legacy JSON is treated as absent;
// invalid individual attachment entries are skipped without losing valid ones.
[[nodiscard]] std::vector<domain::InputAttachment>
DecodeAttachmentJson(std::string_view attachments_json);

} // namespace linecode::infrastructure
