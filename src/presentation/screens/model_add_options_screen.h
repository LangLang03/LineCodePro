#pragma once

#include <functional>

#include <huxerui/view.h>

#include "domain/model_config.h"

namespace linecode::presentation {

struct ModelAddOptionsActions final {
  std::function<void()> on_back;
  std::function<void()> on_custom;
  std::function<void()> on_local;
  std::function<void(domain::ModelProviderPreset)> on_preset;
};

[[huxerui::composable]] huxerui::View
ModelAddOptionsScreen(ModelAddOptionsActions actions);

} // namespace linecode::presentation
