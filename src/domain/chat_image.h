#pragma once

#include <string>
#include <string_view>

namespace linecode::domain {

// An image the user attached to an outgoing message.
//
// Legacy `handleImagePicked` (`MainChatView.java:887-915`) recompresses the
// selected file to JPEG, then passes image bytes, MIME type and display name
// down the send path. The payload travels as base64 rather than as a path —
// the model may be remote and the file may be a content URI the app cannot
// hand out. The HuxerUI picker preserves supported PNG/JPEG bytes because its
// public image API decodes those formats but does not expose a raster
// transcoder.
struct ChatImage final {
  std::string name;
  // MIME type verified from the encoded PNG/JPEG signature, not trusted from
  // provider metadata or a filename extension.
  std::string mime_type;
  // Base64 of the encoded bytes, without line wrapping (`Base64.NO_WRAP`).
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
