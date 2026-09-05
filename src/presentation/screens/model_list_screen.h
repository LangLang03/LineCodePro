#pragma once

#include <functional>
#include <memory>

#include <huxerui/view.h>

#include "application/ports/model_store.h"

namespace linecode::presentation {

struct ModelListActions final {
  std::function<void()> on_back;
  std::function<void()> on_add;
  std::function<void(domain::ModelConfig)> on_edit;
};

[[huxerui::composable]] huxerui::View
ModelListScreen(std::shared_ptr<application::ModelStore> store,
                ModelListActions actions);

} // namespace linecode::presentation
