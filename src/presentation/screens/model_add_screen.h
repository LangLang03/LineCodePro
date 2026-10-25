#pragma once

#include <functional>
#include <memory>
#include <optional>
#include <string>

#include <huxerui/view.h>

#include "application/model_form_service.h"
#include "application/ports/model_store.h"

namespace linecode::presentation {

struct ModelAddScreenOptions final {
  std::optional<domain::ModelProviderPreset> preset;
  std::optional<domain::ModelConfig> editing;
  bool local{};
};

struct ModelAddScreenActions final {
  std::function<void()> on_back;
  std::function<void(domain::ModelConfig)> on_saved;
};

// `local_model_directory` is the application-owned directory an imported GGUF
// is copied into. The picker hands back a capability rather than a path the
// runtime can open, so a local model has to be materialized here before
// llama.cpp can load it.
[[huxerui::composable]] huxerui::View
ModelAddScreen(ModelAddScreenOptions options,
               std::shared_ptr<application::ModelStore> store,
               std::shared_ptr<application::ModelCatalogGateway> catalog,
               std::string local_model_directory,
               ModelAddScreenActions actions);

} // namespace linecode::presentation
