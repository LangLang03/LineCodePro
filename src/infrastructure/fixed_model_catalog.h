#pragma once

#include "application/ports/model_store.h"

namespace linecode::infrastructure {

class FixedModelCatalog final : public application::ModelCatalogGateway {
public:
  [[nodiscard]] huxerui::Task<
      std::expected<std::vector<std::string>, application::ModelStoreError>>
  Fetch(domain::ModelProtocol protocol, std::string base_url,
        std::string api_key) override;

  [[nodiscard]] huxerui::Task<std::expected<application::ModelProbeResult,
                                            application::ModelStoreError>>
  Probe(domain::ModelConfig model) override;
};

} // namespace linecode::infrastructure
