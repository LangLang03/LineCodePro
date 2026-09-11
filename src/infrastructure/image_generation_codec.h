#pragma once

#include <cstddef>
#include <expected>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/ports/image_generation.h"

namespace linecode::infrastructure {

struct ImageHttpRequestDescriptor final {
  std::string url;
  std::vector<std::pair<std::string, std::string>> headers;
  std::string body;

  bool operator==(const ImageHttpRequestDescriptor &) const = default;
};

enum class ImagePayloadSource {
  base64,
  data_url,
  remote_url,
};

struct ImagePayloadCandidate final {
  ImagePayloadSource source{ImagePayloadSource::base64};
  std::string payload;
  std::string mime_type{"image/png"};
  std::string revised_prompt;

  bool operator==(const ImagePayloadCandidate &) const = default;
};

[[nodiscard]] application::ImageGenerationResult<ImageHttpRequestDescriptor>
BuildImageGenerationRequest(const domain::ModelConfig &model,
                            const domain::ImageGenerationRequest &request,
                            bool request_base64 = true);

[[nodiscard]] application::ImageGenerationResult<ImagePayloadCandidate>
DecodeImageGenerationResponse(domain::ModelProtocol protocol,
                              std::string_view body);

[[nodiscard]] bool IsUsableImageBase64(std::string_view payload) noexcept;
[[nodiscard]] bool IsUsableImageDataUrl(std::string_view value) noexcept;
[[nodiscard]] std::string Base64Encode(std::span<const std::byte> bytes);

class JsonImageGenerationToolCodec final
    : public application::ImageGenerationToolCodec {
public:
  [[nodiscard]] application::ImageGenerationResult<
      domain::ImageGenerationRequest>
  DecodeArguments(std::string arguments_json) const override;

  [[nodiscard]] std::string
  EncodeToolResult(const domain::ImageGenerationRequest &request,
                   const domain::GeneratedImage &image) const override;
};

} // namespace linecode::infrastructure
