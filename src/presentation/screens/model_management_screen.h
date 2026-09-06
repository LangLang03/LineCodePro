#pragma once

#include <functional>
#include <memory>

#include <huxerui/view.h>

#include "application/ports/model_store.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View ModelManagementScreen(
    std::shared_ptr<application::ModelStore> store,
    std::shared_ptr<application::ModelCatalogGateway> catalog,
    std::function<void(bool)> on_selection_availability_changed = {});

} // namespace linecode::presentation
