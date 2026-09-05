#include "infrastructure/fixed_model_catalog.h"

#include <utility>

namespace linecode::infrastructure {

huxerui::Task<
    std::expected<std::vector<std::string>, application::ModelStoreError>>
FixedModelCatalog::Fetch(domain::ModelProtocol protocol, std::string base_url,
                         std::string api_key) {
  if (base_url.empty() || api_key.empty()) {
    co_return std::unexpected(application::ModelStoreError{
        .message = "Base URL and API Key are required"});
  }
  switch (protocol) {
  case domain::ModelProtocol::codex_responses:
    co_return std::vector<std::string>{"test-codex", "gpt-5-codex"};
  case domain::ModelProtocol::anthropic_messages:
    co_return std::vector<std::string>{"test-claude", "claude-sonnet-4-5"};
  case domain::ModelProtocol::local_gguf:
    co_return std::vector<std::string>{};
  case domain::ModelProtocol::openai_compatible:
    co_return std::vector<std::string>{"test-model", "gpt-4o-mini"};
  }
  co_return std::vector<std::string>{"test-model"};
}

huxerui::Task<
    std::expected<application::ModelProbeResult, application::ModelStoreError>>
FixedModelCatalog::Probe(domain::ModelConfig model) {
  if (model.base_url.empty() || model.api_key.empty() ||
      model.model_id.empty()) {
    co_return std::unexpected(application::ModelStoreError{
        .message = "Base URL, API Key and Model ID are required"});
  }
  co_return application::ModelProbeResult{
      .response = "Fixed test response from " + model.model_id,
      .elapsed_milliseconds = 0,
  };
}

} // namespace linecode::infrastructure
