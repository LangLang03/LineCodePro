#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/image_generation.h"

namespace linecode::infrastructure {

class HuxImageGenerationGateway final
    : public application::ImageGenerationGateway {
public:
  explicit HuxImageGenerationGateway(
      std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<application::ImageGenerationResult<
      domain::GeneratedImage>>
  Generate(domain::ModelConfig model,
           domain::ImageGenerationRequest request) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
