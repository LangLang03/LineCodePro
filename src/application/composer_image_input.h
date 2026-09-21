#pragma once

#include <cstddef>
#include <expected>
#include <span>
#include <string>

#include "domain/chat_image.h"

namespace linecode::application {

enum class ComposerImageInputError {
  empty,
  unsupported_format,
  too_large,
};

inline constexpr std::size_t max_composer_image_input_bytes =
    32U * 1024U * 1024U;

// Converts a picker-owned PNG/JPEG payload into the transport-neutral image
// value carried by a chat turn. File selection and preview decoding remain UI
// concerns; protocol-specific JSON remains in the completion codecs.
[[nodiscard]] std::expected<domain::ChatImage, ComposerImageInputError>
EncodeComposerImage(std::string name, std::span<const std::byte> bytes);

} // namespace linecode::application
