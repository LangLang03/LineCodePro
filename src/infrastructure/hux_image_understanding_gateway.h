#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/image_understanding.h"

namespace linecode::infrastructure {

class HuxImageUnderstandingGateway final
    : public application::ImageUnderstandingGateway {
public:
  explicit HuxImageUnderstandingGateway(
      std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<
      application::ImageUnderstandingResult<std::string>>
  Analyze(domain::ModelConfig model, std::string system_prompt,
          domain::ImageUnderstandingRequest request,
          domain::WorkspaceImage image) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
