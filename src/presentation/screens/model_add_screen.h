#pragma once

#include <functional>
#include <memory>
#include <optional>

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

[[huxerui::composable]] huxerui::View
ModelAddScreen(ModelAddScreenOptions options,
               std::shared_ptr<application::ModelStore> store,
               std::shared_ptr<application::ModelCatalogGateway> catalog,
               ModelAddScreenActions actions);

} // namespace linecode::presentation
