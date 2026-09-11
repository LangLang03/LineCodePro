#pragma once

#include <expected>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/ports/image_understanding.h"

namespace linecode::infrastructure {

struct ImageUnderstandingHttpRequest final {
  std::string url;
  std::vector<std::pair<std::string, std::string>> headers;
  std::string body;

  bool operator==(const ImageUnderstandingHttpRequest &) const = default;
};

[[nodiscard]] application::ImageUnderstandingResult<
    ImageUnderstandingHttpRequest>
BuildImageUnderstandingRequest(
    const domain::ModelConfig &model, std::string_view system_prompt,
    const domain::ImageUnderstandingRequest &request,
    const domain::WorkspaceImage &image);

[[nodiscard]] application::ImageUnderstandingResult<std::string>
DecodeImageUnderstandingResponse(domain::ModelProtocol protocol,
                                 std::string_view body);

class JsonImageUnderstandingToolCodec final
    : public application::ImageUnderstandingToolCodec {
public:
  [[nodiscard]] application::ImageUnderstandingResult<
      domain::ImageUnderstandingRequest>
  DecodeArguments(std::string arguments_json) const override;

  [[nodiscard]] application::ImageUnderstandingResult<domain::WorkspaceImage>
  ValidateImage(std::string resolved_path,
                std::vector<std::byte> bytes) const override;
};

} // namespace linecode::infrastructure
