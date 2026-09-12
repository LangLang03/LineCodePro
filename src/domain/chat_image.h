#pragma once

#include <string>
#include <string_view>

namespace linecode::domain {

// An image the user attached to an outgoing message.
//
// Legacy `handleImagePicked` (`MainChatView.java:887-915`) reads the picked
// file, compresses it to JPEG and passes `imageBase64` / `imageMimeType` /
// `imageName` down the send path, so the payload travels as base64 rather than
// as a path — the model may be remote and the file may be a content URI the
// app cannot hand out.
struct ChatImage final {
  std::string name;
  // Always "image/jpeg" in practice: the picker re-encodes to JPEG before
  // encoding (`MainChatView.java:899-903`).
  std::string mime_type;
  // Base64 of the JPEG bytes, no line wrapping (`Base64.NO_WRAP`).
  std::string base64;

  [[nodiscard]] bool Empty() const noexcept {
    return base64.empty() || mime_type.empty();
  }

  bool operator==(const ChatImage &) const = default;
};

// The `data:` URL every protocol embeds. Matches the shape the image
// understanding path already uses
// (`infrastructure/image_understanding_codec.cpp:88-90`).
[[nodiscard]] inline std::string ChatImageDataUrl(const ChatImage &image) {
  return "data:" + image.mime_type + ";base64," + image.base64;
}

} // namespace linecode::domain
