#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/model_store.h"

namespace linecode::infrastructure {

class HuxModelCatalogGateway final
    : public application::ModelCatalogGateway {
public:
  explicit HuxModelCatalogGateway(std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<
      std::expected<std::vector<std::string>, application::ModelStoreError>>
  Fetch(domain::ModelProtocol protocol, std::string base_url,
        std::string api_key) override;

  [[nodiscard]] huxerui::Task<std::expected<application::ModelProbeResult,
                                            application::ModelStoreError>>
  Probe(domain::ModelConfig model) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
