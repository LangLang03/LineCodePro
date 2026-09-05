#pragma once

#include <memory>

#include <huxerui/view.h>

#include "application/ports/model_store.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View ModelManagementScreen(
    std::shared_ptr<application::ModelStore> store,
    std::shared_ptr<application::ModelCatalogGateway> catalog);

} // namespace linecode::presentation
