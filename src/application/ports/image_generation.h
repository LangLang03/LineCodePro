#pragma once

#include <cstdint>
#include <expected>
#include <string>

#include <huxerui/task.h>

#include "domain/image_generation.h"
#include "domain/model_config.h"

namespace linecode::application {

enum class ImageGenerationErrorCode : std::uint8_t {
  invalid_arguments,
  unsupported_protocol,
  invalid_configuration,
  transport,
  response_too_large,
  decode,
};

struct ImageGenerationError final {
  ImageGenerationErrorCode code{ImageGenerationErrorCode::decode};
  std::string message;

  bool operator==(const ImageGenerationError &) const = default;
};

template <class Value>
using ImageGenerationResult =
    std::expected<Value, ImageGenerationError>;

class ImageGenerationToolCodec {
public:
  virtual ~ImageGenerationToolCodec() = default;

  [[nodiscard]] virtual ImageGenerationResult<domain::ImageGenerationRequest>
  DecodeArguments(std::string arguments_json) const = 0;
  [[nodiscard]] virtual std::string
  EncodeToolResult(const domain::ImageGenerationRequest &request,
                   const domain::GeneratedImage &image) const = 0;
};

class ImageGenerationGateway {
public:
  virtual ~ImageGenerationGateway() = default;

  [[nodiscard]] virtual huxerui::Task<
      ImageGenerationResult<domain::GeneratedImage>>
  Generate(domain::ModelConfig model,
           domain::ImageGenerationRequest request) = 0;
};

} // namespace linecode::application
