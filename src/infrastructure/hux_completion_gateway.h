#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/completion_gateway.h"

namespace linecode::infrastructure {

class HuxCompletionGateway final : public application::CompletionGateway {
public:
  explicit HuxCompletionGateway(std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<std::expected<application::CompletionResponse,
                                            application::CompletionError>>
  Complete(application::CompletionRequest request,
           application::CompletionObserver observer) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
