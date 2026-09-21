#pragma once

#include <cstdint>
#include <expected>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/image_understanding.h"
#include "domain/model_config.h"

namespace linecode::application {

enum class ImageUnderstandingErrorCode : std::uint8_t {
  invalid_arguments,
  unavailable,
  unsupported_protocol,
  invalid_configuration,
  not_found,
  too_large,
  unsupported_format,
  transport,
  http_status,
  decode,
};

struct ImageUnderstandingError final {
  ImageUnderstandingErrorCode code{ImageUnderstandingErrorCode::decode};
  std::string message;
  int http_status{};

  bool operator==(const ImageUnderstandingError &) const = default;
};

template <class Value>
using ImageUnderstandingResult =
    std::expected<Value, ImageUnderstandingError>;

class ImageUnderstandingToolCodec {
public:
  virtual ~ImageUnderstandingToolCodec() = default;

  [[nodiscard]] virtual ImageUnderstandingResult<
      domain::ImageUnderstandingRequest>
  DecodeArguments(std::string arguments_json) const = 0;

  [[nodiscard]] virtual ImageUnderstandingResult<domain::WorkspaceImage>
  ValidateImage(std::string resolved_path,
                std::vector<std::byte> bytes) const = 0;
};

class WorkspaceImageReader {
public:
  virtual ~WorkspaceImageReader() = default;

  [[nodiscard]] virtual huxerui::Task<
      ImageUnderstandingResult<domain::RawWorkspaceImage>>
  Read(std::string path) = 0;
};

class ImageUnderstandingGateway {
public:
  virtual ~ImageUnderstandingGateway() = default;

  [[nodiscard]] virtual huxerui::Task<
      ImageUnderstandingResult<std::string>>
  Analyze(domain::ModelConfig model, std::string system_prompt,
          domain::ImageUnderstandingRequest request,
          domain::WorkspaceImage image) = 0;
};

} // namespace linecode::application
